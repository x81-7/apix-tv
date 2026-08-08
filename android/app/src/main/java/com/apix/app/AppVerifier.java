package com.apix.app;

import android.content.Context;

/**
 * Compatibility bridge retained for existing activity call sites.
 * Detection, policy, diagnostics, and termination all execute in libv.so.
 */
public final class AppVerifier {
    private static AppVerifier instance;
    private volatile boolean running;
    private Thread monitorThread;

    public interface VerifyCallback {
        void onComplete(boolean passed, String failReason);
    }

    private AppVerifier(Context ignored) {}

    public static synchronized AppVerifier getInstance(Context ctx) {
        if (instance == null) instance = new AppVerifier(ctx.getApplicationContext());
        return instance;
    }

    public String getCurrentAppHash() { return null; }

    public String runCheck() {
        Net.nvpRunGuards();
        return null;
    }

    public void runCheckAsync(VerifyCallback callback) {
        new Thread(() -> {
            Net.nvpRunGuards();
            if (callback != null) callback.onComplete(true, null);
        }, "n_g").start();
    }

    public synchronized void startMonitor() {
        if (running) return;
        running = true;
        monitorThread = new Thread(() -> {
            while (running) {
                Net.nvpRunGuards();
                try { Thread.sleep(3000L); }
                catch (InterruptedException ignored) { return; }
            }
        }, "n_m");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public synchronized void stopMonitor() {
        running = false;
        if (monitorThread != null) monitorThread.interrupt();
        monitorThread = null;
    }
}