package com.apix.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import com.apix.app.BuildConfig;

/**
 * Splash screen with security check, update check via Supabase, and notification permission.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    
    // ── دالة فضح سبب القتل وتأخيره 5 ثواني ──
    private void delayedKill(final String reason) {
        runOnUiThread(() -> {
            android.widget.Toast.makeText(SplashActivity.this, "القاتل: SplashActivity | الدالة: " + reason, android.widget.Toast.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                finishAffinity();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }, 5000);
        });
    }
    
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView errorText;
    private LinearLayout updatePanel;
    private TextView updateTitle;
    private TextView updateMessage;
    private TextView updateStatus;
    private ProgressBar updateProgress;
    private Button updateInstallButton;
    private Button updateSkipButton;
    private boolean bootStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Block screen-recording / casting from capturing splash content.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        // === STRICT EMULATOR BAN (per project policy) ===
        if (!BuildConfig.DEBUG && com.apix.app.security.DeviceIntegrity.shouldStrictBanEmulator(this)) {
            try {
                android.widget.Toast.makeText(this,
                        "تشغيل التطبيق على المحاكيات غير مسموح",
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {}
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                finishAffinity();
                System.exit(0);
            }, 1500);
            setContentView(R.layout.activity_splash);
            return;
        }

        setContentView(R.layout.activity_splash);

        statusText = findViewById(R.id.splash_status);
        progressBar = findViewById(R.id.splash_progress);
        errorText = findViewById(R.id.splash_error);
        updatePanel = findViewById(R.id.splash_update_panel);
        updateTitle = findViewById(R.id.update_title);
        updateMessage = findViewById(R.id.update_message);
        updateStatus = findViewById(R.id.update_status);
        updateProgress = findViewById(R.id.update_progress);
        updateInstallButton = findViewById(R.id.update_install_button);
        updateSkipButton = findViewById(R.id.update_skip_button);

        statusText.setVisibility(View.VISIBLE);
        statusText.setText("التحقق من إذن الإشعارات...");
        AdManager.refreshCacheAsync(this);

        ensureNotificationPermissionThenBoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bootStarted && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            ensureNotificationPermissionThenBoot();
        }
    }

    private void ensureNotificationPermissionThenBoot() {
        NotificationService.createNotificationChannel(this);
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            } else {
                showNotificationRequiredDialog();
            }
            return;
        }

        if (bootStarted) return;
        bootStarted = true;
        statusText.setText("جاري تشغيل التطبيق...");
        NotificationService.init(this);
        startBootFlow();
    }

    private void startBootFlow() {
        if (BuildConfig.DEBUG) {
            checkForUpdate();
            return;
        }

        AppVerifier.getInstance(this).runCheckAsync((passed, failReason) -> {
            if (passed) {
                new Handler(Looper.getMainLooper()).post(this::checkForUpdate);
            } else {
                delayedKill("AppVerifier.runCheckAsync (" + failReason + ")");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                ensureNotificationPermissionThenBoot();
            } else {
                showNotificationRequiredDialog();
            }
        }
    }

    private void showNotificationRequiredDialog() {
        bootStarted = false;
        statusText.setText("يجب تفعيل الإشعارات للمتابعة");
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("الإشعارات مطلوبة")
                .setMessage("لن يعمل التطبيق قبل تفعيل إذن الإشعارات.")
                .setCancelable(false)
                .setPositiveButton("تفعيل", (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
                    } else {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(intent);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        }
                    }
                })
                .setNegativeButton("خروج", (dialog, which) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .show();
    }

    // الدالة الرياضية لمقارنة أرقام الإصدارات بشكل صحيح (مثلاً 1.0.1 مع 1.0.2)
    private static int compareVersionNames(String a, String b) {
        try {
            String[] sa = a.replaceAll("[^0-9.]", "").split("\\.");
            String[] sb = b.replaceAll("[^0-9.]", "").split("\\.");
            int n = Math.max(sa.length, sb.length);
            for (int i = 0; i < n; i++) {
                int x = i < sa.length && !sa[i].isEmpty() ? Integer.parseInt(sa[i]) : 0;
                int y = i < sb.length && !sb[i].isEmpty() ? Integer.parseInt(sb[i]) : 0;
                if (x != y) return Integer.compare(x, y);
            }
            return 0;
        } catch (Throwable t) { return -1; }
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                JSONObject update = SupabaseDataManager.fetchAppUpdate();
                if (update == null) { runOnUiThread(this::proceedToMain); return; }

                boolean isActive = update.optBoolean("isActive", false);
                boolean deleted = update.optBoolean("deleted", false);
                if (deleted || !isActive) { runOnUiThread(this::proceedToMain); return; }
                
                String downloadUrl = update.optString("downloadUrl", "");
                if (downloadUrl.isEmpty()) { runOnUiThread(this::proceedToMain); return; }

                String message = update.optString("message", "هناك تحديث جديد متوفر");
                String versionName = update.optString("versionName", "");
                boolean forceUpdate = update.optBoolean("forceUpdate", false);

                int requiredVersionCode = update.optInt("requiredVersionCode", 0);
                int currentVersionCode = BuildConfig.VERSION_CODE;
                String currentVersionName = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME;
                
                // 1. فحص ذكي: هل التطبيق محدث بالفعل؟
                boolean alreadyOnThisVersion = false;
                if (!versionName.isEmpty()) {
                    // مقارنة النصوص (مثال 1.2.0 مع 1.1.0)
                    alreadyOnThisVersion = compareVersionNames(currentVersionName, versionName) >= 0;
                } else if (requiredVersionCode > 0) {
                    // مقارنة الأكواد إذا لم يتوفر النص
                    alreadyOnThisVersion = (currentVersionCode >= requiredVersionCode);
                } else {
                    // إذا لم يرسل السيرفر أي معلومات، نعتبره محدثاً
                    alreadyOnThisVersion = true;
                }

                // 2. هل التطبيق الحالي أقل من الإصدار الأدنى المسموح به؟
                boolean belowRequired = (requiredVersionCode > 0 && currentVersionCode < requiredVersionCode);
                
                // 3. متى نجبر المستخدم على التحديث؟
                boolean mustForce = forceUpdate || belowRequired;

                // 4. القرار النهائي: إذا لم يكن محدثاً، نعرض شاشة التحديث
                if (!alreadyOnThisVersion) {
                    runOnUiThread(() -> showInternalUpdatePage(message, downloadUrl, versionName, mustForce));
                } else {
                    // التطبيق محدث بالفعل! اذهب للرئيسية وتجاهل أي أمر آخر.
                    runOnUiThread(this::proceedToMain);
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check error", e);
                runOnUiThread(this::proceedToMain);
            }
        }).start();
    }

    private void showInternalUpdatePage(String message, String downloadUrl, String versionName, boolean force) {
        progressBar.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        updatePanel.setVisibility(View.VISIBLE);
        String title = (force ? "تحديث إلزامي" : "تحديث جديد") + (!versionName.isEmpty() ? " (" + versionName + ")" : "");
        updateTitle.setText(title);
        updateMessage.setText(message + (force ? "\n\nيجب تثبيت هذا التحديث قبل استخدام التطبيق." : ""));
        updateStatus.setText("جاهز لتنزيل التحديث داخل التطبيق");
        updateProgress.setProgress(0);
        updateSkipButton.setVisibility(force ? View.GONE : View.VISIBLE);
        updateSkipButton.setOnClickListener(v -> proceedToMain());
        updateInstallButton.setOnClickListener(v -> {
            updateInstallButton.setEnabled(false);
            updateSkipButton.setEnabled(false);
            updateStatus.setText("جاري تنزيل التحديث...");
            UpdateInstaller.download(SplashActivity.this, downloadUrl, new UpdateInstaller.Callback() {
                @Override public void onProgress(int percent) {
                    updateProgress.setProgress(percent);
                    updateStatus.setText("تم تنزيل " + percent + "%");
                }

                @Override public void onReady(java.io.File apk) {
                    updateProgress.setProgress(100);
                    updateStatus.setText("اكتمل التنزيل، افتح نافذة التثبيت لإكمال التحديث");
                    updateInstallButton.setText("فتح التثبيت");
                    updateInstallButton.setEnabled(true);
                    updateInstallButton.setOnClickListener(btn -> UpdateInstaller.installApk(SplashActivity.this, apk));
                    UpdateInstaller.installApk(SplashActivity.this, apk);
                }

                @Override public void onError(String err) {
                    updateStatus.setText("فشل تنزيل التحديث، تحقق من الرابط وحاول مرة أخرى");
                    updateInstallButton.setText("إعادة المحاولة");
                    updateInstallButton.setEnabled(true);
                    if (!force) updateSkipButton.setEnabled(true);
                }
            });
        });
    }

    private void proceedToMain() {
        new Thread(() -> {
            // Native consolidated guard — obfuscated sniffing/instrumentation
            // sweep. Silently terminates the process from native code on any
            // live threat (no boolean returned to Java to patch).
            try { com.apix.app.x.guardOrDie(); } catch (Throwable ignored) {}

            // Run extra guards (DNS / sniffers / signature)
            String guardMsg = com.apix.app.security.GuardRunner.runAll(SplashActivity.this);
            if (guardMsg != null) {
                runOnUiThread(() -> showGuardMessage(guardMsg));
                return;
            }

            // Server-authoritative anti-tamper / ban handshake
            String verdictStatus = "ERROR";
            try {
                String supaUrl = com.apix.app.Net.base();
                String anonKey = com.apix.app.Net.anon();
                com.apix.app.security.HandshakeClient.Verdict v =
                        com.apix.app.security.HandshakeClient.handshake(SplashActivity.this, supaUrl, anonKey,
                                com.apix.app.BuildConfig.VERSION_NAME);
                verdictStatus = v.status;
                if (v.status != null && !"ACTIVE".equals(v.status) && !"ERROR".equals(v.status)) {
                    final com.apix.app.security.HandshakeClient.Verdict fv = v;
                    if ("VPN_BLOCK".equals(fv.status)) {
                        com.apix.app.security.Enforcement.cacheVerdict(SplashActivity.this, fv);
                        delayedKill("Server Handshake -> VPN_BLOCK");
                    } else if ("MESSAGE".equals(fv.mode)) {
                        com.apix.app.security.Enforcement.cacheVerdict(SplashActivity.this, fv);
                        if (fv.wipe) com.apix.app.security.Enforcement.wipeChannelCache(SplashActivity.this);
                        runOnUiThread(() -> showBanMessage(fv.message));
                    } else {
                        delayedKill("Server Handshake -> Security Ban");
                    }
                    return;
                } else if (v.status != null && "ACTIVE".equals(v.status)) {
                    // Clear any stale cached ban once the server confirms ACTIVE.
                    com.apix.app.security.Enforcement.cacheVerdict(SplashActivity.this, v);
                }
            } catch (Throwable ignored) {}

            // Fail-safe VPN gate: if the handshake could NOT confirm an ACTIVE
            // verdict (network error / blocked request) AND a VPN is locally
            // active while the panel has VPN-blocking enabled, we must not fall
            // open into the app — force close. This closes the "VPN enabled
            // before launch" hole where the server round-trip may not complete.
            try {
                boolean confirmedActive = "ACTIVE".equals(verdictStatus);
                if (!confirmedActive
                        && com.apix.app.security.DeviceIntegrity.isVpnActive(SplashActivity.this)) {
                    android.content.SharedPreferences vp =
                            getSharedPreferences("vpn_cache", MODE_PRIVATE);
                    if (vp.getBoolean("vpn_block_enabled", false)) {
                        delayedKill("Local VPN Gate -> isVpnActive");
                        return;
                    }
                }
            } catch (Throwable ignored) {}


            boolean gateEnabled = false;
            String currentCode = "";
            try {
                JSONObject gate = SupabaseDataManager.fetchGateConfig();
                if (gate != null) {
                    gateEnabled = gate.optBoolean("enabled", false);
                    currentCode = gate.optString("bypassCode", "");
                }
            } catch (Exception ignored) {}

            try { SupabaseDataManager.syncDeveloperUUIDs(SplashActivity.this); } catch (Throwable ignored) {}

            GateActivity.revalidateBypass(SplashActivity.this, currentCode);
            boolean bypassed = GateActivity.isBypassed(SplashActivity.this);

            Class<?> target = (gateEnabled && !bypassed) ? GateActivity.class : ComposeActivity.class;
            runOnUiThread(() -> AdManager.maybeRunAppOpenGate(SplashActivity.this, () -> {
                Intent intent = new Intent(SplashActivity.this, target);
                String actionJson = getIntent().getStringExtra("notification_action");
                if (actionJson != null && !actionJson.isEmpty()) intent.putExtra("notification_action", actionJson);
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                if (!BuildConfig.DEBUG) {
                    AppVerifier.getInstance(SplashActivity.this).startMonitor();
                }
            }));
        }).start();
    }

    private void showGuardMessage(String msg) {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("تنبيه")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("إعادة المحاولة", (d, w) -> {
                d.dismiss();
                proceedToMain();
            })
            .setNegativeButton("خروج", (d, w) -> { finishAffinity(); System.exit(0); })
            .show();
    }

    /** Blocking full-screen message for panel bans / VPN blocks. */
    private void showBanMessage(String msg) {
        try {
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            if (updatePanel != null) updatePanel.setVisibility(View.GONE);
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(msg != null && !msg.isEmpty()
                    ? msg : "تم حظرك بسبب استخدامك غير الشرعي للتطبيق");
        } catch (Throwable ignored) {}
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("تم إيقاف الوصول")
            .setMessage(msg != null && !msg.isEmpty()
                    ? msg : "تم حظرك بسبب استخدامك غير الشرعي للتطبيق")
            .setCancelable(false)
            .setPositiveButton("خروج", (d, w) -> { finishAffinity(); System.exit(0); })
            .show();
    }

    /**
     * VPN block enforced as a FORCED CLOSE. We surface the reason so the user
     * knows why, then terminate the app automatically after a short delay
     * (and immediately if they tap the exit button). Unlike a panel ban, a
     * disallowed VPN must not leave the app open in the background.
     */
    private void showVpnBlockThenClose(String msg) {
        final String text = (msg != null && !msg.isEmpty())
                ? msg : "يرجى إيقاف الـ VPN لاستخدام التطبيق";
        try {
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            if (updatePanel != null) updatePanel.setVisibility(View.GONE);
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(text);
        } catch (Throwable ignored) {}
        final Runnable kill = () -> {
            try { finishAffinity(); } catch (Throwable ignored) {}
            try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) {}
            System.exit(0);
        };
        try {
            new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("تم إيقاف الوصول")
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton("خروج", (d, w) -> kill.run())
                .show();
        } catch (Throwable ignored) {}
        // Force close automatically even if the user ignores the dialog.
        new Handler(Looper.getMainLooper()).postDelayed(kill, 4000);
    }
}

