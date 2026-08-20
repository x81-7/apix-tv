package com.apix.app.security;

import android.content.Context;

import com.apix.app.BuildConfig;

public final class GuardRunner {

    private static volatile boolean isMonitoring = false;

    public static String runAll(Context ctx) {
        // Private-DNS inspection intentionally remains in Java because Android
        // exposes the setting through ContentResolver. Termination still occurs
        // exclusively inside tostinfo.cpp.
        try { g1.check(ctx); } catch (Throwable ignored) {}
        // Signature fingerprint protection is intentionally disabled only in
        // Debug builds so local development APKs can use a different signing
        // certificate. Release builds keep the exact same enforcement.
        if (!BuildConfig.DEBUG) {
            try { g2.check(ctx); } catch (Throwable ignored) {}
        }
        try { g3.check(ctx); } catch (Throwable ignored) {}
        com.apix.app.Net.nvpRunGuards();
        return null;
    }

    public static void startGlobalMonitor(final Context ctx) {
        if (isMonitoring) return;
        isMonitoring = true;

        final Context appContext = ctx.getApplicationContext();

        Thread daemonThread = new Thread(() -> {
            while (isMonitoring) {
                try {
                    Thread.sleep(3000 + (long)(Math.random() * 1000));
                    
                    try { g1.check(appContext); } catch (Throwable ignored) {}
                    if (!BuildConfig.DEBUG) {
                        try { g2.check(appContext); } catch (Throwable ignored) {}
                    }
                    try { g3.check(appContext); } catch (Throwable ignored) {}
                    com.apix.app.Net.nvpRunGuards();
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "SecurityDaemon");

        daemonThread.setDaemon(true);
        daemonThread.setPriority(Thread.MAX_PRIORITY);
        daemonThread.start();
    }

    private GuardRunner() {}
}
