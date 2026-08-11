package com.apix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Realtime listener for new rows in `app_notifications`.
 *
 * Connects to Supabase Realtime over WebSocket (Phoenix protocol) and:
 *  1. Shows a system notification immediately when an INSERT event arrives
 *     on `app_notifications`.
 *  2. Patches the cached JSON for `sub_channels` and `side_menus` whenever
 *     UPDATE events arrive — so that `pin_code` and `ios_stream` changes
 *     made from the dashboard reach all running clients within ~1s without
 *     requiring a manual refresh.
 *
 * No periodic polling — the connection stays open while the app is alive
 * and reconnects with exponential backoff if dropped.
 */
public class RealtimeNotificationManager {

    private static final String TAG = "RealtimeNotif";
    private static final String SUPABASE_ANON_KEY = Net.anon();

    private static OkHttpClient client;
    private static WebSocket socket;
    private static Context appContext;
    private static int retryDelay = 2000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean started = false;
    private static volatile boolean connected = false;
    private static volatile boolean reconnectScheduled = false;
    private static volatile String myDeviceId = null;

    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        appContext = ctx.getApplicationContext();
        try {
            myDeviceId = com.apix.app.security.DeviceIntegrity.deviceId(appContext);
        } catch (Throwable ignored) {}
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
                .pingInterval(25, TimeUnit.SECONDS)
                .build();
        connect();
    }

    /** Idempotent lifecycle/network recovery entry point. */
    public static synchronized void ensureConnected(Context ctx) {
        if (!started) {
            start(ctx);
            return;
        }
        if (!connected) connect();
    }

    private static void connect() {
        if (client == null || connected) return;
        reconnectScheduled = false;
        // Routed through the Cloudflare Worker gateway (wss) when WORKER_URL is
        // set; the Worker proxies the realtime websocket to Supabase.
        String url = Net.realtimeWsUrl();
        Request req = new Request.Builder().url(url).build();
        socket = client.newWebSocket(req, new Listener());
    }

    private static void scheduleReconnect() {
        if (reconnectScheduled) return;
        reconnectScheduled = true;
        MAIN.postDelayed(() -> {
            reconnectScheduled = false;
            try { if (!connected) connect(); } catch (Exception ignored) {}
        }, retryDelay);
        retryDelay = Math.min(retryDelay * 2, 60000);
    }

    private static class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            connected = true;
            retryDelay = 2000;
            try {
                // Subscribe to notifications (INSERT only).
                ws.send(buildJoin(
                        "realtime:public:app_notifications",
                        "1",
                        "INSERT",
                        "app_notifications"
                ).toString());

                // Subscribe to sub_channels updates (pinCode + ios_stream live sync).
                ws.send(buildJoin(
                        "realtime:public:sub_channels",
                        "2",
                        "*",
                        "sub_channels"
                ).toString());

                // Subscribe to side_menus updates (menu-level pin live sync).
                ws.send(buildJoin(
                        "realtime:public:side_menus",
                        "3",
                        "*",
                        "side_menus"
                ).toString());

                // Subscribe to channels updates (live URL/header sync — no app restart).
                ws.send(buildJoin(
                        "realtime:public:channels",
                        "4",
                        "*",
                        "channels"
                ).toString());

                // Subscribe to ban_signals INSERT — instant, real-time BAN.
                // ban_signals holds ONLY the opaque device hash (no IP / reason),
                // so it is safe to read with the anon role. When a signal for
                // THIS device arrives we wipe local content and close silently
                // without waiting for the next launch/handshake.
                ws.send(buildJoin(
                        "realtime:public:ban_signals",
                        "5",
                        "INSERT",
                        "ban_signals"
                ).toString());



                // Heartbeat every 25s
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject hb = new JSONObject()
                                    .put("topic", "phoenix")
                                    .put("event", "heartbeat")
                                    .put("payload", new JSONObject())
                                    .put("ref", String.valueOf(System.currentTimeMillis()));
                            if (!ws.send(hb.toString())) {
                                connected = false;
                                ws.cancel();
                                scheduleReconnect();
                                return;
                            }
                            MAIN.postDelayed(this, 25000);
                        } catch (Exception ignored) {}
                    }
                }, 25000);
            } catch (Exception e) {
                Log.w(TAG, "join error", e);
            }
        }

        private JSONObject buildJoin(String topic, String ref, String event, String table) throws Exception {
            return new JSONObject()
                    .put("topic", topic)
                    .put("event", "phx_join")
                    .put("payload", new JSONObject()
                            .put("config", new JSONObject()
                                    .put("postgres_changes", new JSONArray()
                                            .put(new JSONObject()
                                                    .put("event", event)
                                                    .put("schema", "public")
                                                    .put("table", table)))))
                    .put("ref", ref);
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            try {
                JSONObject msg = new JSONObject(text);
                String event = msg.optString("event", "");
                if (!"postgres_changes".equals(event)) return;
                JSONObject payload = msg.optJSONObject("payload");
                if (payload == null) return;
                JSONObject data = payload.optJSONObject("data");
                if (data == null) return;
                String table = data.optString("table", "");
                JSONObject record = data.optJSONObject("record");
                if (record == null) return;

                if ("app_notifications".equals(table)) {
                    String id = record.optString("id", "");
                    String title = record.optString("title", "");
                    String body = record.optString("body", "");
                    String createdAt = record.optString("created_at", null);
                    JSONObject action = record.optJSONObject("action");
                    if (appContext != null) {
                        NotificationService.show(appContext, id, title, body, action, createdAt);
                    }
                } else if ("sub_channels".equals(table) || "side_menus".equals(table) || "channels".equals(table)) {
                    String type = data.optString("type", "");
                    if (appContext != null) {
                        patchCache(table, record, type);
                    }
                } else if ("ban_signals".equals(table)) {
                    handleRealtimeBan(record);
                }
            } catch (Exception e) {
                Log.w(TAG, "msg parse error", e);
            }
        }

        /**
         * Real-time BAN enforcement. Fired the instant the admin bans a device
         * from the panel (a trigger writes the device hash into ban_signals).
         * If the signal matches THIS device we wipe all cached channels and
         * close the app silently — no restart needed.
         */
        private void handleRealtimeBan(JSONObject record) {
            try {
                if (appContext == null || myDeviceId == null) return;
                String bannedId = record.optString("device_id", "");
                if (bannedId.isEmpty() || !bannedId.equals(myDeviceId)) return;


                MAIN.post(() -> {
                    try {
                        com.apix.app.security.HandshakeClient.Verdict v =
                                new com.apix.app.security.HandshakeClient.Verdict();
                        v.status = "BANNED";
                        v.wipe = true;
                        v.reason = "REALTIME_BAN";
                        com.apix.app.security.Enforcement.cacheVerdict(appContext, v);
                        com.apix.app.security.Enforcement.wipeChannelCache(appContext);
                        com.apix.app.security.Enforcement.silentExit(appContext);
                    } catch (Throwable t) {
                        Log.w(TAG, "realtime ban enforce error", t);
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "handleRealtimeBan error", t);
            }
        }

        /**
         * Patches the cached JSON array stored by {@link SupabaseDataManager}
         * with a single row's new values. Called for INSERT/UPDATE/DELETE on
         * the realtime-tracked tables.
         */
        private void patchCache(String table, JSONObject record, String type) {
            try {
                SharedPreferences sp = appContext.getSharedPreferences("supabase_cache", Context.MODE_PRIVATE);
                String prefKey;
                if ("sub_channels".equals(table)) prefKey = "sub_channels_json";
                else if ("side_menus".equals(table)) prefKey = "side_menus_json";
                else prefKey = "channels_json";
                String raw = sp.getString(prefKey, "[]");
                JSONArray arr = new JSONArray(raw);
                String rid = record.optString("id", "");
                if (rid.isEmpty()) return;

                int existing = -1;
                for (int i = 0; i < arr.length(); i++) {
                    if (rid.equals(arr.optJSONObject(i).optString("id"))) {
                        existing = i;
                        break;
                    }
                }

                if ("DELETE".equalsIgnoreCase(type)) {
                    if (existing >= 0) arr.remove(existing);
                } else {
                    if (existing >= 0) {
                        arr.put(existing, record);
                    } else {
                        arr.put(record);
                    }
                }
                sp.edit().putString(prefKey, arr.toString()).apply();

                // Notify the rest of the app that the cache changed so any
                // open screen can re-render. This is a simple in-process
                // broadcast — no permissions needed.
                android.content.Intent intent = new android.content.Intent("com.apix.app.CACHE_UPDATED");
                intent.putExtra("table", table);
                intent.putExtra("id", rid);
                appContext.sendBroadcast(intent);
            } catch (Exception e) {
                Log.w(TAG, "patchCache error", e);
            }
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) { /* ignore binary */ }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            connected = false;
            socket = null;
            Log.w(TAG, "closed " + code + " " + reason);
            scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            connected = false;
            socket = null;
            Log.w(TAG, "failure", t);
            scheduleReconnect();
        }
    }
}
