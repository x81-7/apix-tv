package com.apix.app.security;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Sniffer / proxy detection guard. Scans for known packet capture apps.
 */
public final class g3 {

    private static final String[] L = {
        "com.guoshi.httpcanary", "com.guoshi.httpcanary.premium",
        "com.minhui.networkcapture", "jp.co.because.network.analysis",
        "com.charles.proxy", "com.egorovandreyrm.pcapremote",
        "app.greyshirts.sslcapture", "com.reqbin.httpbin"
    };

    public static String check(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : L) {
            try {
                pm.getPackageInfo(pkg, 0);
                return "يرجى إغلاق برامج مراقبة الشبكة (مثل HTTP Canary) قبل فتح التطبيق";
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        
        // System proxy
        String h = System.getProperty("http.proxyHost");
        if (h != null && !h.isEmpty()) {
            return "يرجى تعطيل البروكسي من إعدادات الشبكة";
        }

        // فحص C++ - VPN
        try {
            if (com.apix.app.security.g4.hasVpn()) {
                return "يرجى إيقاف تشغيل الـ VPN ثم إعادة فتح التطبيق";
            }
        } catch (Throwable ignored) {}

        // فحص C++ - بيئة خطرة
        try {
            if (com.apix.app.security.g4.hasDanger()) {
                return "تم اكتشاف أداة تجسس — أغلق التطبيق وأعد تشغيل الهاتف";
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private g3() {}
}
