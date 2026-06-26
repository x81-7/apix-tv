package com.apix.app;

import android.app.Activity;
import android.content.Context;
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
 * Ban / environment-danger screen.
 * UI is 100% programmatic — no layout XML file exists.
 * All string constants that could identify this class come from NDK (n4.cpp).
 * ProGuard renames this class in release builds.
 */
public class KillScreenActivity extends Activity {

    private static final int GOLD = 0xFFD4A017;

    public static void launch(
            Context ctx, String status,
            String banUntil, String reason, String telegramUrl) {
        Intent i = new Intent(ctx, KillScreenActivity.class);
        i.putExtra("s", status);
        i.putExtra("u", banUntil      != null ? banUntil      : "");
        i.putExtra("r", reason        != null ? reason        : "");
        i.putExtra("t", telegramUrl   != null ? telegramUrl   : "");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screenshot / screen-cast
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String status      = getIntent().getStringExtra("s");
        String banUntil    = getIntent().getStringExtra("u");
        String reason      = getIntent().getStringExtra("r");
        String telegramUrl = getIntent().getStringExtra("t");

        // ── Root layout ───────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setPadding(dp(24), dp(48), dp(24), dp(48));

        // ── Logo ─────────────────────────────────────────────────
        TextView logo = new TextView(this);
        logo.setText("APiX");
        logo.setTextColor(GOLD);
        logo.setTextSize(44);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        // ── Gold divider ─────────────────────────────────────────
        View divider = new View(this);
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(dp(60), dp(3));
        divP.gravity = Gravity.CENTER_HORIZONTAL;
        divP.setMargins(0, dp(8), 0, dp(32));
        divider.setLayoutParams(divP);
        divider.setBackgroundColor(GOLD);
        root.addView(divider);

        // ── Status icon (circle) ─────────────────────────────────
        TextView icon = new TextView(this);
        icon.setText("✕");
        icon.setTextColor(Color.parseColor("#E50914"));
        icon.setTextSize(52);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconP.gravity = Gravity.CENTER_HORIZONTAL;
        iconP.setMargins(0, 0, 0, dp(20));
        icon.setLayoutParams(iconP);
        root.addView(icon);

        // ── Main message ─────────────────────────────────────────
        TextView msg = new TextView(this);
        msg.setText(buildMessage(status, banUntil));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(18);
        msg.setTypeface(null, Typeface.BOLD);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 0, 0, dp(12));
        root.addView(msg);

        // ── Reason (optional) ────────────────────────────────────
        if (reason != null && !reason.isEmpty()) {
            TextView reasonView = new TextView(this);
            reasonView.setText(reason);
            reasonView.setTextColor(Color.parseColor("#999999"));
            reasonView.setTextSize(14);
            reasonView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rp.gravity = Gravity.CENTER_HORIZONTAL;
            rp.setMargins(0, 0, 0, dp(24));
            reasonView.setLayoutParams(rp);
            root.addView(reasonView);
        }

        // ── Telegram button (optional) ───────────────────────────
        if (telegramUrl != null && !telegramUrl.isEmpty()) {
            // بناء الرابط عبر NDK لإخفائه عن الـ decompiler
            final String tUrl = buildTelegramUrl(telegramUrl);
            Button btn = new Button(this);
            btn.setText("تواصل معنا");
            btn.setTextColor(Color.BLACK);
            btn.setBackgroundColor(GOLD);
            btn.setAllCaps(false);
            btn.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            bp.gravity = Gravity.CENTER_HORIZONTAL;
            bp.setMargins(0, dp(8), 0, 0);
            btn.setLayoutParams(bp);
            btn.setPadding(dp(32), dp(14), dp(32), dp(14));
            btn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tUrl)));
                } catch (Exception ignored) {}
            });
            root.addView(btn);
        }

        setContentView(root);
    }

    private String buildMessage(String status, String banUntil) {
        if (status == null) return "تم إيقاف التطبيق";
        switch (status) {
            case "TEMP_BAN":
                return "تم حظر حسابك بشكل مؤقت" +
                    (banUntil != null && !banUntil.isEmpty() ? "\nحتى: " + banUntil : "");
            case "PERMA_BAN":
                return "تم حظر حسابك بشكل نهائي";
            case "ENVIRONMENT_DANGER":
                return "بيئة غير آمنة\nيتعذر تشغيل التطبيق";
            case "ENVIRONMENT_EMULATOR":
            case "EMULATOR_BLOCKED":
                return "المحاكيات غير مدعومة";
            default:
                return "تم إيقاف التطبيق";
        }
    }

    /**
     * يبني رابط Telegram عبر NDK
     * الرابط لا يظهر كنص مقروء في الـ decompiler
     */
    private String buildTelegramUrl(String channelOrUrl) {
        if (channelOrUrl.startsWith("http")) return channelOrUrl;
        try {
            // nk1() = "https://", nk2() = "t.me/"
            return com.apix.app.x.nk1() + com.apix.app.x.nk2() + channelOrUrl;
        } catch (Throwable t) {
            return "https://t.me/" + channelOrUrl;
        }
    }

    // ── منع الخروج بأي طريقة ─────────────────────────────────────

    @Override
    public void onBackPressed() { /* ممنوع */ }

    @Override
    protected void onPause() {
        super.onPause();
        relaunch();
    }

    @Override
    protected void onStop() {
        super.onStop();
        relaunch();
    }

    private void relaunch() {
        try {
            Intent i = new Intent(getApplicationContext(), KillScreenActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("s", getIntent().getStringExtra("s"));
            i.putExtra("u", getIntent().getStringExtra("u"));
            i.putExtra("r", getIntent().getStringExtra("r"));
            i.putExtra("t", getIntent().getStringExtra("t"));
            getApplicationContext().startActivity(i);
        } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}