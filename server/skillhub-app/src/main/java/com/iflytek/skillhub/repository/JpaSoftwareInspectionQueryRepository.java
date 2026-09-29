package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareInspectionTask;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.dto.software.SoftwareInspectionSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Projection joining inspection tasks with package revisions. Lives at the
 * app layer because the join spans two aggregates for presentation only; the
 * mapping to the response DTO happens in Java because enum-to-string
 * projection is not portable in JPQL constructor expressions.
 */
@Repository
public class JpaSoftwareInspectionQueryRepository implements SoftwareInspectionQueryRepository {

    private static final String SUMMARY_FROM = """
            from SoftwareInspectionTask t
            join SoftwarePackageRevision r on r.id = t.packageRevisionId
            """;

    private final EntityManager entityManager;

    public JpaSoftwareInspectionQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<SoftwareInspectionSummaryResponse> search(SoftwareInspectionTaskStatus status, Pageable pageable) {
        String filter = status == null ? "" : " where t.status = :status";
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select t, r " + SUMMARY_FROM + filter + " order by t.id desc", Object[].class);
        if (status != null) {
            query.setParameter("status", status);
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<SoftwareInspectionSummaryResponse> content = query.getResultList().stream()
                .map(this::toSummary)
                .toList();

        TypedQuery<Long> count = entityManager.createQuery(
                "select count(t) from SoftwareInspectionTask t" + filter, Long.class);
        if (status != null) {
            count.setParameter("status", status);
        }
        return new PageImpl<>(content, pageable, count.getSingleResult());
    }

    @Override
    public SoftwareInspectionSummaryResponse findByTaskId(Long taskId) {
        List<Object[]> result = entityManager.createQuery(
                        "select t, r " + SUMMARY_FROM + " where t.id = :taskId", Object[].class)
                .setParameter("taskId", taskId)
                .getResultList();
        return result.isEmpty() ? null : toSummary(result.getFirst());
    }

    private SoftwareInspectionSummaryResponse toSummary(Object[] row) {
        SoftwareInspectionTask task = (SoftwareInspectionTask) row[0];
        SoftwarePackageRevision revision = (SoftwarePackageRevision) row[1];
        return new SoftwareInspectionSummaryResponse(
                task.getId(), task.getPackageRevisionId(), task.getApplicationId(),
                task.getStatus().name(), task.getAttemptCount(), task.getMaxAttempts(),
                task.getManualRetryCount(), task.getPolicyRevision(), task.getFormat().name(),
                revision.getFileName(), revision.getOriginalFullVersion(), revision.getByteSize(),
                task.getLeaseOwner(), task.getLeaseExpiresAt(), task.getNextAttemptNotBefore(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
