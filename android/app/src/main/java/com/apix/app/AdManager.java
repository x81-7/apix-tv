package com.apix.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AdManager {
    private static final String TAG = "AdManager";
    private static final String PREFS = "ad_prefs";
    private static final String KEY_CUSTOM_ADS = "custom_ads_json";
    private static final String KEY_FORCED_COUNT = "forced_custom_ads_count";
    private static final String KEY_LAST_INDEX = "last_watched_ad_index";
    private static final String KEY_APP_OPEN_DONE = "rewarded_app_open_done";
    private static final String KEY_LOCAL_TRIGGER = "local_ads_trigger";
    private static final String KEY_LOCAL_CHANNEL_LIST = "local_ads_channel_ids";
    private static final String KEY_LOCAL_APP_OPEN_DONE = "local_app_open_done";
    private static final String KEY_LOCAL_FORCE_EXTERNAL = "local_ads_force_external";
    private static final String KEY_NETWORK_FORCE_EXTERNAL = "network_ads_force_external";
    private static final String KEY_APP_OPEN_DONE_AT = "rewarded_app_open_done_at";
    private static final long APP_OPEN_REARM_HOURS = 6L;

    public interface GateCallback { void onAllowed(); }

    private AdManager() {}

    private static boolean isVip(Context ctx) {
        try {
            return new com.apix.app.vip.VipChecker(
                ctx, Net.base(), Net.anon()
            ).isActiveLocally();
        } catch (Throwable t) { return false; }
    }

    public static void refreshCacheAsync(Context context) {
        new Thread(() -> {
            try {
                JSONArray ads = SupabaseDataManager.fetchVisibleCustomAds();
                int forcedCount = SupabaseDataManager.fetchForcedCustomAdsCount();
                JSONObject localCfg = SupabaseDataManager.fetchLocalAdsConfig();
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = sp.edit()
                    .putString(KEY_CUSTOM_ADS, ads.toString())
                    .putInt(KEY_FORCED_COUNT, Math.max(1, forcedCount));
                if (localCfg != null) {
                    ed.putString(KEY_LOCAL_TRIGGER, localCfg.optString("trigger", "app_open"));
                    JSONArray ids = localCfg.optJSONArray("channelIds");
                    ed.putString(KEY_LOCAL_CHANNEL_LIST, ids != null ? ids.toString() : "[]");
                    ed.putBoolean(KEY_LOCAL_FORCE_EXTERNAL, localCfg.optBoolean("forceExternal", false));
                }
                JSONObject adCfg = SupabaseDataManager.fetchAdConfig();
                if (adCfg != null) {
                    ed.putBoolean(KEY_NETWORK_FORCE_EXTERNAL, adCfg.optBoolean("forceExternal", false));
                    ed.putBoolean("ads_enabled", adCfg.optBoolean("adsEnabled", false));
                    ed.putString("ads_gate_mode", adCfg.optString("gateMode", "app_open_each"));
                    ed.putString("ads_unit_id", adCfg.optString("rewardedAdUnitId",
                        adCfg.optString("admobRewardedId", "")));
                    // حفظ WebAd settings
                    ed.putString("web_ad_url",        adCfg.optString("webAdUrl", ""));
                    ed.putInt   ("web_ad_skip_after",  adCfg.optInt("webAdSkipAfter", 8));
                    ed.putString("web_ad_seller_url",  adCfg.optString("sellerUrl", ""));
                    String unit = adCfg.optString("rewardedAdUnitId",
                        adCfg.optString("admobRewardedId", ""));
                    if (!unit.isEmpty()) {
                        try { RewardedAdHelper.preload(context, unit); } catch (Throwable ignored) {}
                    }
                }
                ed.apply();
            } catch (Throwable t) {
                Log.w(TAG, "refreshCacheAsync failed", t);
            }
        }).start();
    }

    // ── App Open Gate ─────────────────────────────────────────────────
    public static void maybeRunAppOpenGate(Activity activity, GateCallback callback) {
        if (isVip(activity)) { callback.onAllowed(); return; }

        new Thread(() -> {
            // جلب config في الخلفية — لا NetworkOnMainThreadException
            JSONObject freshConfig = null;
            try { freshConfig = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
            final JSONObject fConfig = freshConfig;

            activity.runOnUiThread(() -> {
                SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

                // حفظ config الجديد إذا وُجد
                if (fConfig != null) {
                    sp.edit()
                      .putBoolean("ads_enabled", fConfig.optBoolean("adsEnabled", false))
                      .putString("ads_gate_mode", fConfig.optString("gateMode", "app_open_each"))
                      .putString("ads_unit_id", fConfig.optString("rewardedAdUnitId",
                          fConfig.optString("admobRewardedId", "")))
                      .apply();
                }

                // قراءة القيم (من الكاش أو الجديد)
                boolean adsOn = sp.getBoolean("ads_enabled", false);
                String gateMode = sp.getString("ads_gate_mode", "app_open_each");
                String unitId = sp.getString("ads_unit_id", "");

                // إعادة ضبط Rewarded بعد 6 ساعات
                long doneAt = sp.getLong(KEY_APP_OPEN_DONE_AT, 0L);
                if (doneAt > 0L && System.currentTimeMillis() - doneAt > APP_OPEN_REARM_HOURS * 3600_000L) {
                    sp.edit().putBoolean(KEY_APP_OPEN_DONE, false).remove(KEY_APP_OPEN_DONE_AT).apply();
                }

                // إعادة ضبط إعلانات الويب بعد 6 ساعات
                long localDoneAt = sp.getLong("local_app_open_done_at", 0L);
                if (localDoneAt > 0L && System.currentTimeMillis() - localDoneAt > APP_OPEN_REARM_HOURS * 3600_000L) {
                    sp.edit().putBoolean(KEY_LOCAL_APP_OPEN_DONE, false)
                              .remove("local_app_open_done_at").apply();
                }

                Log.d(TAG, "OPEN_GATE: adsOn=" + adsOn + " gateMode=" + gateMode
                    + " unitId=" + (unitId.isEmpty() ? "EMPTY" : "SET")
                    + " appOpenDone=" + sp.getBoolean(KEY_APP_OPEN_DONE, false));

                final String webAdUrl    = getWebAdUrl(fConfig, sp);
                final int    webAdSkip   = getWebAdSkipAfter(fConfig, sp);
                final String sellerUrl   = getSellerUrl(fConfig, sp);

                Runnable afterAll = () -> {
                    // WebAd آخر شيء قبل دخول التطبيق
                    if (!webAdUrl.isEmpty()) {
                        showWebAd(activity, webAdUrl, webAdSkip, sellerUrl, callback::onAllowed);
                    } else {
                        callback.onAllowed();
                    }
                };

                Runnable afterRewarded = () -> {
                    String trigger = sp.getString(KEY_LOCAL_TRIGGER, "app_open");
                    boolean localOnAppOpen = "app_open".equalsIgnoreCase(trigger)
                        || "both".equalsIgnoreCase(trigger);
                    if (localOnAppOpen && !sp.getBoolean(KEY_LOCAL_APP_OPEN_DONE, false)) {
                        showSequentialAds(activity, () -> {
                            sp.edit()
                              .putBoolean(KEY_LOCAL_APP_OPEN_DONE, true)
                              .putLong("local_app_open_done_at", System.currentTimeMillis())
                              .apply();
                            afterAll.run();
                        });
                    } else {
                        afterAll.run();
                    }
                };

                if (adsOn && "app_open_once".equalsIgnoreCase(gateMode)
                        && !sp.getBoolean(KEY_APP_OPEN_DONE, false)
                        && !unitId.isEmpty()) {
                    RewardedAdHelper.showOrSkip(activity, unitId, rewarded -> {
                        sp.edit()
                          .putBoolean(KEY_APP_OPEN_DONE, true)
                          .putLong(KEY_APP_OPEN_DONE_AT, System.currentTimeMillis())
                          .apply();
                        afterRewarded.run();
                    });
                } else if (adsOn && "app_open_each".equalsIgnoreCase(gateMode) && !unitId.isEmpty()) {
                    RewardedAdHelper.showOrSkip(activity, unitId, rewarded -> afterRewarded.run());
                } else {
                    afterRewarded.run();
                }
            });
        }).start();
    }

    // ── Unlock Gate (عند فتح قناة) ────────────────────────────────────
    public static void maybeRunUnlockGate(Activity activity, String channelId, GateCallback callback) {
        if (isVip(activity)) { callback.onAllowed(); return; }

        new Thread(() -> {
            JSONObject freshConfig = null;
            try { freshConfig = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
            final JSONObject fConfig = freshConfig;

            activity.runOnUiThread(() -> {
                SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                boolean rewardedFires = isRewardedGateEnabled(fConfig, "unlock_channel")
                    && isLockedChannel(fConfig, channelId);
                String trigger = sp.getString(KEY_LOCAL_TRIGGER, "app_open");
                boolean localOnChannel = "on_channel".equalsIgnoreCase(trigger)
                    || "both".equalsIgnoreCase(trigger);
                boolean localFires = localOnChannel && isLocalAdAllowedForChannel(sp, channelId);

                if (!rewardedFires && !localFires) { callback.onAllowed(); return; }

                final String webAdUrl  = getWebAdUrl(fConfig, sp);
                final int    webAdSkip = getWebAdSkipAfter(fConfig, sp);
                final String sellerUrl = getSellerUrl(fConfig, sp);

                Runnable afterAll = () -> {
                    if (!webAdUrl.isEmpty()) {
                        showWebAd(activity, webAdUrl, webAdSkip, sellerUrl, callback::onAllowed);
                    } else {
                        callback.onAllowed();
                    }
                };

                Runnable afterRewarded = () -> {
                    if (localFires) showSequentialAds(activity, afterAll::run);
                    else afterAll.run();
                };
                if (rewardedFires) {
                    String unitId = fConfig != null
                        ? fConfig.optString("rewardedAdUnitId", fConfig.optString("admobRewardedId", ""))
                        : sp.getString("ads_unit_id", "");
                    RewardedAdHelper.showOrSkip(activity, unitId, r -> afterRewarded.run());
                } else {
                    afterRewarded.run();
                }
            });
        }).start();
    }

    // ── External Gate (روابط خارجية) ──────────────────────────────────
    public static void maybeRunExternalGate(Activity activity, GateCallback callback) {
        if (isVip(activity)) { callback.onAllowed(); return; }

        new Thread(() -> {
            JSONObject freshConfig = null;
            try { freshConfig = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
            final JSONObject fConfig = freshConfig;

            activity.runOnUiThread(() -> {
                SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                boolean networkOn = sp.getBoolean(KEY_NETWORK_FORCE_EXTERNAL, false)
                    && fConfig != null && fConfig.optBoolean("adsEnabled", false);
                boolean localOn = sp.getBoolean(KEY_LOCAL_FORCE_EXTERNAL, false);

                if (!networkOn && !localOn) { callback.onAllowed(); return; }

                int counter = sp.getInt("ext_ad_counter", 0);
                sp.edit().putInt("ext_ad_counter", counter + 1).apply();
                boolean useNetwork = networkOn && (!localOn || counter % 2 == 0);

                if (useNetwork) {
                    String unitId = fConfig != null
                        ? fConfig.optString("rewardedAdUnitId", fConfig.optString("admobRewardedId", ""))
                        : sp.getString("ads_unit_id", "");
                    RewardedAdHelper.showOrSkip(activity, unitId, r -> {
                        if (!r && localOn) showSequentialAds(activity, callback::onAllowed);
                        else callback.onAllowed();
                    });
                } else {
                    showSequentialAds(activity, callback::onAllowed);
                }
            });
        }).start();
    }

    private static boolean isLocalAdAllowedForChannel(SharedPreferences sp, String channelId) {
        try {
            String raw = sp.getString(KEY_LOCAL_CHANNEL_LIST, "[]");
            JSONArray arr = new JSONArray(raw);
            if (arr.length() == 0) return true;
            if (channelId == null) return false;
            for (int i = 0; i < arr.length(); i++) {
                if (channelId.equals(arr.optString(i))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isRewardedGateEnabled(JSONObject config, String mode) {
        if (config == null) return false;
        return config.optBoolean("adsEnabled", false)
            && mode.equalsIgnoreCase(config.optString("gateMode", "app_open_once"));
    }
// ── استخراج إعدادات WebAd من config ─────────────────────────────
    private static String getWebAdUrl(JSONObject config, SharedPreferences sp) {
        if (config != null) {
            String u = config.optString("webAdUrl", "");
            if (!u.isEmpty()) return u;
        }
        return sp.getString("web_ad_url", "");
    }

    private static int getWebAdSkipAfter(JSONObject config, SharedPreferences sp) {
        if (config != null && config.has("webAdSkipAfter"))
            return Math.max(3, config.optInt("webAdSkipAfter", 8));
        return Math.max(3, sp.getInt("web_ad_skip_after", 8));
    }

    private static String getSellerUrl(JSONObject config, SharedPreferences sp) {
        if (config != null) {
            String u = config.optString("sellerUrl", "");
            if (!u.isEmpty()) return u;
        }
        return sp.getString("web_ad_seller_url", "");
    }
    
    private static boolean isLockedChannel(JSONObject config, String channelId) {
        if (config == null || channelId == null) return false;
        JSONArray ids = config.optJSONArray("lockedChannelIds");
        if (ids == null) return false;
        for (int i = 0; i < ids.length(); i++) {
            if (channelId.equals(ids.optString(i))) return true;
        }
        return false;
    }

    public static void showSequentialAds(Activity activity, GateCallback callback) {
        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_CUSTOM_ADS, "[]");
        int forcedCount = Math.max(1, sp.getInt(KEY_FORCED_COUNT, 1));
        int lastIndex = Math.max(0, sp.getInt(KEY_LAST_INDEX, 0));
        try {
            JSONArray array = new JSONArray(raw);
            if (array.length() == 0) { callback.onAllowed(); return; }
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < forcedCount; i++) {
                int idx = (lastIndex + i) % array.length();
                urls.add(array.getJSONObject(idx).optString("video_url", ""));
            }
            sp.edit().putInt(KEY_LAST_INDEX, (lastIndex + urls.size()) % array.length()).apply();
            Intent intent = new Intent(activity, WebViewActivity.class);
            intent.putExtra("sequential_ad_urls", new JSONArray(urls).toString());
            intent.putExtra("url", "about:blank");
            intent.putExtra("title", "الإعلانات");
            SequentialAdBridge.registerCallback(callback);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "showSequentialAds failed", e);
            callback.onAllowed();
        }
    }

    public static void handleUpdateInstall(Activity activity, String url, boolean internalInstall) {
        if (internalInstall) { UpdateInstaller.downloadAndInstall(activity, url); }
        else {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ignored) {}
        }
    }

    // ── Bridge للـ SequentialAds (فيديو m3u8) ────────────────────────
    public static final class SequentialAdBridge {
        private static GateCallback pending;
        private SequentialAdBridge() {}
        public static void registerCallback(GateCallback cb) { pending = cb; }
        public static void complete() {
            if (pending != null) { GateCallback cb = pending; pending = null; cb.onAllowed(); }
        }
    }

    // ── Bridge للـ WebAd (صفحة ويب مع عداد) ─────────────────────────
    public static final class WebAdBridge {
        private static GateCallback pending;
        private WebAdBridge() {}
        public static void register(GateCallback cb) { pending = cb; }
        public static void complete() {
            if (pending != null) { GateCallback cb = pending; pending = null; cb.onAllowed(); }
        }
    }

    // ── عرض إعلان الويب + callback عند التخطي ─────────────────────────
    private static void showWebAd(Activity activity, String url, int skipAfter,
                                   String sellerUrl, GateCallback callback) {
        if (url == null || url.isEmpty()) { callback.onAllowed(); return; }
        WebAdBridge.register(callback);
        WebAdActivity.launchIfEnabled(activity, url, skipAfter, sellerUrl);
    }
}