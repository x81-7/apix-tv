package com.apix.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.HashMap;

/**
 * Front gate screen — manual stream entry + bypass code.
 * Shown before the main app when enabled in dashboard.
 */
public class GateActivity extends AppCompatActivity {

    private static final String PREFS = "gate_prefs";
    private static final String KEY_BYPASS_OK = "bypass_ok";
    private static final String KEY_BYPASS_CODE = "bypass_code_used";

    private EditText nameField, linkField, uaField, refererField, clearKeyField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gate);

        nameField = findViewById(R.id.gate_name);
        linkField = findViewById(R.id.gate_link);
        uaField = findViewById(R.id.gate_ua);
        refererField = findViewById(R.id.gate_referer);
        clearKeyField = findViewById(R.id.gate_clearkey);
        TextView subtitleView = findViewById(R.id.gate_subtitle);

        MaterialButton playBtn = findViewById(R.id.gate_play);
        MaterialButton telegramBtn = findViewById(R.id.gate_telegram);
        MaterialButton aboutBtn = findViewById(R.id.gate_about);

        // One-tap play (don't steal focus first)
        playBtn.setFocusable(true);
        playBtn.setFocusableInTouchMode(false);
        playBtn.setOnClickListener(v -> handlePlay());

        // One-tap About (don't steal focus first either)
        aboutBtn.setFocusable(true);
        aboutBtn.setFocusableInTouchMode(false);
        aboutBtn.setOnClickListener(v -> {
            startActivity(new Intent(GateActivity.this, AboutActivity.class));
        });

        // Fetch gate config from Supabase
        new Thread(() -> {
            try {
                JSONObject cfg = SupabaseDataManager.fetchGateConfig();
                String telegramUrl = cfg != null ? cfg.optString("telegramUrl", "https://t.me/apix_tv") : "https://t.me/apix_tv";
                String title = cfg != null ? cfg.optString("title", "") : "";
                String subtitle = cfg != null ? cfg.optString("subtitle", "") : "";
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(title)) ((android.widget.TextView) findViewById(R.id.gate_title)).setText(title);
                    if (!TextUtils.isEmpty(subtitle)) ((android.widget.TextView) findViewById(R.id.gate_subtitle)).setText(subtitle);
                    subtitleView.setVisibility(View.VISIBLE);
                    telegramBtn.setOnClickListener(v -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)));
                        } catch (Exception ignored) {}
                    });
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void handlePlay() {
        String input = linkField.getText().toString().trim();
        String name = nameField.getText().toString().trim();

        // Check if this is a bypass code (no link, just digits/text in link or name field)
        // The user said: code goes in any field they choose. We accept it from the link field.
        if (!TextUtils.isEmpty(input) && isLikelyCode(input)) {
            verifyBypassCode(input);
            return;
        }
        // Also accept code from name field as fallback
        if (TextUtils.isEmpty(input) && !TextUtils.isEmpty(name) && isLikelyCode(name)) {
            verifyBypassCode(name);
            return;
        }

        if (TextUtils.isEmpty(input)) {
            android.widget.Toast.makeText(this, "أدخل رابط أو كود الدخول", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Build StreamConfig and pass to ComposeActivity
        StreamConfig stream = new StreamConfig();
        stream.url = input;
        stream.title = TextUtils.isEmpty(name) ? "بث يدوي" : name;
        stream.actionType = "native";

        StreamConfig.Headers h = new StreamConfig.Headers();
        h.userAgent = nullIfEmpty(uaField.getText().toString().trim());
        h.referer = nullIfEmpty(refererField.getText().toString().trim());
        stream.headers = h;

        // ClearKey
        String clearKey = clearKeyField.getText().toString().trim();
        if (!TextUtils.isEmpty(clearKey) && clearKey.contains(":")) {
            String[] parts = clearKey.split(":", 2);
            StreamConfig.DrmConfig drm = new StreamConfig.DrmConfig();
            drm.scheme = "clearkey";
            drm.keyId = parts[0].trim();
            drm.key = parts[1].trim();
            stream.drm = drm;
            // Auto-detect dash
            if (input.toLowerCase().contains(".mpd")) stream.actionType = "native";
        }

        String json = new Gson().toJson(stream);
        Intent it = new Intent(this, ComposeActivity.class);
        it.putExtra("streamConfig", json);
        startActivity(it);
        finish();
    }

    private void verifyBypassCode(String code) {
        new Thread(() -> {
            try {
                JSONObject cfg = SupabaseDataManager.fetchGateConfig();
                String expected = cfg != null ? cfg.optString("bypassCode", "") : "";
                if (!TextUtils.isEmpty(expected) && expected.equals(code)) {
                    SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                    sp.edit()
                        .putBoolean(KEY_BYPASS_OK, true)
                        .putString(KEY_BYPASS_CODE, code)
                        .apply();
                    runOnUiThread(this::goToMain);
                } else {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "كود غير صحيح", android.widget.Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "تعذر التحقق", android.widget.Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void goToMain() {
        startActivity(new Intent(this, ComposeActivity.class));
        finish();
    }

    private static boolean isLikelyCode(String s) {
        // No URL scheme and short → treat as code
        if (s.startsWith("http://") || s.startsWith("https://")) return false;
        return s.length() <= 16 && !s.contains(".") && !s.contains("/");
    }

    private static String nullIfEmpty(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }

    /** Has the user already entered the correct bypass code before? */
    public static boolean isBypassed(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BYPASS_OK, false);
    }

    /** Re-validate bypass code against current dashboard value. */
    public static void revalidateBypass(android.content.Context ctx, String currentCode) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = sp.getString(KEY_BYPASS_CODE, null);
        if (stored == null || !stored.equals(currentCode)) {
            sp.edit().remove(KEY_BYPASS_OK).remove(KEY_BYPASS_CODE).apply();
        }
    }
}
