package com.iflytek.skillhub.service;

import com.iflytek.skillhub.repository.SoftwarePublisherQueryRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.software.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit session grant management and live credential/application authorization. */
@Service
public class SoftwarePublisherAppService {
    private final SoftwarePublisherGrantRepository grants;
    private final SoftwareApplicationRepository apps;
    private final ApiTokenService tokens;
    private final ApiTokenRepository tokenRepository;
    private final UserAccountRepository users;
    private final UserRoleBindingRepository roles;
    private final ApiTokenScopeService scopes;
    private final AuditLogService audit;
    private final Clock clock;
    private final SoftwarePublisherQueryRepository queries;
    public SoftwarePublisherAppService(SoftwarePublisherGrantRepository grants, SoftwareApplicationRepository apps,
            ApiTokenService tokens, ApiTokenRepository tokenRepository, UserAccountRepository users,
            UserRoleBindingRepository roles, ApiTokenScopeService scopes, AuditLogService audit, Clock clock, SoftwarePublisherQueryRepository queries) {
        this.grants = grants; this.apps = apps; this.tokens = tokens; this.tokenRepository = tokenRepository;
        this.users = users; this.roles = roles; this.scopes = scopes; this.audit = audit; this.clock = clock; this.queries = queries;
    }
    @Transactional
    public SoftwarePublisherCreatedResponse create(Long appId, SoftwarePublisherCreateRequest body, Authentication auth) {
        String actor = requireSessionAdmin(auth); requireApp(appId);
        if (body.expiresAt() == null || body.expiresAt().isBlank()) throw new DomainBadRequestException("validation.token.expiresAt.future");
        var token = tokens.createToken(actor, body.name(), "[\"software:publish\"]", body.expiresAt());
        var grant = grants.save(new SoftwarePublisherGrant(token.entity().getId(), appId, actor, actor, Instant.now(clock)));
        record("software.publisher.create", appId, actor, grant.getTokenId());
        return new SoftwarePublisherCreatedResponse(queries.project(grant, token.entity()), token.rawToken());
    }
    @Transactional(readOnly = true)
    public List<SoftwarePublisherResponse> list(Long appId, Authentication auth) {
        requireSessionAdmin(auth); requireApp(appId);
        return queries.list(appId);
    }
    @Transactional
    public void revoke(Long appId, Long tokenId, Authentication auth) {
        String actor = requireSessionAdmin(auth); requireApp(appId);
        var grant = grants.findById(tokenId).filter(item -> Objects.equals(appId, item.getApplicationId()))
                .orElseThrow(() -> new DomainNotFoundException("error.software.publisher.notFound"));
        grant.revoke(Instant.now(clock)); grants.save(grant);
        tokens.revokeToken(tokenId, grant.getOwnerUserId());
        record("software.publisher.revoke", appId, actor, tokenId);
    }
    /** Do not trust the roles or scopes captured at session/token issuance. */
    public String requireMachine(Long appId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PlatformPrincipal principal)
                || !"api_token".equals(principal.oauthProvider()) || !(auth.getDetails() instanceof ApiTokenIdentity identity)
                || identity.tokenId() == null) throw forbidden();
        var token = tokenRepository.findById(identity.tokenId()).orElseThrow(SoftwarePublisherAppService::forbidden);
        var grant = grants.findById(identity.tokenId()).orElseThrow(SoftwarePublisherAppService::forbidden);
        Instant now = Instant.now(clock);
        if (!token.isValid(now) || (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now))
                || !Objects.equals(token.getUserId(), principal.userId())
                || !Objects.equals(grant.getOwnerUserId(), principal.userId())
                || !Objects.equals(grant.getApplicationId(), appId) || grant.getRevokedAt() != null
                || !scopes.parseScopes(token.getScopeJson()).contains("software:publish")) throw forbidden();
        requireCurrentAdmin(principal.userId()); requireApp(appId);
        return principal.userId();
    }
    private String requireSessionAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PlatformPrincipal principal)
                || "api_token".equals(principal.oauthProvider())) throw forbidden();
        requireCurrentAdmin(principal.userId()); return principal.userId();
    }
    private void requireCurrentAdmin(String owner) {
        if (users.findById(owner).filter(user -> user.isActive()).isEmpty()
                || roles.findByUserId(owner).stream().noneMatch(binding ->
                    List.of("SOFTWARE_ADMIN", "SUPER_ADMIN").contains(binding.getRole().getCode()))) throw forbidden();
    }
    private void requireApp(Long appId) {
        if (apps.findById(appId).isEmpty()) throw new DomainNotFoundException("error.software.application.notFound", appId);
    }
    private void record(String action, Long appId, String actor, Long tokenId) {
        audit.record(actor, action, "SOFTWARE_APPLICATION", appId, null, null, null, "{\"tokenId\":" + tokenId + "}");
    }
    private static DomainForbiddenException forbidden() { return new DomainForbiddenException("error.software.publisher.forbidden"); }
}
