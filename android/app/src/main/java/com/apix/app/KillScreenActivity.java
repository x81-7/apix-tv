package com.apix.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * KillScreen — غلاف بسيط جداً (UI فقط).
 * كل المنطق والنصوص مخفية داخل المكتبة Native (v.so).
 */
public class KillScreenActivity extends Activity {

    private static final int GOLD = 0xFFD4A017;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String status = getIntent().getStringExtra("s");
        String banUntil = getIntent().getStringExtra("u");
        String reason = getIntent().getStringExtra("r");
        String telegramUrl = getIntent().getStringExtra("t");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(48), dp(24), dp(48));

        // العنوان (عبر Native)
        TextView logo = new TextView(this);
        logo.setText(x.ksTitle()); 
        logo.setTextColor(GOLD);
        logo.setTextSize(44);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        // أيقونة (مبسطة)
        TextView icon = new TextView(this);
        icon.setText(x.ksShield()); // إشارة الحماية من Native
        icon.setTextColor(Color.parseColor("#E50914"));
        icon.setTextSize(52);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        // الرسالة (المنطق كاملاً في الـ Native)
        TextView msg = new TextView(this);
        msg.setText(x.ksMessage(status, banUntil));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(18);
        msg.setGravity(Gravity.CENTER);
        root.addView(msg);

        // زر التواصل (الرابط مُجمع داخل Native)
        if (telegramUrl != null && !telegramUrl.isEmpty()) {
            Button btn = new Button(this);
            btn.setText(x.ksButton());
            btn.setTextColor(Color.BLACK);
            btn.setBackgroundColor(GOLD);
            btn.setOnClickListener(v -> {
                try {
                    String finalUrl = x.ks1() + x.ks2() + telegramUrl;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)));
                } catch (Exception ignored) {}
            });
            root.addView(btn);
        }

        setContentView(root);
    }

    // ── حماية الإغلاق ─────────────────────────────────────────────
    @Override public void onBackPressed() { }
    @Override protected void onPause() { super.onPause(); relaunch(); }
    @Override protected void onStop() { super.onStop(); relaunch(); }

    private void relaunch() {
        try {
            Intent i = new Intent(getApplicationContext(), KillScreenActivity.class);
            i.putExtras(getIntent());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
