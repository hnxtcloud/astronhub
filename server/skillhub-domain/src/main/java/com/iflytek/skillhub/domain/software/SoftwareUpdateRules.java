package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict machine coordinates independent of the human display version. */
public final class SoftwareUpdateRules {
    private static final String NUMBER = "(?:0|[1-9][0-9]*)";
    private static final String PRE = "(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
    private static final Pattern SEMVER = Pattern.compile(NUMBER + "\\." + NUMBER + "\\." + NUMBER
            + "(?:-" + PRE + "(?:\\." + PRE + ")*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private SoftwareUpdateRules() { }
    public static void version(String value) {
        if (value == null || value.length() > 255 || !SEMVER.matcher(value).matches()) invalid("version");
    }
    public static void path(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.startsWith("/")
                || value.chars().anyMatch(c -> c < 32 || c == 127 || "\\%?#:".indexOf(c) >= 0)) invalid("path");
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) invalid("path");
        }
    }
    public static void identity(String version, String tag, String os, String arch, String channel,
            String variant, String protocol, String piVersion) {
        version(version);
        if (tag == null || tag.length() > 255 || !(version.equals(tag) || ("v" + version).equals(tag) || ("V" + version).equals(tag))) invalid("tag");
        try {
            SoftwareTargetOs.valueOf(os);
            SoftwareTargetArch.valueOf(arch);
        } catch (IllegalArgumentException | NullPointerException ex) { invalid("target"); }
        if (!Set.of("stable", "preview").contains(channel == null ? "" : channel)) invalid("channel");
        if ("stable".equals(channel) && version.split("\\+", 2)[0].contains("-")) invalid("channel");
        if (variant == null || !variant.matches("[a-z0-9][a-z0-9-]{0,63}")) invalid("variant");
        if (!Set.of("ELECTRON", "BAIZE").contains(protocol == null ? "" : protocol)) invalid("protocol");
        if ("BAIZE".equals(protocol)) {
            version(piVersion);
            if (!"WINDOWS".equals(os) || !"X86_64".equals(arch)) invalid("target");
        }
    }
    public static void role(String role) {
        if (!Set.of("METADATA", "BLOCKMAP", "CHECKSUM", "SIGNATURE", "OTHER").contains(role == null ? "" : role)) invalid("role");
    }
    public static void invalid(String field) { throw new DomainBadRequestException("error.software.update.invalid", field); }
}
