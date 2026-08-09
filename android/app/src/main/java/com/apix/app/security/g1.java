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
                    k(); 
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void k() {
        // DNS check remains in Java by design; TostInfo enforces silent kill
        // in release and diagnostic toast+kill in debug when admin toggle is on.
        TostInfo.report("g1", "dns");
    }

    private g1() {}
}
