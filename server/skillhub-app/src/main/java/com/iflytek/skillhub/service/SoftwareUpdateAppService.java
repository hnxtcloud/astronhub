package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.repository.SoftwareUpdateQueryRepository;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates release rules, real storage bytes, capacity reservations and audited publication. */
@Service
public class SoftwareUpdateAppService {
    private final SoftwareUpdateReleaseRepository releases;
    private final SoftwareReleaseAssetRepository assets;
    private final SoftwareAssetUploadRepository uploads;
    private final SoftwareReleaseRepository catalog;
    private final SoftwareApplicationRepository applications;
    private final SoftwarePackageRevisionRepository packages;
    private final SoftwarePolicyService policies;
    private final SoftwareQuotaService quota;
    private final SoftwareTransferStorage storage;
    private final SoftwareUpdateMetadataValidator metadata;
    private final SoftwareUpdateQueryRepository queries;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final SoftwareUpdateSourceRepository sources;
    private final SoftwareUpdateChannelRepository channels;

    public SoftwareUpdateAppService(SoftwareUpdateReleaseRepository releases, SoftwareReleaseAssetRepository assets,
            SoftwareAssetUploadRepository uploads, SoftwareReleaseRepository catalog, SoftwareApplicationRepository applications,
            SoftwarePackageRevisionRepository packages, SoftwarePolicyService policies, SoftwareQuotaService quota,
            SoftwareTransferStorage storage, SoftwareUpdateMetadataValidator metadata, SoftwareUpdateQueryRepository queries,
            AuditLogService audit, TransactionTemplate tx, SoftwareUpdateSourceRepository sources,
            SoftwareUpdateChannelRepository channels) {
        this.releases = releases; this.assets = assets; this.uploads = uploads; this.catalog = catalog;
        this.applications = applications; this.packages = packages; this.policies = policies; this.quota = quota;
        this.storage = storage; this.metadata = metadata; this.queries = queries; this.audit = audit; this.tx = tx;
        this.sources = sources; this.channels = channels;
    }

