package com.apix.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.apix.app.SupabaseDataManager;

import java.security.MessageDigest;
import java.util.List;

/**
 * Fingerprint guard. Validates the APK signing certificate against allowed
 * hashes from Supabase. Runs ONCE on first launch only — result is cached
 * locally and not re-verified to save backend calls.
 */
public final class g2 {

    private static final String P = "g2_st";
    private static final String K_OK = "ok";
    private static final String K_HASH = "h";

    public static String check(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE);
        boolean ok = sp.getBoolean(K_OK, false);
        if (ok) return null; // already validated, never check again

        String hash = computeHash(ctx);
        if (hash == null) {
            return "تعذر التحقق من توقيع التطبيق";
        }

        List<String> allowed = SupabaseDataManager.fetchSignatures(ctx);
        if (allowed == null || allowed.isEmpty()) {
            // No allowed list configured — pass-through (don't block legitimate users)
            return null;
        }

        if (allowed.contains(hash.toLowerCase())) {
            sp.edit().putBoolean(K_OK, true).putString(K_HASH, hash).apply();
            return null;
        }
        return "نسختك ليست رسمية، يرجى تنزيل النسخة الأصلية من القناة الرسمية";
    }

    @SuppressWarnings("deprecation")
    private static String computeHash(Context ctx) {
        try {
            PackageInfo info;
            Signature sig = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo != null) {
                    Signature[] sigs = info.signingInfo.getApkContentsSigners();
                    if (sigs != null && sigs.length > 0) sig = sigs[0];
                }
            } else {
                info = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(), PackageManager.GET_SIGNATURES);
                if (info.signatures != null && info.signatures.length > 0) sig = info.signatures[0];
            }
            if (sig == null) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(sig.toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private g2() {}
}
