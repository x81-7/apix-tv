package com.apix.app.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import com.apix.app.SupabaseDataManager;

import java.security.MessageDigest;
import java.util.List;

/**
 * g2 — توقيع التطبيق
 * يتحقق في كل مرة (لا كاش) ومقارنة مزدوجة:
 * 1. مقارنة مع القائمة المسموحة من Supabase
 * 2. مقارنة مع الهاش المضمّن في NDK (لا يمكن تعديله)
 */
public final class g2 {

    private static final String T = "g2";

    public static String check(Context ctx) {
        String hash = computeHash(ctx);
        if (hash == null) {
            return "تعذر التحقق من توقيع التطبيق";
        }

        // ── التحقق 1: الهاش المضمّن في NDK ──────────────────────────
        // هذا لا يمكن تعديله حتى بعد فك وإعادة تجميع APK
        try {
            String ndkHash = g4.kh(); // هاش التوقيع من vault.cpp
            if (ndkHash != null && !ndkHash.isEmpty()
                    && !ndkHash.equals("__HASH__")) {
                if (!hash.equalsIgnoreCase(ndkHash.trim())) {
                    Log.w(T, "NDK hash mismatch");
                    return "نسخة معدّلة — TAMPERED_MOD";
                }
                // NDK تحقق بنجاح — لا حاجة لطلب شبكة
                return null;
            }
        } catch (Throwable ignored) {}

        // ── التحقق 2: القائمة من Supabase (fallback) ─────────────────
        List<String> allowed = SupabaseDataManager.fetchSignatures(ctx);
        if (allowed == null || allowed.isEmpty()) {
            return null; // لا قائمة = مرور آمن
        }
        if (allowed.contains(hash.toLowerCase())) {
            return null;
        }
        return "نسختك ليست رسمية، يرجى تنزيل النسخة الأصلية من القناة الرسمية";
    }

    public static String currentHash(Context ctx) {
        return computeHash(ctx);
    }

    @SuppressWarnings("deprecation")
    private static String computeHash(Context ctx) {
        try {
            PackageInfo info;
            Signature sig = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo != null) {
                    Signature[] sigs = info.signingInfo.getApkContentsSigners();
                    if (sigs != null && sigs.length > 0) sig = sigs[0];
                }
            } else {
                info = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(),
                    PackageManager.GET_SIGNATURES);
                if (info.signatures != null
                        && info.signatures.length > 0)
                    sig = info.signatures[0];
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