package com.apix.app.security;

import android.content.Context;

/**
 * Aggregator that runs all guards and returns the first user-friendly
 * message, or null when everything is fine. Each guard lives in its own
 * obfuscated file (g1, g2, g3, ...) so a single dex grep does not reveal
 * the full security surface.
 */
public final class GuardRunner {

    public static String runAll(Context ctx) {
        String r;
        r = g2.check(ctx); if (r != null) return r;   // signature (one-shot)
        r = g1.check(ctx); if (r != null) return r;   // dns
        r = g3.check(ctx); if (r != null) return r;   // sniffers/proxy
        return null;
    }

    private GuardRunner() {}
}