    public SoftwareUpdateReleaseResponse create(SoftwareUpdateCreateRequest request, String actor) {
        SoftwareUpdateRules.identity(request.machineVersion(), request.tag(), request.targetOs(), request.targetArch(),
                request.channel(), request.variant(), request.protocol(), request.piVersion());
        try {
            return tx.execute(status -> {
            var grouping = catalog.findById(request.catalogReleaseId()).orElseThrow(() -> missing("catalog release"));
            releases.findByApplicationIdAndMachineVersionAndTargetOsAndTargetArchAndChannelAndVariant(grouping.getApplicationId(),
                    request.machineVersion(), request.targetOs(), request.targetArch(), request.channel(), request.variant()).ifPresent(existing -> {
                        throw new DomainConflictException("error.software.update.identityConflict");
                    });
            releases.findByApplicationIdAndTagAndTargetOsAndTargetArchAndChannelAndVariant(grouping.getApplicationId(),
                    request.tag(), request.targetOs(), request.targetArch(), request.channel(), request.variant()).ifPresent(existing -> {
                        throw new DomainConflictException("error.software.update.identityConflict");
                    });
            var release = releases.save(new SoftwareUpdateRelease(grouping.getApplicationId(), grouping.getId(),
                    request.machineVersion(), request.tag(), request.targetOs(), request.targetArch(), request.channel(),
                    request.variant(), request.protocol(), request.piVersion(), grouping.getReleaseNotes(), actor));
            record("software.update.create", release.getId(), actor);
            return queries.project(release);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            // The database unique constraint also serializes two simultaneous admissions of the same identity.
            throw new DomainConflictException("error.software.update.identityConflict");
        }
    }

    public List<SoftwareUpdateReleaseResponse> list(Long catalogReleaseId) {
        return releases.findByCatalogReleaseIdOrderByIdDesc(catalogReleaseId).stream().map(queries::project).toList();
    }

    public List<SoftwareUpdateReleaseResponse> listPublished(Long applicationId) {
        return releases.findByApplicationIdAndStatusOrderByPublishedAtDesc(applicationId, "PUBLISHED").stream().map(queries::project).toList();
    }

    public SoftwareUpdateReleaseResponse get(Long id) { return queries.project(requireRelease(id)); }

    public SoftwareUpdateSourceResponse source(Long applicationId) {
        applications.findById(applicationId).orElseThrow(() -> missing("application"));
        boolean enabled = sources.findById(applicationId).map(SoftwareUpdateSource::isAnonymousEnabled).orElse(false);
        return new SoftwareUpdateSourceResponse(applicationId, enabled,
                channels.findByApplicationIdOrderByIdAsc(applicationId).stream().map(queries::channel).toList());
    }

    public SoftwareUpdateSourceResponse setAnonymous(Long applicationId, boolean enabled, String actor) {
        return tx.execute(status -> {
            applications.findById(applicationId).orElseThrow(() -> missing("application"));
            var source = sources.findById(applicationId).orElseGet(() -> new SoftwareUpdateSource(applicationId));
            source.setAnonymousEnabled(enabled);
            sources.save(source);
            record("software.update.anonymous." + enabled, applicationId, actor);
            return source(applicationId);
        });
    }

    public SoftwareUpdateChannelResponse saveChannel(Long applicationId, SoftwareUpdateChannelRequest request, String actor) {
        return tx.execute(status -> {
            applications.findById(applicationId).orElseThrow(() -> missing("application"));
            var channel = channels.findByApplicationIdAndSlug(applicationId, request.slug())
                    .orElseGet(() -> new SoftwareUpdateChannel(applicationId, request.slug()));
            channels.findByVirtualProjectPath(request.virtualProjectPath()).ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), channel.getId()))
                    throw new DomainConflictException("error.software.update.projectConflict");
            });
            channel.configure(request.virtualProjectPath(), request.targetOs(), request.targetArch(), request.channel(), request.variant(), request.targetReleaseId());
            if (request.targetReleaseId() != null) {
                var target = requireRelease(request.targetReleaseId());
                if (!"PUBLISHED".equals(target.getStatus()) || !channel.matches(target)) invalid("channel target");
                requireGovernedPackages(target);
            }
            var saved = channels.save(channel);
            record("software.update.channel", applicationId, actor);
            return queries.channel(saved);
        });
    }

    public SoftwareReleaseAssetResponse linkPackage(Long id, Long packageId, String path, String actor) {
        SoftwareUpdateRules.path(path);
        var release = requireRelease(id);
        release.requireDraft();
        var revision = requirePackage(release, packageId);
        var hashes = hashes(revision.getObjectKey(), revision.getByteSize());
        if (!hashes.sha256().equals(revision.getSha256())) invalid("installer hash");
        return tx.execute(status -> {
            var locked = locked(id);
            locked.requireDraft();
            var current = requirePackage(locked, packageId);
            requireFreePath(id, path);
            var asset = assets.save(new SoftwareReleaseAsset(id, path, "INSTALLER", packageId, current.getObjectKey(),
                    current.getByteSize(), hashes.sha256(), hashes.sha512()));
            record("software.update.link", id, actor);
            return queries.asset(asset);
        });
    }

    /** Streams auxiliary files into the software provider; installers use the existing confirmed-package workflow. */
    public SoftwareReleaseAssetResponse uploadAsset(Long id, String path, String role, long bytes, InputStream body, String actor) {
        SoftwareUpdateRules.path(path);
        SoftwareUpdateRules.role(role);
        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
        if (List.of(".exe", ".msi", ".deb", ".rpm", ".zip", ".dmg", ".tar.gz", ".tar.xz").stream().anyMatch(lowerPath::endsWith))
            invalid("installers must use the confirmed software package workflow");
        SoftwareAssetUpload admission = tx.execute(status -> {
            var release = locked(id);
            release.requireDraft();
            requireFreePath(id, path);
            var config = policies.requireActive().config();
            if (bytes <= 0 || bytes > config.maxPackageBytes()) invalid("byte size");
            if (("METADATA".equals(role) || "CHECKSUM".equals(role)) && bytes > 2L * 1024 * 1024) invalid("metadata/checksum exceeds 2 MiB");
            quota.reserve(bytes, config.totalQuotaBytes());
            if (uploads.countByOwnerUserIdAndState(actor, "RESERVED") >= config.maxActiveUploadsPerAdmin())
                throw new DomainConflictException("error.software.upload.tooManyActive");
            return uploads.save(new SoftwareAssetUpload(id, actor, path, role,
                    "software/update-assets/" + UUID.randomUUID(), bytes,
                    Instant.now().plus(config.uploadIdleTimeoutMinutes(), ChronoUnit.MINUTES)));
        });
        try {
            String ref = storage.initiateUpload(admission.getObjectKey());
            admission.assignStorageSessionRef(ref);
            tx.executeWithoutResult(status -> {
                var upload = uploads.findLockedById(admission.getId()).orElseThrow();
                requireReserved(upload);
                upload.assignStorageSessionRef(ref);
                uploads.save(upload);
            });
            List<Integer> parts = new ArrayList<>();
            long remaining = bytes;
            long chunkSize = policies.requireActive().config().chunkSizeBytes();
            for (int part = 1; remaining > 0; part++) {
                long length = Math.min(remaining, chunkSize);
                int partNumber = part;
                // Serialize each bounded provider operation with cleanup. A timed-out writer
                // cannot create uncharged parts after a cleanup worker has reclaimed its lease.
                tx.executeWithoutResult(status -> {
                    var upload = uploads.findLockedById(admission.getId()).orElseThrow();
                    requireReserved(upload);
                    storage.stageChunk(ref, partNumber, new LimitedInput(body, length), length);
                    storage.commitChunk(ref, admission.getObjectKey(), partNumber);
                    upload.touch(Instant.now().plus(policies.requireActive().config().uploadIdleTimeoutMinutes(), ChronoUnit.MINUTES));
                    uploads.save(upload);
                });
                parts.add(part);
                remaining -= length;
            }
            if (body.read() != -1) invalid("unexpected extra bytes");
            return tx.execute(status -> {
                var release = locked(id);
                release.requireDraft();
                var upload = uploads.findLockedById(admission.getId()).orElseThrow();
                requireReserved(upload);
                if (assets.findByReleaseIdAndRelativePath(id, path).isPresent()) conflict();
                var complete = storage.completeUpload(ref, admission.getObjectKey(), parts);
                if (complete.byteSize() != bytes) invalid("completed length");
                var hashes = hashes(admission.getObjectKey(), bytes);
                if (!complete.sha256Hex().equals(hashes.sha256())) invalid("completed checksum");
                var asset = assets.save(new SoftwareReleaseAsset(id, path, role, null, upload.getObjectKey(),
                        bytes, hashes.sha256(), hashes.sha512()));
                quota.confirmReservation(bytes, bytes);
                upload.complete();
                uploads.save(upload);
                record("software.update.asset.upload", id, actor);
                return queries.asset(asset);
            });
        } catch (IOException | RuntimeException ex) {
            tx.executeWithoutResult(status -> uploads.findLockedById(admission.getId()).ifPresent(upload -> {
                if ("RESERVED".equals(upload.getState())) {
                    if (upload.getStorageSessionRef() == null) upload.assignStorageSessionRef(admission.getStorageSessionRef());
                    upload.requireCleanup(); uploads.save(upload);
                }
            }));
            try { cleanupUpload(admission.getId()); } catch (RuntimeException cleanupFailure) { ex.addSuppressed(cleanupFailure); }
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new DomainBadRequestException("error.software.update.invalid", "upload stream failed");
        }
    }

    public void validate(Long id) {
        var release = requireRelease(id);
        requireGovernedPackages(release);
        metadata.validate(release, assets.findByReleaseIdOrderByIdAsc(id));
    }

    public void removeAsset(Long id, Long assetId, String actor) {
        Long cleanupId = tx.execute(status -> {
            locked(id).requireDraft();
            var asset = assets.findById(assetId).filter(found -> found.getReleaseId().equals(id))
                    .orElseThrow(() -> missing("asset"));
            Long pending = null;
            if (asset.getPackageRevisionId() == null) {
                var upload = uploads.findByObjectKey(asset.getObjectKey()).orElseThrow(() -> missing("asset ownership"));
                upload.deleteUsedObject();
                pending = uploads.save(upload).getId();
            }
            // A linked installer is only detached; its existing package bytes and quota stay owned by the package.
            assets.delete(asset);
            record("software.update.asset.remove", id, actor);
            return pending;
        });
        if (cleanupId != null) {
            try { cleanupUpload(cleanupId); } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Detached update asset remains charged until cleanup succeeds: {}", cleanupId, ex);
            }
        }
    }

    public SoftwareUpdateReleaseResponse publish(Long id, String actor) {
        return tx.execute(status -> {
            var release = locked(id);
            release.requireDraft();
            if (!uploads.findByReleaseIdAndState(id, "RESERVED").isEmpty())
                throw new DomainConflictException("error.software.update.uploadPending");
            validate(id);
            release.publish(assets.findByReleaseIdOrderByIdAsc(id), actor);
            releases.save(release);
            record("software.update.publish", id, actor);
            return queries.project(release);
        });
    }

    public SoftwareUpdateReleaseResponse withdraw(Long id, String actor) {
        return tx.execute(status -> {
            var release = locked(id);
            release.withdraw();
            releases.save(release);
            record("software.update.withdraw", id, actor);
            return queries.project(release);
        });
    }

    /** Recheck for every update read; a published snapshot cannot bypass installer/application takedown. */
    public void requireGovernedPackages(SoftwareUpdateRelease release) {
        var application = applications.findById(release.getApplicationId()).orElseThrow(() -> missing("application"));
        if (application.getStatus() != SoftwareApplicationStatus.PUBLISHED) invalid("application not published");
        for (var asset : assets.findByReleaseIdOrderByIdAsc(release.getId())) {
            if (asset.getPackageRevisionId() != null) requirePackage(release, asset.getPackageRevisionId());
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void cleanupExpiredUploads() {
        for (var upload : uploads.findTop20ByStateInAndExpiresAtBefore(List.of("RESERVED", "CLEANUP", "DELETE_USED"), Instant.now())) {
            try { cleanupUpload(upload.getId()); } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Update asset cleanup will retry for {}", upload.getId(), ex);
            }
        }
    }

    public void cleanupUpload(Long uploadId) {
        tx.executeWithoutResult(status -> {
            var upload = uploads.findLockedById(uploadId).orElseThrow();
            if ("COMPLETED".equals(upload.getState()) || "CLEANED".equals(upload.getState())) return;
            if ("RESERVED".equals(upload.getState()) && upload.getExpiresAt().isAfter(Instant.now())) return;
            // No capacity is released if abort/delete fails. The durable row remains available for retry.
            if (upload.getStorageSessionRef() != null) storage.abortUploadVerified(upload.getStorageSessionRef(), upload.getObjectKey());
            storage.deleteObject(upload.getObjectKey());
            if ("DELETE_USED".equals(upload.getState())) quota.releaseUsed(upload.getReservedBytes());
            else quota.releaseReservation(upload.getReservedBytes());
            upload.cleaned();
            uploads.save(upload);
        });
    }

    public SoftwareUpdateRelease requireRelease(Long id) { return releases.findById(id).orElseThrow(() -> missing("update release")); }
    private SoftwareUpdateRelease locked(Long id) { return releases.findLockedById(id).orElseThrow(() -> missing("update release")); }
    private SoftwarePackageRevision requirePackage(SoftwareUpdateRelease release, Long packageId) {
        var revision = packages.findById(packageId).orElseThrow(() -> missing("installer"));
        if (!Objects.equals(revision.getApplicationId(), release.getApplicationId())
                || !Objects.equals(revision.getReleaseId(), release.getCatalogReleaseId())
                || !revision.getTargetOs().name().equals(release.getTargetOs())
                || !revision.getTargetArch().name().equals(release.getTargetArch())
                || !revision.isConfirmed() || revision.getStatus() != SoftwarePackageStatus.PUBLISHED) invalid("installer not applicable/published");
        return revision;
    }
    private void requireFreePath(Long id, String path) {
        if (assets.findByReleaseIdAndRelativePath(id, path).isPresent()
                || uploads.findByReleaseIdAndState(id, "RESERVED").stream().anyMatch(upload -> upload.getRelativePath().equals(path))) conflict();
    }
    private Hashes hashes(String key, long expectedSize) {
        if (storage.objectSize(key) != expectedSize) invalid("stored byte size");
        try (var input = storage.readRange(key, 0, expectedSize)) {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            byte[] buffer = new byte[65536];
            long count = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                sha256.update(buffer, 0, read); sha512.update(buffer, 0, read); count += read;
            }
            if (count != expectedSize) invalid("stored byte size");
            return new Hashes(HexFormat.of().formatHex(sha256.digest()), Base64.getEncoder().encodeToString(sha512.digest()));
        } catch (IOException ex) { throw new DomainBadRequestException("error.software.update.invalid", "stored bytes unavailable"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private record Hashes(String sha256, String sha512) { }
    private void record(String action, Long id, String actor) { audit.record(actor, action, "SOFTWARE_UPDATE", id, null, null, null, null); }
    private static DomainNotFoundException missing(String resource) { return new DomainNotFoundException("error.software.update.notFound", resource); }
    private static void invalid(String detail) { SoftwareUpdateRules.invalid(detail); }
    private static void conflict() { throw new DomainConflictException("error.software.update.pathConflict"); }
    private static void requireReserved(SoftwareAssetUpload upload) {
        if (!"RESERVED".equals(upload.getState())) throw new DomainConflictException("error.software.update.uploadExpired");
    }
    /** Presents exactly one chunk without closing the request stream or reading the following chunk. */
    private static final class LimitedInput extends FilterInputStream {
        private long remaining;
        LimitedInput(InputStream source, long length) { super(source); remaining = length; }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = in.read(); if (value != -1) remaining--; return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) return -1;
            int count = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) remaining -= count; return count;
        }
        @Override public void close() { }
    }
}
