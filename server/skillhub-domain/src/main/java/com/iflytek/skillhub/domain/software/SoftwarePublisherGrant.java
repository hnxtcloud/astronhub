package com.iflytek.skillhub.domain.software;

import jakarta.persistence.*;
import java.time.Instant;

/** An administrator-issued credential is bound to exactly one application. */
@Entity
@Table(name = "software_publisher_grant")
public class SoftwarePublisherGrant {
    @Id private Long tokenId;
    @Column(nullable = false) private Long applicationId;
    @Column(nullable = false, length = 128) private String ownerUserId;
    @Column(nullable = false, length = 128) private String grantedBy;
    @Column(nullable = false) private Instant createdAt;
    private Instant revokedAt;
    protected SoftwarePublisherGrant() { }
    public SoftwarePublisherGrant(Long tokenId, Long applicationId, String ownerUserId, String grantedBy, Instant now) {
        this.tokenId = tokenId; this.applicationId = applicationId; this.ownerUserId = ownerUserId;
        this.grantedBy = grantedBy; this.createdAt = now;
    }
    public Long getTokenId() { return tokenId; }
    public Long getApplicationId() { return applicationId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getGrantedBy() { return grantedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
