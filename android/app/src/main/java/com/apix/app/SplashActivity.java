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

        // === STRICT EMULATOR BAN (per project policy) ===
        // Allowed only when this device's UUID is on the developer override list
        // (system_settings.developer_uuids → mirrored into local prefs by
        //  CloudDataManager / SupabaseDataManager on each successful sync).
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
            new Handler(Looper.getMainLooper()).post(() -> {
                if (passed) {
                    checkForUpdate();
                } else {
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText(failReason != null ? failReason : "فشل فحص الأمان");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        finishAffinity();
                        System.exit(0);
                    }, 3000);
                }
            });
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

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                JSONObject update = SupabaseDataManager.fetchAppUpdate();
                if (update == null) { runOnUiThread(this::proceedToMain); return; }

                boolean isActive = update.optBoolean("isActive", false);
                String downloadUrl = update.optString("downloadUrl", "");
                String message = update.optString("message", "هناك تحديث جديد متوفر");
                String versionName = update.optString("versionName", "");
                String installMode = update.optString("installMode", "external");
                boolean forceUpdate = update.optBoolean("forceUpdate", false);

                // If a target versionCode is set in the panel and we're below it, also force.
                int requiredVersionCode = update.optInt("requiredVersionCode", 0);
                int currentVersionCode = BuildConfig.VERSION_CODE;
                boolean belowRequired = requiredVersionCode > 0 && currentVersionCode < requiredVersionCode;
                final boolean mustForce = forceUpdate || belowRequired;

                if (isActive && !downloadUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(message, downloadUrl, versionName, installMode, mustForce));
                } else {
                    runOnUiThread(this::proceedToMain);
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check error", e);
                runOnUiThread(this::proceedToMain);
            }
        }).start();
    }

    private void showUpdateDialog(String message, String downloadUrl, String versionName, String installMode, boolean force) {
        String title = (force ? "تحديث إلزامي" : "تحديث جديد") + (!versionName.isEmpty() ? " (" + versionName + ")" : "");
        AlertDialog.Builder b = new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message + (force ? "\n\nهذا التحديث إلزامي ولا يمكن المتابعة بدونه." : ""))
                .setCancelable(!force)
                .setPositiveButton("تثبيت", (dialog, which) -> {
                    if ("internal".equalsIgnoreCase(installMode)) {
                        UpdateInstaller.downloadAndInstall(SplashActivity.this, downloadUrl);
                        if (force) {
                            // Keep splash alive — installer will replace the app.
                            return;
                        }
                    } else {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))); } catch (Exception ignored) {}
                    }
                    if (!force) proceedToMain();
                    else finishAffinity();
                });
        if (!force) {
            b.setNegativeButton("إلغاء", (dialog, which) -> { dialog.dismiss(); proceedToMain(); })
             .setOnCancelListener(dialog -> proceedToMain());
        } else {
            b.setNegativeButton("خروج", (dialog, which) -> { finishAffinity(); System.exit(0); });
        }
        b.show();
    }

    private void proceedToMain() {
        new Thread(() -> {
            // Run extra guards (DNS / sniffers / signature) — show messages instead of killing
            String guardMsg = com.apix.app.security.GuardRunner.runAll(SplashActivity.this);
            if (guardMsg != null) {
                runOnUiThread(() -> showGuardMessage(guardMsg));
                return;
            }

            // ── Server-authoritative anti-tamper / ban handshake ──
            try {
                String supaUrl = BuildConfig.CLOUD_URL;
                String anonKey = BuildConfig.CLOUD_ANON_KEY;
                com.apix.app.security.HandshakeClient.Verdict v =
                        com.apix.app.security.HandshakeClient.handshake(SplashActivity.this, supaUrl, anonKey,
                                com.apix.app.BuildConfig.VERSION_NAME);
                if (v.status != null && !"ACTIVE".equals(v.status) && !"ERROR".equals(v.status)) {
                    final com.apix.app.security.HandshakeClient.Verdict fv = v;
                    runOnUiThread(() -> {
                        KillScreenActivity.launch(SplashActivity.this, fv.status, fv.banUntil, fv.reason, fv.telegramUrl);
                        finish();
                    });
                    return;
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

            // Mirror developer-allow-list (UUIDs) into local prefs so the
            // emulator strict-ban gate works offline on next launch.
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
}
