package com.iflytek.skillhub.service.plugin;

/** Shared portable archive path checks; callers handle directory markers and localized errors. */
final class PluginArchivePathPolicy {
    private PluginArchivePathPolicy() {}

    static boolean isSafe(String path) {
        if (path == null
                || path.isBlank()
                || path.length() > 1024
                || path.startsWith("/")
                || path.contains("\\")
                || path.contains(":")
                || path.chars().anyMatch(c -> c < 32 || c == 127)) return false;
        for (String part : path.split("/", -1)) {
            if (part.isBlank()
                    || part.equals(".")
                    || part.equals("..")
                    || part.endsWith(".")
                    || part.endsWith(" ")
                    || part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"))
                return false;
        }
        return true;
    }
}
