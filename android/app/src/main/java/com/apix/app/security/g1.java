package com.apix.app.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public final class g1 {

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
                    k(ctx, "Private DNS: " + b); 
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void k(Context ctx, String reason) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(ctx, "القاتل: g1.java | السبب: " + reason, android.widget.Toast.LENGTH_LONG).show();
            new android.os.Handler().postDelayed(() -> {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }, 5000);
        });
    }

    private g1() {}
}
