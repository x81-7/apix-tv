package com.apix.app.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Base64;

public final class g3 {

    private static final String[] L = {
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5",
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5LnByZW1pdW0=",
        "Y29tLm1pbmh1aS5uZXR3b3JrY2FwdHVyZQ==",
        "anAuY28uYmVjYXVzZS5uZXR3b3JrLmFuYWx5c2lz",
        "Y29tLmNoYXJsZXMucHJveHk=",
        "Y29tLmVnb3JvdmFuZHJleXJtLnBjYXByZW1vdGU=",
        "YXBwLmdyZXlzaGlydHMuc3NsY2FwdHVyZQ==",
        "Y29tLnJlcWJpbi5odHRwYmlu"
    };

    private static String d(String s) {
        return new String(Base64.decode(s, Base64.DEFAULT));
    }

    public static String check(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        for (String b64 : L) {
            try {
                pm.getPackageInfo(d(b64), 0);
                k(ctx, "Sniffer App: " + d(b64));
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        String h = System.getProperty("http.proxyHost");
        if (h != null && !h.isEmpty()) {
            k(ctx, "HTTP Proxy Detected");
        }

        try {
            if (com.apix.app.x.hasVpn()) {
                k(ctx, "hasVpn (C++)");
            }
        } catch (Throwable ignored) {}

        try {
            if (com.apix.app.x.hasDanger()) {
                k(ctx, "hasDanger (C++)");
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static void k(Context ctx, String reason) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(ctx, "القاتل: g3.java | السبب: " + reason, android.widget.Toast.LENGTH_LONG).show();
            new android.os.Handler().postDelayed(() -> {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }, 5000);
        });
    }

    private g3() {}
}
