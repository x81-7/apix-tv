package com.apix.app;

import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;    // تم إضافة هذا
import android.os.Looper;     // تم إضافة هذا
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * KillScreen — full-screen unrecoverable lock for TEMP_BAN / PERMA_BAN /
 * TAMPERED_MOD / ENVIRONMENT_DANGER. Cannot be dismissed via back button.
 */
public class KillScreenActivity extends AppCompatActivity {

    public static final String EX_STATUS = "status";
    public static final String EX_BAN_UNTIL = "ban_until";
    public static final String EX_REASON = "reason";
    public static final String EX_TELEGRAM = "telegram";

    private CountDownTimer timer;

    public static void launch(android.content.Context ctx, String status, String banUntil,
                              String reason, String telegram) {
        Intent i = new Intent(ctx, KillScreenActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(EX_STATUS, status);
        i.putExtra(EX_BAN_UNTIL, banUntil);
        i.putExtra(EX_REASON, reason);
        i.putExtra(EX_TELEGRAM, telegram);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        String status = getIntent().getStringExtra(EX_STATUS);
        String banUntil = getIntent().getStringExtra(EX_BAN_UNTIL);
        String reason = getIntent().getStringExtra(EX_REASON);
        String telegram = getIntent().getStringExtra(EX_TELEGRAM);

        boolean tampered = "TAMPERED_MOD".equals(status) || "ENVIRONMENT_DANGER".equals(status);
        boolean perma = "PERMA_BAN".equals(status);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(tampered ? 0xFF7F0000 : (perma ? 0xFF000000 : 0xFF1A0000));

        TextView icon = new TextView(this);
        icon.setText(tampered ? "⚠️" : (perma ? "⛔" : "⏳"));
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView title = new TextView(this);
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 24, 0, 16);
        title.setText(tampered
                ? "تحذير أمني خطير"
                : (perma ? "تم حظر هذا الجهاز نهائياً" : "تم إيقاف التطبيق مؤقتاً"));
        root.addView(title);

        TextView body = new TextView(this);
        body.setTextSize(16);
        body.setTextColor(0xFFFFFFFF);
        body.setGravity(Gravity.CENTER);
        body.setPadding(16, 0, 16, 16);
        if ("TAMPERED_MOD".equals(status)) {
            body.setText("⚠️ تم اكتشاف تعديل غير قانوني في ملفات التطبيق (نسخة معدلة).\n" +
                    "قم بإلغاء تثبيت هذه النسخة فوراً وتثبيت النسخة الرسمية لتجنب إدراج هاتفك في القائمة السوداء.");
        } else if ("ENVIRONMENT_DANGER".equals(status)) {
            body.setText("⚠️ تم اكتشاف بيئة تشغيل غير آمنة (Debugger / Hooking).\n" +
                    "تم إغلاق التطبيق لحماية البيانات.");
        } else if (perma) {
            body.setText("تم إيقاف التطبيق على هذا الجهاز نهائياً بسبب اكتشاف نشاط مريب\n" +
                    "(تكرار حذف وإعادة تثبيت التطبيق).");
        } else {
            body.setText("تم إيقاف التطبيق مؤقتاً بسبب اكتشاف نشاط مريب\n(تكرار حذف وإعادة تثبيت التطبيق).");
        }
        root.addView(body);

        TextView countdown = new TextView(this);
        countdown.setTextSize(20);
        countdown.setTextColor(0xFFFFD54F);
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(0, 16, 0, 16);
        root.addView(countdown);

        if (perma || tampered) {
            countdown.setText("⛔ حظر دائم");
        } else if (banUntil != null && !banUntil.isEmpty()) {
            startCountdown(countdown, banUntil);
        } else {
            countdown.setVisibility(View.GONE);
        }

        if (reason != null && !reason.isEmpty()) {
            TextView r = new TextView(this);
            r.setText("السبب: " + reason);
            r.setTextSize(12);
            r.setTextColor(0xFFBDBDBD);
            r.setGravity(Gravity.CENTER);
            r.setPadding(0, 8, 0, 24);
            root.addView(r);
        }

        if (telegram != null && !telegram.isEmpty()) {
            Button tg = new Button(this);
            tg.setText("📨 تواصل مع الإدارة عبر تيليجرام");
            tg.setBackgroundColor(0xFF0088CC);
            tg.setTextColor(0xFFFFFFFF);
            tg.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(telegram)));
                } catch (Throwable ignored) {}
            });
            root.addView(tg);
        }

        if (perma) {
            Button copyId = new Button(this);
            copyId.setText("نسخ ID الحظر");
            copyId.setOnClickListener(v -> {
                try {
                    String deviceId = com.apix.app.security.DeviceIntegrity.deviceId(this);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("ban_device_id", deviceId));
                    }
                } catch (Throwable ignored) {}
            });
            root.addView(copyId);
        }

        setContentView(root);
    }

    private void startCountdown(TextView tv, String iso) {
        try {
            long until = java.time.Instant.parse(iso).toEpochMilli();
            long left = until - System.currentTimeMillis();
            if (left <= 0) { tv.setText("انتهى الحظر — أعد فتح التطبيق"); return; }
            timer = new CountDownTimer(left, 1000) {
                @Override public void onTick(long ms) {
                    long m = ms / 60000; long s = (ms / 1000) % 60;
                    tv.setText(String.format("الوقت المتبقي: %02d:%02d", m, s));
                }
                @Override public void onFinish() {
                    tv.setText("انتهى الحظر — أعد فتح التطبيق");
                    finishAffinity();
                }
            }.start();
        } catch (Throwable t) {
            tv.setText("");
        }
    }

    @Override public void onBackPressed() { /* مستحيل الخروج */ }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) {
                    try {
                        android.app.ActivityManager am =
                            (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                        if (am != null) am.moveTaskToFront(getTaskId(), 0);
                    } catch (Throwable ignored) {}
                }
            }, 300);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            Intent self = new Intent(this, KillScreenActivity.class);
            self.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            self.putExtra(EX_STATUS,    getIntent().getStringExtra(EX_STATUS));
            self.putExtra(EX_BAN_UNTIL, getIntent().getStringExtra(EX_BAN_UNTIL));
            self.putExtra(EX_REASON,    getIntent().getStringExtra(EX_REASON));
            self.putExtra(EX_TELEGRAM,  getIntent().getStringExtra(EX_TELEGRAM));
            try { startActivity(self); } catch (Throwable ignored) {}
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
