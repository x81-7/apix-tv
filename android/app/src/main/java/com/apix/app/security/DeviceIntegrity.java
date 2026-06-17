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
                    String[] needles = {"frida", "xposed", "substrate", "lspatch", "lsposed"};
                    for (String n : needles) if (s.contains(n)) return "HOOK:" + n;
                }
            }
        } catch (Throwable ignored) {}
        // Installed packages (best-effort, no QUERY_ALL_PACKAGES).
        
        try {
            PackageManager pm = ctx.getPackageManager();
            String[] pkgs = {
                "de.robv.android.xposed.installer", // Xposed
                "io.github.lsposed.manager",         // LSPosed
                "com.topjohnwu.magisk",              // Magisk الرسمي (مستقر/canary/debug)
                "io.github.huskydg.magisk",          // Magisk فرع Kitsune/HuskyDG
                "io.github.vvb2060.magisk",          // Magisk فرع Delta/community
                "me.weishu.kernelsu",                // KernelSU
                "me.bmax.apatch"                     // APatch
            };
            for (String p : pkgs) {
                try { pm.getPackageInfo(p, 0); return "HOOK_APP:" + p; } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * MODIFIED FOR TV BOX COMPATIBILITY & STRICT CLOUD EMULATOR BLOCKING
     */
    public static boolean isEmulator() {
        try {
            String fp  = String.valueOf(Build.FINGERPRINT).toLowerCase();
            String mdl = String.valueOf(Build.MODEL).toLowerCase();
            String mfr = String.valueOf(Build.MANUFACTURER).toLowerCase();
            String brd = String.valueOf(Build.BRAND).toLowerCase();
            String dev = String.valueOf(Build.DEVICE).toLowerCase();
            String prd = String.valueOf(Build.PRODUCT).toLowerCase();
            String hw  = String.valueOf(Build.HARDWARE).toLowerCase();
            String bd  = String.valueOf(Build.BOARD).toLowerCase();

            String haystack = fp + "|" + mdl + "|" + mfr + "|" + brd + "|" + dev + "|" + prd + "|" + hw + "|" + bd;

            // 1) Cloud Emulators & Server-Side VMs (Redfinger, VMOS, AWS, Tencent)
            String[] cloudVms = {"redfinger", "vmos", "vphone", "netmulator", "tencent", "cloudvm", "aliyun"};
            for (String c : cloudVms) if (haystack.contains(c)) return true;

            // 2) Generic Android Studio / SDK emulator markers
            // REMOVED: "test-keys", "generic", "unknown" as they are common in Chinese TV Boxes
            if (fp.contains("generic_x86") || fp.contains("emu") || fp.contains("vbox")
                    || fp.contains("sdk_gphone") || fp.contains("ranchu") || fp.contains("goldfish")) return true;
            if (mdl.contains("google_sdk") || mdl.contains("emulator") || mdl.contains("android sdk")) return true;
            if (mfr.contains("genymotion")) return true;
            if (brd.equals("generic") && dev.equals("generic")) return true; // Strict generic check
            if (prd.contains("sdk") || prd.contains("emulator") || prd.contains("simulator") || prd.contains("vbox")) return true;
            if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox") || hw.contains("ttvm") || hw.contains("intel")) return true;
            if (bd.contains("qemu") || bd.contains("vbox")) return true;

            // 3) LDPlayer / BlueStacks / Nox / MEmu — popular RE emulators
            String[] emuMarkers = {"ldplayer", "bluestacks", "noxplayer", "nox ", "memu", "andy",
                    "droid4x", "ttvm", "windroye", "mumu", "phoenix os", "koplayer"};
            for (String m : emuMarkers) if (haystack.contains(m)) return true;

            // 4) Telltale files only present in emulator images
            String[] emuFiles = {
                "/dev/socket/qemud", "/dev/qemu_pipe", "/system/lib/libc_malloc_debug_qemu.so",
                "/sys/qemu_trace", "/system/bin/qemu-props", "/dev/socket/genyd",
                "/dev/socket/baseband_genyd", "/system/lib/libdroid4x.so",
                "/system/bin/microvirt-prop", "/system/bin/nox-prop", "/system/bin/ldinit",
                "/system/bin/windroyed", "/proc/ldbinder"
            };
            for (String p : emuFiles) {
                try { if (new File(p).exists()) return true; } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Reads developer allow-list (UUIDs) from system_settings cache. */
    private static java.util.Set<String> developerOverrides(Context ctx) {
        try {
            android.content.SharedPreferences sp =
                    ctx.getSharedPreferences("apix_dev_overrides", Context.MODE_PRIVATE);
            String json = sp.getString("developer_uuids", null);
            if (json == null || json.isEmpty()) return java.util.Collections.emptySet();
            org.json.JSONArray arr = new org.json.JSONArray(json);
            java.util.HashSet<String> out = new java.util.HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                String v = arr.optString(i, null);
                if (v != null && !v.isEmpty()) out.add(v.toLowerCase());
            }
            return out;
        } catch (Throwable t) {
            return java.util.Collections.emptySet();
        }
    }

    /**
     * STRICT emulator gate consumed by SplashActivity.
     * Returns true ONLY when emulator is detected AND the device id is NOT
     * present in the developer override list (system_settings.developer_uuids).
     */
    public static boolean shouldStrictBanEmulator(Context ctx) {
        if (!isEmulator()) return false;
        java.util.Set<String> overrides = developerOverrides(ctx);
        if (overrides.isEmpty()) return true;
        String myId = String.valueOf(deviceId(ctx)).toLowerCase();
        return !overrides.contains(myId);
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
