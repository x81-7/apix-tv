package com.apix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads an APK from a URL and triggers internal install.
 * Falls back to ACTION_VIEW (browser) if anything fails.
 */
public final class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";

    private UpdateInstaller() {}

    public interface Callback {
        void onProgress(int percent);
        void onReady(File apk);
        void onError(String message);
    }

    /** Entry point. Runs on a background thread. */
    public static void downloadAndInstall(final Activity activity, final String url) {
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle("تنزيل التحديث");
        dialog.setMessage("يرجى الانتظار...");
        dialog.setIndeterminate(false);
        dialog.setCancelable(false);
        dialog.setMax(100);
        dialog.show();

        new Thread(() -> {
            File apk = null;
            try {
                apk = downloadApk(activity, url, (pct) -> {
                    new Handler(Looper.getMainLooper()).post(() -> dialog.setProgress(pct));
                });
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
            }
            final File finalApk = apk;
            new Handler(Looper.getMainLooper()).post(() -> {
                try { dialog.dismiss(); } catch (Exception ignored) {}
                if (finalApk != null && finalApk.exists()) {
                    installApk(activity, finalApk);
                } else {
                    fallbackToBrowser(activity, url);
                }
            });
        }).start();
    }

    public static void download(final Activity activity, final String url, final Callback callback) {
        new Thread(() -> {
            try {
                File apk = downloadApk(activity, url, (pct) -> {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(pct));
                });
                new Handler(Looper.getMainLooper()).post(() -> callback.onReady(apk));
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "download_failed"));
            }
        }).start();
    }

    private interface ProgressCb { void onProgress(int pct); }

    private static File downloadApk(Context ctx, String urlStr, ProgressCb cb) throws Exception {
        File dir = new File(ctx.getExternalFilesDir(null), "updates");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "update.apk");
        if (out.exists()) out.delete();

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.connect();

        int total = conn.getContentLength();
        try (InputStream in = conn.getInputStream();
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int read;
            long downloaded = 0;
            int lastPct = 0;
            while ((read = in.read(buf)) != -1) {
                os.write(buf, 0, read);
                downloaded += read;
                if (total > 0) {
                    int pct = (int) ((downloaded * 100L) / total);
                    if (pct != lastPct) { lastPct = pct; cb.onProgress(pct); }
                }
            }
            os.flush();
        }
        return out;
    }

    public static void installApk(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".updateprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            // On Android O+ also need install-unknown-apps permission. The system will prompt.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                AlertDialog.Builder b = new AlertDialog.Builder(activity);
                b.setTitle("إذن التثبيت")
                        .setMessage("يرجى السماح بتثبيت التطبيقات من هذا المصدر، ثم العودة لإكمال التحديث.")
                        .setPositiveButton("فتح الإعدادات", (d, w) -> {
                            try {
                                Intent settings = new Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(settings);
                            } catch (Exception ignored) {}
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
                return;
            }
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "installApk failed", e);
            fallbackToBrowser(activity, apk.toString());
        }
    }

    private static void fallbackToBrowser(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }
}
