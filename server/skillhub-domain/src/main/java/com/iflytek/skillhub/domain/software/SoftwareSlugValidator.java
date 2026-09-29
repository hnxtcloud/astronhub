package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes and validates software-center slugs. The slug is unique inside
 * the software center only and is unrelated to skill/namespace slugs.
 */
public final class SoftwareSlugValidator {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9]([a-z0-9-._]*[a-z0-9])?$");
    private static final int MAX_LENGTH = 100;

    private SoftwareSlugValidator() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new DomainBadRequestException("error.software.slug.invalid", (Object) null);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH || !VALID.matcher(normalized).matches()) {
            throw new DomainBadRequestException("error.software.slug.invalid", normalized);
        }
        return normalized;
    }
}
