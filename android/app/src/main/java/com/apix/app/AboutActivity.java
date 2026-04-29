package com.apix.app;

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

/**
 * About / Privacy policy screen — Arabic + English.
 * Generic media player wording (no mention of channels) for Play Store compliance.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView body = findViewById(R.id.about_body);
        body.setText(Html.fromHtml(buildHtml(), Html.FROM_HTML_MODE_LEGACY));

        MaterialButton back = findViewById(R.id.about_back);
        back.setOnClickListener(v -> finish());
        back.setFocusable(true);
        back.requestFocus();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private String buildHtml() {
        return ""
            + "<h2>حول التطبيق</h2>"
            + "<p>هذا التطبيق هو <b>مشغّل وسائط (Media Player)</b> يدعم تشغيل أنواع متعدّدة من روابط الفيديو "
            + "بما فيها HLS و DASH و MP4 و RTSP وغيرها، مع دعم تمرير ترويسات مخصّصة "
            + "(<i>User-Agent</i>, <i>Referer</i>, <i>Custom Headers</i>) ومفاتيح <i>ClearKey</i> للروابط المشفّرة.</p>"
            + "<p>يدعم التطبيق أجهزة التلفاز الذكية (Android TV) عبر الريموت، وكذلك الهواتف واللوحيات.</p>"

            + "<h3>سياسة الخصوصية</h3>"
            + "<ul>"
            + "<li><font color='#FF5252'><b>لا نجمع أي معلومات شخصية عن المستخدم.</b></font></li>"
            + "<li><font color='#FF5252'><b>لا نخزّن أي روابط يقوم المستخدم بإدخالها يدوياً.</b></font></li>"
            + "<li>لا نشارك أي بيانات مع أطراف ثالثة.</li>"
            + "<li>قد يُستخدم اتصال إنترنت بسيط لجلب التحديثات والإشعارات الفنية فقط.</li>"
            + "</ul>"

            + "<h3>إخلاء المسؤولية</h3>"
            + "<p><font color='#FF5252'><b>نحن غير مسؤولين بأي شكل من الأشكال عن أي استخدام غير شرعي للتطبيق "
            + "أو عن المحتوى الذي يقوم المستخدم بتشغيله عبره. التطبيق هو مجرد أداة عرض، "
            + "والمستخدم وحده يتحمّل كامل المسؤولية القانونية عمّا يختاره من روابط.</b></font></p>"

            + "<hr/>"
            + "<h2>About</h2>"
            + "<p>This application is a <b>media player</b> that supports playing many types of video links "
            + "including HLS, DASH, MP4, RTSP and more — with the ability to pass custom headers "
            + "(<i>User-Agent</i>, <i>Referer</i>, custom headers) and <i>ClearKey</i> keys for protected streams.</p>"
            + "<p>The app supports Android TV (remote control), phones and tablets.</p>"

            + "<h3>Privacy Policy</h3>"
            + "<ul>"
            + "<li><font color='#FF5252'><b>We do NOT collect any personal information.</b></font></li>"
            + "<li><font color='#FF5252'><b>We do NOT store any URLs entered manually by the user.</b></font></li>"
            + "<li>We do not share any data with third parties.</li>"
            + "<li>A small internet connection may be used only for technical updates and notifications.</li>"
            + "</ul>"

            + "<h3>Disclaimer</h3>"
            + "<p><font color='#FF5252'><b>We are not responsible in any way for any illegal use of this app "
            + "or for the content played through it. This app is only a playback tool, and the user "
            + "alone bears full legal responsibility for the links they choose.</b></font></p>";
    }
}
