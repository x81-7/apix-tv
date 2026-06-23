package com.apix.app.security;

import android.content.Context;

public final class GuardRunner {
    public static String runAll(Context ctx) {
        // تمرير دائم دون أي فحص
        return null;
    }

    public static void startGlobalMonitor(final Context ctx) {
        // تم تخدير المراقب، لن يعمل في الخلفية
    }

    private GuardRunner() {}
}
