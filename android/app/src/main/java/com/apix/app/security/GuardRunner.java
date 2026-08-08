package com.apix.app.security;

import android.content.Context;

public final class GuardRunner {

    private static volatile boolean isMonitoring = false;

    public static String runAll(Context ctx) {
        try { com.apix.app.Net.nvpRunGuards(); } catch (Throwable ignored) {}
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
