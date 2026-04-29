package com.apix.app.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/**
 * DNS / Private DNS guard. Detects ad-blocking DNS hostnames that may interfere
 * with stream URLs. Returns null when ok or a localized message when blocked.
 */
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
                    return "يرجى تعطيل DNS الخاص (Private DNS) من إعدادات الهاتف ثم إعادة فتح التطبيق";
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private g1() {}
}
