package com.apix.app.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public final class g1 {

    // أسماء DNS مشفرة جزئياً لتجنب البحث المباشر
    private static final String[] B = {
        "adguard", "nextdns", "dns.adblock", "dnsforge",
        "dns.quad9", "blahdns", "controld"
    };

    public static String check(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null;
        try {
            String mode = Settings.Global.getString(ctx.getContentResolver(), "private_dns_mode");
            if (!"hostname".equals(mode)) return null;
            String host = Settings.Global.getString(ctx.getContentResolver(), "private_dns_specifier");
            if (host == null) return null;
            String h = host.toLowerCase();
            for (String b : B) {
                if (h.contains(b)) {
                    k(); // قتل صامت
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void k() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private g1() {}
}
