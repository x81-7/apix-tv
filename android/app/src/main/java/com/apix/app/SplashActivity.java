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
import android.content.Context; // تمت إضافة هذا الاستيراد لحل مشكلة الـ Context

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
                String downloadUrl = update.optString("downloadUrl", "");
                String message = update.optString("message", "هناك تحديث جديد متوفر");
                String versionName = update.optString("versionName", "");
                String installMode = update.optString("installMode", "internal");
                boolean forceUpdate = update.optBoolean("forceUpdate", false);

                int requiredVersionCode = update.optInt("requiredVersionCode", 0);
                int currentVersionCode = BuildConfig.VERSION_CODE;
                String currentVersionName = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME;
                boolean belowRequired = requiredVersionCode > 0 && currentVersionCode < requiredVersionCode;
                
                boolean alreadyOnThisVersion = !versionName.isEmpty()
                        && compareVersionNames(currentVersionName, versionName) >= 0;
                final boolean mustForce = forceUpdate || belowRequired;

                if (alreadyOnThisVersion) {
                    runOnUiThread(this::proceedToMain);
                    return;
                }

                if (isActive && !downloadUrl.isEmpty()) {
                    runOnUiThread(() -> showInternalUpdatePage(message, downloadUrl, versionName, mustForce));
                } else {
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

            String gm = com.apix.app.security.GuardRunner.runAll(SplashActivity.this);
            if (gm != null) {
                runOnUiThread(() -> showGuardMessage(gm));
                return;
            }

            try {
                com.apix.app.security.HandshakeClient.Verdict v =
                    com.apix.app.security.HandshakeClient.handshake(
                        SplashActivity.this,
                        BuildConfig.CLOUD_URL,
                        BuildConfig.CLOUD_ANON_KEY,
                        BuildConfig.VERSION_NAME);

                // --- إصلاح مشكلة الكاش هنا ---
                // حفظ حالة الحظر محلياً (تم استبدال ctx بـ SplashActivity.this)
                if (v.status != null && !"ERROR".equals(v.status)) {
                    SplashActivity.this.getSharedPreferences("ban_cache", Context.MODE_PRIVATE)
                       .edit()
                       .putString("last_status", v.status)
                       .putLong("last_check", System.currentTimeMillis())
                       .apply();
                }

                // إذا كان الاتصال فاشلاً، تحقق من الكاش المحلي
                String effectiveStatus = v.status;
                if ("ERROR".equals(v.status)) {
                    android.content.SharedPreferences bc =
                        SplashActivity.this.getSharedPreferences("ban_cache", Context.MODE_PRIVATE);
                    String cached = bc.getString("last_status", "ACTIVE");
                    long lastCheck = bc.getLong("last_check", 0L);
                    // إذا الكاش أقل من 24 ساعة واحتوى على حظر، طبّقه
                    boolean fresh = System.currentTimeMillis() - lastCheck < 86400_000L;
                    if (fresh && !"ACTIVE".equals(cached) && !"ERROR".equals(cached)) {
                        effectiveStatus = cached;
                    }
                }

                // فحص الحظر بناءً على الـ effectiveStatus لضمان تفعيل الكاش
                if (effectiveStatus != null
                        && !"ACTIVE".equals(effectiveStatus)
                        && !"ERROR".equals(effectiveStatus)) {
                    final String fStatus = effectiveStatus;
                    final com.apix.app.security.HandshakeClient.Verdict fv = v;
                    runOnUiThread(() -> {
                        KillScreenActivity.launch(
                            SplashActivity.this,
                            fStatus, // تمرير الحالة الصحيحة
                            fv.banUntil,
                            fv.reason != null ? fv.reason : "تم الحظر", // تفادي أي قيمة Null
                            fv.telegramUrl);
                        finish();
                    });
                    return;
                }
                // --- نهاية الإصلاح ---

            } catch (Throwable ignored) {}

            com.apix.app.data.p6 repo =
                new com.apix.app.data.p6(SplashActivity.this);

            if (repo.ok() && repo.fresh()) {
                runOnUiThread(() -> statusText.setText("جاري الفتح..."));
                new Thread(() -> {
                    int sv = repo.remoteVer();
                    int lv = repo.localVer();
                    if (sv > 0 && sv != lv) {
                        SupabaseDataManager.fetchRemote(
                            SplashActivity.this,
                            new SupabaseDataManager.DataCallback() {
                                @Override
                                public void onSuccess(
                                        SupabaseDataManager.DataBundle d) {
                                    repo.sync(d);
                                    repo.saveVer(sv);
                                }
                                @Override
                                public void onError(String e) {
                                    Log.w(TAG, "bg sync: " + e);
                                }
                            });
                    }
                }).start();
                launchMain();
                return;
            }

            runOnUiThread(() -> statusText.setText("جاري تحميل القنوات..."));
            SupabaseDataManager.fetchRemote(
                SplashActivity.this,
                new SupabaseDataManager.DataCallback() {
                    @Override
                    public void onSuccess(SupabaseDataManager.DataBundle d) {
                        repo.sync(d);
                        int sv = repo.remoteVer();
                        if (sv > 0) repo.saveVer(sv);
                        launchMain();
                    }
                    @Override
                    public void onError(String e) {
                        if (repo.ok()) {
                            launchMain();
                        } else {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(
                                    android.view.View.GONE);
                                errorText.setVisibility(
                                    android.view.View.VISIBLE);
                                errorText.setText(
                                    "فشل الاتصال بالسيرفر");
                            });
                        }
                    }
                });
        }).start();
    }

    private void launchMain() {
        boolean ge = false;
        String cc = "";
        try {
            JSONObject gate = SupabaseDataManager.fetchGateConfig();
            if (gate != null) {
                ge = gate.optBoolean("enabled", false);
                cc = gate.optString("bypassCode", "");
            }
        } catch (Exception ignored) {}

        try {
            SupabaseDataManager.syncDeveloperUUIDs(SplashActivity.this);
        } catch (Throwable ignored) {}

        GateActivity.revalidateBypass(SplashActivity.this, cc);
        boolean bp = GateActivity.isBypassed(SplashActivity.this);
        Class<?> target = (ge && !bp)
            ? GateActivity.class
            : ComposeActivity.class;

        runOnUiThread(() ->
            AdManager.maybeRunAppOpenGate(
                SplashActivity.this, () -> {
                    Intent i = new Intent(SplashActivity.this, target);
                    String aj = getIntent()
                        .getStringExtra("notification_action");
                    if (aj != null && !aj.isEmpty())
                        i.putExtra("notification_action", aj);
                    startActivity(i);
                    finish();
                    overridePendingTransition(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out);
                    if (!BuildConfig.DEBUG) {
                        AppVerifier.getInstance(
                            SplashActivity.this).startMonitor();
                    }
                }));
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
