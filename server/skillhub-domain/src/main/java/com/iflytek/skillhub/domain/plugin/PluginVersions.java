package com.iflytek.skillhub.domain.plugin;

/** Strict SemVer 2.0 identities shared by offline adapters and draft creation. */
public final class PluginVersions {
    private PluginVersions() {}

    public static boolean valid(String version) {
        if (version == null
                || version.length() > 64
                || !version.matches(
                        "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"))
            return false;
        int hyphen = version.indexOf('-');
        if (hyphen < 0 || (version.indexOf('+') >= 0 && hyphen > version.indexOf('+'))) return true;
        int build = version.indexOf('+', hyphen);
        for (String identifier :
                version.substring(hyphen + 1, build < 0 ? version.length() : build).split("\\.")) {
            if (identifier.matches("[0-9]+")
                    && identifier.length() > 1
                    && identifier.startsWith("0")) return false;
        }
        return true;
    }
}
