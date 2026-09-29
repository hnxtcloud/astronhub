package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** A user holding the SOFTWARE_ADMIN duty. */
public record SoftwareAdminGrantResponse(
        String userId,
        String displayName,
        String email,
        java.util.List<String> allRoles,
        Instant grantedAt
) {
}
