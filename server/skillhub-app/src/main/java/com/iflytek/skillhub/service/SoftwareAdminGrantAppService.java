package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.software.SoftwareAdminGrantResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOFTWARE_ADMIN duty grants. The duty is additive: granting adds a role
 * binding without touching existing ones, revoking removes only the
 * SOFTWARE_ADMIN binding. Both directions are restricted to SUPER_ADMIN and
 * always audited; the legacy single-role editor can neither grant nor wipe it.
 */
@Service
public class SoftwareAdminGrantAppService {

    public static final String SOFTWARE_ADMIN_ROLE = "SOFTWARE_ADMIN";

    private final UserRoleBindingRepository userRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SoftwareAdminGrantAppService(UserRoleBindingRepository userRoleBindingRepository,
                                        RoleRepository roleRepository,
                                        UserAccountRepository userAccountRepository,
                                        AuditLogService auditLogService,
                                        RequestIdAccessor requestIdAccessor) {
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @Transactional(readOnly = true)
    public List<SoftwareAdminGrantResponse> listGrants() {
        return userRoleBindingRepository.findByRole_Code(SOFTWARE_ADMIN_ROLE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SoftwareAdminGrantResponse grant(String userId, String actorId,
                                            AuditRequestContext auditContext) {
        UserAccount user = requireUser(userId);
        if (user.isSystemAccount()) {
            throw new DomainForbiddenException("error.admin.user.systemAccount.immutable");
        }
        Role role = requireSoftwareAdminRole();
        if (userRoleBindingRepository.findByUserId(userId).stream()
                .anyMatch(binding -> SOFTWARE_ADMIN_ROLE.equals(binding.getRole().getCode()))) {
            // Idempotent grant: the duty is already present.
            return toResponse(findBinding(userId));
        }
        userRoleBindingRepository.save(new UserRoleBinding(userId, role));
        recordAudit("software.grant.grant", userId, actorId, auditContext);
        return toResponse(findBinding(userId));
    }

    /** Removes only the SOFTWARE_ADMIN binding; all other roles are preserved. */
    @Transactional
    public void revoke(String userId, String actorId, AuditRequestContext auditContext) {
        requireUser(userId);
        UserRoleBinding binding = userRoleBindingRepository.findByUserId(userId).stream()
                .filter(candidate -> SOFTWARE_ADMIN_ROLE.equals(candidate.getRole().getCode()))
                .findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.software.grant.notFound", userId));
        userRoleBindingRepository.delete(binding);
        userRoleBindingRepository.flush();
        recordAudit("software.grant.revoke", userId, actorId, auditContext);
    }

    private UserAccount requireUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException("error.admin.user.notFound", userId));
    }

    private Role requireSoftwareAdminRole() {
        return roleRepository.findByCode(SOFTWARE_ADMIN_ROLE)
                .orElseThrow(() -> new DomainBadRequestException("error.software.grant.roleMissing"));
    }

    private UserRoleBinding findBinding(String userId) {
        return userRoleBindingRepository.findByUserId(userId).stream()
                .filter(binding -> SOFTWARE_ADMIN_ROLE.equals(binding.getRole().getCode()))
                .findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.software.grant.notFound", userId));
    }

    private SoftwareAdminGrantResponse toResponse(UserRoleBinding binding) {
        UserAccount user = userAccountRepository.findById(binding.getUserId()).orElse(null);
        List<String> allRoles = userRoleBindingRepository.findByUserId(binding.getUserId()).stream()
                .map(candidate -> candidate.getRole().getCode())
                .sorted()
                .toList();
        return new SoftwareAdminGrantResponse(
                binding.getUserId(),
                user != null ? user.getDisplayName() : null,
                user != null ? user.getEmail() : null,
                allRoles,
                binding.getCreatedAt()
        );
    }

    private void recordAudit(String action, String targetUserId, String actorId,
                             AuditRequestContext auditContext) {
        auditLogService.record(
                actorId,
                action,
                "SOFTWARE_ADMIN_GRANT",
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                safeJson("targetUserId", targetUserId)
        );
    }

    private String safeJson(String key, String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(java.util.Map.of(key, value));
        } catch (Exception ex) {
            return null;
        }
    }
}
