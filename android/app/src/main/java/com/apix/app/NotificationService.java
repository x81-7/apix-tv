package com.apix.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Direct realtime notification service. Backed by Supabase Realtime over
 * a long-lived websocket (see {@link RealtimeNotificationManager}). This
 * class owns the notification channel + a tiny REST helper to fetch the
 * latest unseen notification on cold start (one shot, NOT a periodic worker).
 */
public class NotificationService {

    private static final String TAG = "NotificationService";
    private static final String CHANNEL_ID = "app_notifications";
    private static final String PREFS = "notification_prefs";
    private static final String KEY_LAST_NOTIF_ID = "last_notif_id";

    private static final String SUPABASE_URL = BuildConfig.CLOUD_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.CLOUD_ANON_KEY;

    /** Ensure notification channel + connect realtime listener. */
    public static void init(Context ctx) {
        createNotificationChannel(ctx);
        // Fetch any single unseen notification once (cold-start catchup)
        fetchLatestOnce(ctx);
        // Then start realtime listener
        RealtimeNotificationManager.start(ctx);
    }

    /** Backward-compat shim — old SplashActivity used to call this. */
    public static void schedulePolling(Context ctx) {
        init(ctx);
    }

    public static void createNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "إشعارات التطبيق", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("إشعارات القنوات والتحديثات");
            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /** Max age (ms) for a notification to still be worth showing. */
    private static final long MAX_AGE_MS = 10L * 60L * 1000L; // 10 minutes

    /** Show a notification right now. Called by both cold-start fetch and realtime stream. */
    public static void show(Context ctx, String id, String title, String body, JSONObject action) {
        show(ctx, id, title, body, action, null);
    }

    /** createdAtIso may be null — when null we assume "fresh" (realtime path). */
    public static void show(Context ctx, String id, String title, String body, JSONObject action, String createdAtIso) {
        if (id == null || id.isEmpty()) return;

        if (createdAtIso != null && !createdAtIso.isEmpty()) {
            try {
                long created = java.time.OffsetDateTime.parse(createdAtIso).toInstant().toEpochMilli();
                if (System.currentTimeMillis() - created > MAX_AGE_MS) {
                    Log.d(TAG, "Dropping stale notification " + id);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // Skip app_update notifications when the device is already on or above the target version.
        if (action != null && "app_update".equals(action.optString("type", ""))) {
            try {
                String minVer = action.optString("minVersionName", "");
                int reqCode = action.optInt("requiredVersionCode", 0);
                String cur = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME;
                int curCode = BuildConfig.VERSION_CODE;
                boolean codeOk = reqCode <= 0 || curCode >= reqCode;
                boolean nameOk = minVer.isEmpty() || compareVerNames(cur, minVer) >= 0;
                if (codeOk && nameOk) {
                    Log.d(TAG, "Dropping app_update notif: device already on " + cur + " >= " + minVer);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lastId = sp.getString(KEY_LAST_NOTIF_ID, "");
        if (id.equals(lastId)) return;
        sp.edit().putString(KEY_LAST_NOTIF_ID, id).apply();

        Intent intent = new Intent(ctx, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (action != null) intent.putExtra("notification_action", action.toString());
        PendingIntent pi = PendingIntent.getActivity(
                ctx, id.hashCode(), intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : "APiX TV")
                .setContentText(body != null ? body : "")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void fetchLatestOnce(Context ctx) {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/app_notifications"
                        + "?select=id,title,body,action,created_at"
                        + "&order=created_at.desc&limit=1";
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) return;
                JSONObject n = arr.getJSONObject(0);
                show(ctx, n.optString("id"), n.optString("title"), n.optString("body"),
                        n.optJSONObject("action"), n.optString("created_at", null));
            } catch (Exception e) {
                Log.w(TAG, "fetchLatestOnce error", e);
            }
        }).start();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /** Compare semantic version strings: returns >=0 when current is same/newer. */
    private static int compareVerNames(String a, String b) {
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
}
