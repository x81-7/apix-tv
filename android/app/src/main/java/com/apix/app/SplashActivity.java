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
        // The authoritative handshake runs in proceedToMain before the native
        // sweep. This lets it propagate debug_kill_toasts first, so Debug builds
        // can actually display the native diagnostic instead of dying too early.
        new Handler(Looper.getMainLooper()).post(this::checkForUpdate);
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
                    com.apix.app.security.Enforcement.enforce(SplashActivity.this, fv);
                    return;
                } else if (v.status != null && "ACTIVE".equals(v.status)) {
                    // Clear any stale cached ban once the server confirms ACTIVE.
                    com.apix.app.security.Enforcement.cacheVerdict(SplashActivity.this, v);
                }
            } catch (Throwable ignored) {}

            // A previously cached server ban remains authoritative when the
            // gateway is unavailable or its encrypted response is invalid.
            if (!"ACTIVE".equals(verdictStatus)
                    && com.apix.app.security.Enforcement.isBannedCached(SplashActivity.this)) {
                com.apix.app.security.Enforcement.wipeChannelCache(SplashActivity.this);
                com.apix.app.security.Enforcement.silentExit(SplashActivity.this);
                return;
            }

            // Run guards only after handshake settings (including the Debug
            // diagnostic toggle) have been propagated into native atomics.
            com.apix.app.security.GuardRunner.runAll(SplashActivity.this);

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
                        com.apix.app.Net.nvpTerminate("vpn");
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
                AppVerifier.getInstance(SplashActivity.this).startMonitor();
            }));
        }).start();
    }

}

