package com.apix.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.apix.app.security.DeviceIntegrity;

/**
 * VIP Activation screen.
 *
 * Shows the user's Device ID + a copy button + a "Contact Seller" button
 * that opens the Telegram URL configured in the panel.
 */
public class ActivationActivity extends AppCompatActivity {

    public static final String EXTRA_SELLER_URL = "sellerUrl";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sellerUrl = getIntent().getStringExtra(EXTRA_SELLER_URL);
        String deviceId = DeviceIntegrity.deviceId(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("خطوات الاشتراك");
        title.setTextColor(0xFFFACC15);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp());

        TextView idLabel = new TextView(this);
        idLabel.setText("رمز التعريف ID");
        idLabel.setTextColor(0xFFFFFFFF);
        idLabel.setTextSize(16);
        idLabel.setGravity(Gravity.CENTER);
        idLabel.setPadding(0, 32, 0, 8);
        root.addView(idLabel, lp());

        TextView idText = new TextView(this);
        idText.setText(deviceId);
        idText.setTextColor(0xFFFFFFFF);
        idText.setTextSize(18);
        idText.setGravity(Gravity.CENTER);
        idText.setBackgroundColor(0xFF1F2937);
        idText.setPadding(24, 24, 24, 24);
        root.addView(idText, lp());

        Button btnCopy = new Button(this);
        btnCopy.setText("نسخ رمز التعريف");
        btnCopy.setAllCaps(false);
        btnCopy.setTextColor(0xFF000000);
        btnCopy.setBackgroundColor(0xFFFFFFFF);
        btnCopy.setFocusable(true);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("device_id", deviceId));
                Toast.makeText(this, "تم نسخ رمز التعريف", Toast.LENGTH_SHORT).show();
            }
        });
        btnCopy.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0xFFE5E7EB : 0xFFFFFFFF));
        LinearLayout.LayoutParams copyLp = lp();
        copyLp.topMargin = 16;
        root.addView(btnCopy, copyLp);

        if (sellerUrl != null && !sellerUrl.isEmpty()) {
            Button btnSeller = new Button(this);
            btnSeller.setText("تواصل مع البائع");
            btnSeller.setAllCaps(false);
            btnSeller.setTextColor(0xFFFFFFFF);
            btnSeller.setBackgroundColor(0xFF2563EB); // blue (NOT gold)
            btnSeller.setFocusable(true);
            btnSeller.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(sellerUrl));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
                }
            });
            btnSeller.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0xFF1D4ED8 : 0xFF2563EB));
            LinearLayout.LayoutParams sLp = lp();
            sLp.topMargin = 16;
            root.addView(btnSeller, sLp);
        }

        Button btnBack = new Button(this);
        btnBack.setText("رجوع");
        btnBack.setAllCaps(false);
        btnBack.setBackgroundColor(0xFF374151);
        btnBack.setTextColor(0xFFFFFFFF);
        btnBack.setFocusable(true);
        btnBack.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bLp = lp();
        bLp.topMargin = 32;
        root.addView(btnBack, bLp);

        setContentView(root);
        btnCopy.requestFocus();
    }

    private static LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
