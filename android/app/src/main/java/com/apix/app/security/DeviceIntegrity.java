package com.apix.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.MediaDrm;
import android.provider.Settings;
import android.os.Build;
import android.os.Debug;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Collects hardware-bound device id (MediaDrm Widevine), signature SHA-256
 * and classes.dex CRC32 in obfuscated, indirect ways. Also exposes
 * environment-danger checks (debugger / hooking frameworks).
 *
 * Public API kept tiny so attackers can't easily grep call sites.
 */
public final class DeviceIntegrity {

    private static final UUID WV = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final String P = "_di_state";
    private static final String K_INSTALL = "_inst";

    public static String deviceId(Context ctx) {
        // MediaDrm Widevine ID is bound to motherboard and survives factory reset
        try {
            MediaDrm drm = new MediaDrm(WV);
            byte[] raw = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            try { drm.close(); } catch (Throwable ignored) {}
            if (raw != null && raw.length > 0) {
                return sha256Hex(raw);
            }
        } catch (Throwable ignored) {}
        // Fallback 1: ANDROID_ID is stable per device + signing key and survives reinstall.
        try {
            String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty() && !"9774d56d682e549c".equals(androidId)) {
                return "aid_" + sha256Hex(androidId.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}

        // Fallback 2: hardware fingerprint
        try {
            String fb = Build.MANUFACTURER + "|" + Build.MODEL + "|" + Build.BOARD + "|" + Build.FINGERPRINT;
            return "fb_" + sha256Hex(fb.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** SHA-256 of currently installed APK signing certificate. */
    public static String signatureHash(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi;
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
            } else {
                pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
                sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0) return null;
            return sha256Hex(sigs[0].toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    /** CRC32 of classes.dex inside the installed APK. */
    public static String dexChecksum(Context ctx) {
        ZipFile zf = null;
        try {
            String src = ctx.getApplicationInfo().sourceDir;
            zf = new ZipFile(new File(src));
            ZipEntry e = zf.getEntry("classes.dex");
            if (e == null) return null;
            InputStream in = zf.getInputStream(e);
            CRC32 crc = new CRC32();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) crc.update(buf, 0, n);
            in.close();
            return Long.toHexString(crc.getValue());
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
    }

    /** Returns short danger label or null. */
    public static String environmentDanger(Context ctx) {
        // Anti-debug
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return "DEBUGGER";
        // Anti-hook: scan /proc/self/maps for known frameworks
        try {
            File maps = new File("/proc/self/maps");
            if (maps.exists() && maps.canRead()) {
                FileInputStream fis = new FileInputStream(maps);
                byte[] data = new byte[(int) Math.min(maps.length(), 1024 * 200)];
                int read = fis.read(data);
                fis.close();
                if (read > 0) {
                    String s = new String(data, 0, read);
                    String[] needles = {"frida", "xposed", "substrate", "magisk", "lspatch", "lsposed"};
                    for (String n : needles) if (s.contains(n)) return "HOOK:" + n;
                }
            }
        } catch (Throwable ignored) {}
        // Installed packages (best-effort, no QUERY_ALL_PACKAGES)
        try {
            PackageManager pm = ctx.getPackageManager();
            String[] pkgs = {"de.robv.android.xposed.installer", "io.github.lsposed.manager",
                    "com.topjohnwu.magisk", "eu.chainfire.supersu"};
            for (String p : pkgs) {
                try { pm.getPackageInfo(p, 0); return "HOOK_APP:" + p; } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * isFreshInstall = true the FIRST time we see this install (or after the
     * user wiped app data). Stored in plain SharedPreferences which gets
     * cleared on uninstall + clear-data.
     */
    public static boolean consumeFreshInstall(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE);
        if (sp.getBoolean(K_INSTALL, false)) return false;
        sp.edit().putBoolean(K_INSTALL, true).apply();
        return true;
    }

    private static String sha256Hex(byte[] in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(in);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            Log.w("DI", "sha256 fail", t);
            return null;
        }
    }

    private DeviceIntegrity() {}
}
