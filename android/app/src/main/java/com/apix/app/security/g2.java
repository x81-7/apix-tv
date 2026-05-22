package com.apix.app.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.apix.app.SupabaseDataManager;

import java.security.MessageDigest;
import java.util.List;

public final class g2 {

    public static String check(Context ctx) {
        String hash = computeHash(ctx);
        if (hash == null) {
            k();
            return null;
        }

        try {
            String ndkHash = g4.kh();
            if (ndkHash != null && !ndkHash.isEmpty() && !ndkHash.equals("__HASH__")) {
                if (!hash.equalsIgnoreCase(ndkHash.trim())) {
                    k();
                }
                return null;
            }
        } catch (Throwable ignored) {}

        List<String> allowed = SupabaseDataManager.fetchSignatures(ctx);
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        if (allowed.contains(hash.toLowerCase())) {
            return null;
        }
        
        k(); 
        return null;
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
                info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo != null) {
                    Signature[] sigs = info.signingInfo.getApkContentsSigners();
                    if (sigs != null && sigs.length > 0) sig = sigs[0];
                }
            } else {
                info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
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

    private static void k() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private g2() {}
}
