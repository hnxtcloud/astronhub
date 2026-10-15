package com.iflytek.skillhub.service.plugin;

/** Scoped write fence propagated through the synchronous adapters without changing native plans. */
public final class PluginPublicationWriteGuard implements AutoCloseable {
    private static final ThreadLocal<Runnable> CURRENT = new ThreadLocal<>();
    private final Runnable previous;

    private PluginPublicationWriteGuard(Runnable check) {
        previous = CURRENT.get();
        CURRENT.set(check);
    }

    public static PluginPublicationWriteGuard bind(Runnable check) {
        return new PluginPublicationWriteGuard(check);
    }

    public static void check() {
        var check = CURRENT.get();
        if (check != null) check.run();
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
