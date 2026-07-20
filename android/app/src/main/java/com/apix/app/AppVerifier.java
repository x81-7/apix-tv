package com.apix.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Debug;
import android.view.Display;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AppVerifier {

    private static final String TAG = "av";
    private static AppVerifier instance;
    private Context context;
    private Thread monitorThread;
    private volatile boolean running = false;

    private String currentHash = null;
    private int expectedDexCount = -1;

    private volatile List<String> allowedHashes = new ArrayList<>();
    private volatile List<String> blockedHashes = new ArrayList<>();
    private volatile boolean hashesLoaded = false;

    private static final String PREFS_NAME = "app_vf";
    private static final String KEY_LAST_CHECK = "lc";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;

    private static final String[] DP = {
        "com.guoshi.httpcanary", "com.guoshi.httpcanary.premium",
        "com.minhui.networkcapture", "jp.co.because.network.analysis",
        "com.charles.proxy", "com.egorovandreyrm.pcapremote",
        "app.greyshirts.sslcapture", "com.reqbin.httpbin",
        "io.anyline.xposed", "de.robv.android.xposed.installer",
        "org.lsposed.manager", "com.topjohnwu.magisk",
        "eu.chainfire.supersu", "me.weishu.exp",
        "com.saurik.substrate", "com.zachspong.temprootremovejb",
        "com.mt.mtmanager", "bin.mt.plus",
        "com.apktool.apktools", "com.jrummyapps.rootbrowser",
        "com.noshufou.android.su", "com.koushikdutta.superuser",
        "com.chelpus.lackypatch",
        "com.android.vending.billing.InAppBillingService.LACK",
        "com.ramdroid.appquarantine",
        "re.frida.server", "com.frida",
        "com.redfinger.app", "com.redfinger.cloud",
        "com.nowgg.cloud", "com.netease.mumu",
        "com.microvirt.memuime", "com.bignox.appcenter",
        "com.ldmnq.launcher3", "com.ldmnq.launcher",
        "com.kaopu.gameassistant", "com.excelliance.multiaccount",
        "com.parallel.space", "com.parallel.space.lite",
        "com.lbe.parallel.intl", "com.jumobile.smartapp.dual",
    };

    private static final String[] CI = {
        "vmos", "redfinger", "nowgg", "cloudphone", "remotegaming",
        "cloud_phone", "virtual_phone", "phonecloud", "genymotion",
        "tencent_cloud", "huawei_cloud", "alicloud", "aws_device_farm",
    };

    private static final int[] FP = {27042, 27043};
    
    // تم إرجاع الآيبيهات الثابتة كما كانت في كودك الأصلي
    private static final String[] VWP = {"172.19.0.", "172.16.0.2"};

    public interface VerifyCallback {
        void onComplete(boolean passed, String failReason);
    }

    private AppVerifier(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.currentHash = computeHash();
        this.expectedDexCount = countDex();
        fetchRemoteHashes();
    }

    public static synchronized AppVerifier getInstance(Context ctx) {
        if (instance == null) instance = new AppVerifier(ctx);
        return instance;
    }

    // ── الدالة الذكية لاكتشاف أجهزة الشاشات والتيفي بوكس ──
    private boolean isTvBox() {
        try {
            android.app.UiModeManager uiManager = (android.app.UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (uiManager != null && uiManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                return true;
            }
            String model = Build.MODEL.toLowerCase();
            String hardware = Build.HARDWARE.toLowerCase();
          
            if (model.contains("box") || model.contains("tv") || hardware.contains("amlogic") || hardware.contains("rockchip") || hardware.contains("allwinner")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean shouldRunCheck() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
        return (System.currentTimeMillis() - lastCheck) >= CHECK_INTERVAL_MS;
    }

    private void markCheckDone() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
    }

    private void fetchRemoteHashes() {
        new Thread(() -> {
            try { allowedHashes = SupabaseDataManager.fetchSignatures(context); } catch (Exception ignored) {}
            try { blockedHashes = SupabaseDataManager.fetchBlockedSignatures(context); } catch (Exception ignored) {}
            hashesLoaded = true;
        }).start();
    }

    public String getCurrentAppHash() { return currentHash; }

    public String runCheck() {
        boolean isTv = isTvBox();
        try { if (com.apix.app.x.vpnTunnelUp()) return "E01"; } catch (Throwable ignored) {}
        try { if (com.apix.app.x.hasDanger())   return "E02"; } catch (Throwable ignored) {}
        
        if (detectUnauthorizedVPN())  return "E03";
        if (detectBlockedHash())      return "E06";
        if (detectProxy())            return "E04";
        
        
        if (!isTv) {
            if (detectSniffers())         return "E05";
            if (detectSecondaryDisplay()) return "E07";
        }

        if (!shouldRunCheck()) return null;
        
        if (detectPrivateDNS()) return "E12";
        if (!isTv && detectCloudPhone()) return "E08";
        if (detectTampering())  return "E09";
        if (detectDebugger())   return "E10";
        if (detectFrida())      return "E11";
        
        markCheckDone();
        return null;
    }

    private boolean detectBlockedHash() {
        if (!hashesLoaded || blockedHashes.isEmpty() || currentHash == null) return false;
        return blockedHashes.contains(currentHash.toLowerCase());
    }

    public void runCheckAsync(VerifyCallback callback) {
        new Thread(() -> {
            boolean isTv = isTvBox();
            try { if (com.apix.app.x.vpnTunnelUp()) { if (callback != null) callback.onComplete(false, "E01"); return; } } catch (Throwable ignored) {}
            try { if (com.apix.app.x.hasDanger())   { if (callback != null) callback.onComplete(false, "E02"); return; } } catch (Throwable ignored) {}
            
            if (detectUnauthorizedVPN()) { if (callback != null) callback.onComplete(false, "E03"); return; }
            if (detectBlockedHash())     { if (callback != null) callback.onComplete(false, "E06"); return; }
            if (detectProxy())           { if (callback != null) callback.onComplete(false, "E04"); return; }
            
            if (!isTv) {
                if (detectSniffers())        { if (callback != null) callback.onComplete(false, "E05"); return; }
            }

            if (!shouldRunCheck()) {
                if (callback != null) callback.onComplete(true, null);
                return;
            }
            
            String result = null;
            if (detectPrivateDNS())      result = "E12";
            else if (!isTv && detectCloudPhone()) result = "E08";
            else if (detectTampering())  result = "E09";
            else if (detectDebugger())   result = "E10";
            else if (detectFrida())      result = "E11";
            
            if (result == null) markCheckDone();
            if (callback != null) callback.onComplete(result == null, result);
        }).start();
    }

    public void startMonitor() {
        if (running) return;
        running = true;

        monitorThread = new Thread(() -> {
            boolean isTv = isTvBox(); 
            
            while (running) {
                try {
                    try { if (com.apix.app.x.vpnTunnelUp()) { killApp(); return; } } catch (Throwable ignored) {}
                    try { if (com.apix.app.x.hasDanger())   { killApp(); return; } } catch (Throwable ignored) {}
                    
                    /
                    if (detectUnauthorizedVPN())     { killApp(); return; }
                    if (detectBlockedHash())         { killApp(); return; }
                    if (detectPrivateDNS())          { killApp(); return; }
                    if (detectProxy())               { killApp(); return; }
                    

                    if (!isTv) {
                        if (detectSniffers())            { killApp(); return; }
                        if (detectCloudPhone())          { killApp(); return; }
                        if (detectSecondaryDisplay())    { killApp(); return; }
                        if (detectHostsMod())            { killApp(); return; }
                    }

                    if (detectDynamicHashMismatch()) { killApp(); return; }
                    if (detectDebugger())            { killApp(); return; }
                    if (detectFrida())               { killApp(); return; }
                    if (detectTampering())           { killApp(); return; }

                    Thread.sleep(5 + (long)(Math.random() * 10)); 
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "m_sys_t");

        monitorThread.setDaemon(true);
        monitorThread.setPriority(Thread.MAX_PRIORITY);
        monitorThread.start();
    }

    public void stopMonitor() {
        running = false;
        if (monitorThread != null) { monitorThread.interrupt(); monitorThread = null; }
    }

    private void killApp() {
        running = false;
        
        // 1. انهيار الواجهة
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            throw new RuntimeException("E00");
        });

        // 2. قتل الخلفية
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : processes) {
                        if (proc.processName.contains(context.getPackageName())) {
                            android.os.Process.killProcess(proc.pid);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // 3. القتل القاسي والنهائي
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
        Runtime.getRuntime().halt(1);
    }

    private boolean detectDynamicHashMismatch() {
        if (!hashesLoaded || allowedHashes.isEmpty()) return false;
        if (currentHash == null) return true;
        return !allowedHashes.contains(currentHash.toLowerCase());
    }

    private boolean detectCloudPhone() {
        String model       = Build.MODEL.toLowerCase();
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String brand       = Build.BRAND.toLowerCase();
        String product     = Build.PRODUCT.toLowerCase();
        String device      = Build.DEVICE.toLowerCase();
        String hardware    = Build.HARDWARE.toLowerCase();
        String fingerprint = Build.FINGERPRINT.toLowerCase();
        String board       = Build.BOARD.toLowerCase();

        for (String indicator : CI) {
            if (model.contains(indicator) || manufacturer.contains(indicator) ||
                brand.contains(indicator) || product.contains(indicator) ||
                device.contains(indicator) || hardware.contains(indicator) ||
                fingerprint.contains(indicator) || board.contains(indicator)) {
                return true;
            }
        }

        if (model.contains("vmos") || Build.DISPLAY.toLowerCase().contains("vmos") ||
            new File("/data/data/com.vmos.pro").exists() ||
            new File("/data/data/com.vmos.app").exists()) return true;

        String[] cpf = {
            "/data/data/com.redfinger.app", "/data/data/com.redfinger.cloud",
            "/data/data/com.nowgg.cloud", "/system/app/VMOSFakeGps", "/data/vmos",
        };
        for (String path : cpf) { if (new File(path).exists()) return true; }

        String[] propKeys = {
            "ro.product.model", "ro.product.brand", "ro.product.manufacturer",
            "ro.product.device", "ro.product.name", "ro.build.fingerprint",
            "ro.build.display.id", "ro.boot.hardware", "ro.hardware",
            "init.svc.vmos_daemon", "persist.sys.vmos", "ro.kernel.qemu",
            "ro.boot.qemu", "ro.boot.selinux"
        };
        for (String key : propKeys) {
            String val = sysProp(key).toLowerCase();
            if (val.contains("vmos") || val.contains("cloud.phone") ||
                val.contains("redfinger") || val.contains("virtual.device")) return true;
        }
        return false;
    }

    private static String sysProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = c.getMethod("get", String.class);
            Object val = get.invoke(null, key);
            return val != null ? val.toString() : "";
        } catch (Exception e) { return ""; }
    }

    private boolean detectSecondaryDisplay() {
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display[] displays = dm.getDisplays();
                if (displays.length > 1) {
                    for (Display display : displays) {
                        if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                            int flags = display.getFlags();
                            if ((flags & Display.FLAG_PRESENTATION) != 0 ||
                                (flags & Display.FLAG_PRIVATE) != 0) return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean detectSniffers() {
        PackageManager pm = context.getPackageManager();
        for (String pkg : DP) {
            try { pm.getPackageInfo(pkg, 0); return true; }
            catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    private boolean detectProxy() {
        String proxyHost = System.getProperty("http.proxyHost");
        if (proxyHost != null && !proxyHost.isEmpty()) return true;
        try {
            String globalProxy = android.provider.Settings.Global.getString(
                context.getContentResolver(), "http_proxy");
            if (globalProxy != null && !globalProxy.isEmpty() && !globalProxy.equals(":0")) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean detectHostsMod() {
        try {
            File hostsFile = new File("/etc/hosts");
            if (hostsFile.exists() && hostsFile.length() > 10240) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean detectUnauthorizedVPN() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                if (ni.isUp() && (ni.getName().startsWith("tun") || ni.getName().startsWith("ppp"))) {
                    return !isWhitelistedVPN();
                }
            }

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return !isWhitelistedVPN();
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isWhitelistedVPN() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                if (ni.isUp() && (ni.getName().startsWith("tun") || ni.getName().startsWith("ppp"))) {
                    List<InetAddress> addresses = Collections.list(ni.getInetAddresses());
                    for (InetAddress addr : addresses) {
                        String ip = addr.getHostAddress();
                        if (ip != null) {
                            // مقارنة الـ IP مع المصفوفة المحلية الثابتة
                            for (String allowedIp : VWP) {
                                if (ip.startsWith(allowedIp) || ip.equals(allowedIp)) return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @SuppressWarnings("deprecation")
    private String computeHash() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo != null) {
                    Signature[] sigs = info.signingInfo.getApkContentsSigners();
                    if (sigs != null && sigs.length > 0) return hashSig(sigs[0]);
                }
            } else {
                info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
                if (info.signatures != null && info.signatures.length > 0)
                    return hashSig(info.signatures[0]);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String hashSig(Signature sig) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sig.toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private boolean detectPrivateDNS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String privateDnsMode = android.provider.Settings.Global.getString(
                    context.getContentResolver(), "private_dns_mode");
                if ("hostname".equals(privateDnsMode)) {
                    String hostname = android.provider.Settings.Global.getString(
                        context.getContentResolver(), "private_dns_specifier");
                    if (hostname != null) {
                        String lower = hostname.toLowerCase();
                        if (lower.contains("adguard") || lower.contains("nextdns") ||
                            lower.contains("dns.adblock") || lower.contains("dnsforge") ||
                            lower.contains("dns.quad9") || lower.contains("blahdns") ||
                            lower.contains("controld")) return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean detectDebugger() {
        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) return false;
        } catch (Exception ignored) {}
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true;
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream("/proc/self/status")));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    int pid = Integer.parseInt(line.substring(10).trim());
                    if (pid != 0) { reader.close(); return true; }
                    break;
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return false;
    }

    private boolean detectFrida() {
        for (int port : FP) {
            try { java.net.Socket s = new java.net.Socket("127.0.0.1", port); s.close(); return true; }
            catch (Exception ignored) {}
        }
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream("/proc/self/maps")));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("frida") || line.contains("gadget")) { reader.close(); return true; }
            }
            reader.close();
        } catch (Exception ignored) {}
        if (new File("/data/local/tmp/frida-server").exists() ||
            new File("/data/local/tmp/re.frida.server").exists()) return true;
        return false;
    }

    private boolean detectTampering() {
        if (expectedDexCount <= 0) return false;
        return countDex() != expectedDexCount;
    }

    private int countDex() {
        try {
            String apkPath = context.getApplicationInfo().sourceDir;
            ZipFile zipFile = new ZipFile(apkPath);
            int count = 0;
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".dex")) count++;
            }
            zipFile.close();
            return count;
        } catch (Exception e) { return -1; }
    }
}
