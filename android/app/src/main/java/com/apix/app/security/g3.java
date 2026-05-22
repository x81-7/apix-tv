package com.apix.app.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Base64;

public final class g3 {

    
    private static final String[] L = {
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5", // httpcanary
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5LnByZW1pdW0=", // canary premium
        "Y29tLm1pbmh1aS5uZXR3b3JrY2FwdHVyZQ==", // networkcapture
        "anAuY28uYmVjYXVzZS5uZXR3b3JrLmFuYWx5c2lz", // network analysis
        "Y29tLmNoYXJsZXMucHJveHk=", // charles
        "Y29tLmVnb3JvdmFuZHJleXJtLnBjYXByZW1vdGU=", // pcapremote
        "YXBwLmdyZXlzaGlydHMuc3NsY2FwdHVyZQ==", // sslcapture
        "Y29tLnJlcWJpbi5odHRwYmlu" // httpbin
    };

    private static String d(String s) {
        return new String(Base64.decode(s, Base64.DEFAULT));
    }

    public static String check(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        
        
        for (String b64 : L) {
            try {
                pm.getPackageInfo(d(b64), 0);
                k();
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        
        
        String h = System.getProperty("http.proxyHost");
        if (h != null && !h.isEmpty()) {
            k();
        }

        
        try {
            if (com.apix.app.security.g4.hasVpn()) {
                k();
            }
        } catch (Throwable ignored) {}

        try {
            if (com.apix.app.security.g4.hasDanger()) {
                k();
            }
        } catch (Throwable ignored) {}

        return null; 
    }

    private static void k() {
        
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
        throw new RuntimeException(); 
    }

    private g3() {}
}
