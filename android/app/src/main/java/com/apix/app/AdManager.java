package com.apix.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class AdManager {
    private static final String TAG = "AdManager";
    private static final String PREFS = "ad_prefs";
    private static final String KEY_CUSTOM_ADS = "custom_ads_json";
    private static final String KEY_FORCED_COUNT = "forced_custom_ads_count";
    private static final String KEY_LAST_INDEX = "last_watched_ad_index";
    private static final String KEY_APP_OPEN_DONE = "rewarded_app_open_done";
    private static final String KEY_LOCAL_TRIGGER = "local_ads_trigger"; // off | app_open | on_channel | both
    private static final String KEY_LOCAL_CHANNEL_LIST = "local_ads_channel_ids"; // optional whitelist
    private static final String KEY_LOCAL_APP_OPEN_DONE = "local_app_open_done";
    private static final String KEY_LOCAL_FORCE_EXTERNAL = "local_ads_force_external";
    private static final String KEY_NETWORK_FORCE_EXTERNAL = "network_ads_force_external";
    private static final String KEY_APP_OPEN_DONE_AT = "rewarded_app_open_done_at";
    /** Re-arm the "once per app open" gate after this many hours so users see
     *  ads regularly even when the dashboard left gateMode at app_open_once. */
    private static final long APP_OPEN_REARM_HOURS = 6L;

    public interface GateCallback {
        void onAllowed();
    }

    private AdManager() {}

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
                    String unit = adCfg.optString("rewardedAdUnitId",
                                  adCfg.optString("admobRewardedId", ""));
                    if (!unit.isEmpty()) {
                        // Pre-warm a rewarded ad in background
                        try { RewardedAdHelper.preload(context, unit); } catch (Throwable ignored) {}
                    }
                }
                ed.apply();
            } catch (Throwable t) {
                Log.w(TAG, "refreshCacheAsync failed", t);
            }
        }).start();
    }

    public static void maybeRunAppOpenGate(Activity activity, GateCallback callback) {
        // Use the freshly-cached config from refreshCacheAsync (also fetches fresh)
        JSONObject config = null;
        try { config = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
        final JSONObject fConfig = config;

        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Runnable afterRewarded = () -> {
            // 2) Run LOCAL sequential ads if trigger includes app_open
            String trigger = sp.getString(KEY_LOCAL_TRIGGER, "app_open");
            boolean localOnAppOpen = "app_open".equalsIgnoreCase(trigger) || "both".equalsIgnoreCase(trigger);
            if (localOnAppOpen && !sp.getBoolean(KEY_LOCAL_APP_OPEN_DONE, false)) {
                showSequentialAds(activity, () -> {
                    sp.edit().putBoolean(KEY_LOCAL_APP_OPEN_DONE, true).apply();
                    callback.onAllowed();
                });
            } else {
                callback.onAllowed();
            }
        };

        boolean adsOn = fConfig != null && fConfig.optBoolean("adsEnabled", false);
        // Default changed to app_open_each so ads show on every launch when
        // the dashboard didn't explicitly pick a mode. Previous default
        // (app_open_once) caused users to never see ads after the first show.
        String gateMode = fConfig != null ? fConfig.optString("gateMode", "app_open_each") : "";
        String unitId = fConfig != null
            ? fConfig.optString("rewardedAdUnitId", fConfig.optString("admobRewardedId", ""))
            : "";

        // Re-arm the once-per-launch flag after APP_OPEN_REARM_HOURS so the
        // ad re-shows even on a long-running install.
        long doneAt = sp.getLong(KEY_APP_OPEN_DONE_AT, 0L);
        if (doneAt > 0L && System.currentTimeMillis() - doneAt > APP_OPEN_REARM_HOURS * 3600_000L) {
            sp.edit().putBoolean(KEY_APP_OPEN_DONE, false).remove(KEY_APP_OPEN_DONE_AT).apply();
        }

        // Diagnostics — exposes WHY an ad gate did or didn't fire.
        // Filter logs with: adb logcat -s AdManager:* RewardedAdHelper:*
        Log.d(TAG, "OPEN_GATE: adsOn=" + adsOn
                + " gateMode=" + gateMode
                + " unitIdSet=" + (unitId != null && !unitId.isEmpty())
                + " appOpenDone=" + sp.getBoolean(KEY_APP_OPEN_DONE, false)
                + " configPresent=" + (fConfig != null));
        if (fConfig == null) {
            Log.w(TAG, "OPEN_GATE: adConfig is NULL — system_settings.adConfig row missing or unreachable");
        } else if (!adsOn) {
            Log.w(TAG, "OPEN_GATE: adsEnabled=false in dashboard. Toggle it on to show ads.");
        } else if (unitId == null || unitId.isEmpty()) {
            Log.w(TAG, "OPEN_GATE: rewardedAdUnitId / admobRewardedId is EMPTY in dashboard.");
        }

        if (adsOn && "app_open_once".equalsIgnoreCase(gateMode)
                && !sp.getBoolean(KEY_APP_OPEN_DONE, false)
                && unitId != null && !unitId.isEmpty()) {
            // Show on EVERY app open if dashboard set "app_open_each" — but default is once.
            RewardedAdHelper.showOrSkip(activity, unitId, rewarded -> {
                sp.edit()
                  .putBoolean(KEY_APP_OPEN_DONE, true)
                  .putLong(KEY_APP_OPEN_DONE_AT, System.currentTimeMillis())
                  .apply();
                afterRewarded.run();
            });
        } else if (adsOn && "app_open_each".equalsIgnoreCase(gateMode)
                && unitId != null && !unitId.isEmpty()) {
            RewardedAdHelper.showOrSkip(activity, unitId, rewarded -> afterRewarded.run());
        } else {
            afterRewarded.run();
        }
    }

    public static void maybeRunUnlockGate(Activity activity, String channelId, GateCallback callback) {
        JSONObject config = null;
        try { config = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
        final JSONObject fConfig = config;

        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean rewardedFires = isRewardedGateEnabled(fConfig, "unlock_channel") && isLockedChannel(fConfig, channelId);

        String trigger = sp.getString(KEY_LOCAL_TRIGGER, "app_open");
        boolean localOnChannel = "on_channel".equalsIgnoreCase(trigger) || "both".equalsIgnoreCase(trigger);
        boolean localFires = localOnChannel && isLocalAdAllowedForChannel(sp, channelId);

        if (!rewardedFires && !localFires) { callback.onAllowed(); return; }

        Runnable afterRewarded = () -> {
            if (localFires) showSequentialAds(activity, callback::onAllowed);
            else callback.onAllowed();
        };
        if (rewardedFires) {
            String unitId = fConfig != null
                ? fConfig.optString("rewardedAdUnitId", fConfig.optString("admobRewardedId", ""))
                : "";
            RewardedAdHelper.showOrSkip(activity, unitId, r -> afterRewarded.run());
        } else {
            afterRewarded.run();
        }
    }

    /**
     * Forced ad gate before opening an EXTERNAL encrypted link.
     * Hybrid behavior: when both network and local are flagged forceExternal, alternates
     * between them based on a counter so the user sees variety.
     */
    public static void maybeRunExternalGate(Activity activity, GateCallback callback) {
        JSONObject config = null;
        try { config = SupabaseDataManager.fetchAdConfig(); } catch (Throwable ignored) {}
        final JSONObject fConfig = config;

        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean networkOn = sp.getBoolean(KEY_NETWORK_FORCE_EXTERNAL, false)
                && fConfig != null && fConfig.optBoolean("adsEnabled", false);
        boolean localOn = sp.getBoolean(KEY_LOCAL_FORCE_EXTERNAL, false);

        if (!networkOn && !localOn) { callback.onAllowed(); return; }

        // Hybrid alternation
        int counter = sp.getInt("ext_ad_counter", 0);
        sp.edit().putInt("ext_ad_counter", counter + 1).apply();
        boolean useNetwork = networkOn && (!localOn || counter % 2 == 0);

        if (useNetwork) {
            String unitId = fConfig.optString("rewardedAdUnitId", fConfig.optString("admobRewardedId", ""));
            RewardedAdHelper.showOrSkip(activity, unitId, r -> {
                if (!r && localOn) showSequentialAds(activity, callback::onAllowed);
                else callback.onAllowed();
            });
        } else {
            showSequentialAds(activity, callback::onAllowed);
        }
    }

    /** Whitelist check: empty list = applies to ALL channels. */
    private static boolean isLocalAdAllowedForChannel(SharedPreferences sp, String channelId) {
        try {
            String raw = sp.getString(KEY_LOCAL_CHANNEL_LIST, "[]");
            JSONArray arr = new JSONArray(raw);
            if (arr.length() == 0) return true; // unrestricted
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
            if (array.length() == 0) {
                callback.onAllowed();
                return;
            }

            List<String> urls = new ArrayList<>();
            for (int i = 0; i < forcedCount; i++) {
                int idx = (lastIndex + i) % array.length();
                JSONObject obj = array.getJSONObject(idx);
                urls.add(obj.optString("video_url", ""));
            }

            int nextIndex = (lastIndex + urls.size()) % array.length();
            sp.edit().putInt(KEY_LAST_INDEX, nextIndex).apply();

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
        if (internalInstall) {
            UpdateInstaller.downloadAndInstall(activity, url);
        } else {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        }
    }

    public static final class SequentialAdBridge {
        private static GateCallback pending;

        private SequentialAdBridge() {}

        public static void registerCallback(GateCallback callback) {
            pending = callback;
        }

        public static void complete() {
            if (pending != null) {
                GateCallback cb = pending;
                pending = null;
                cb.onAllowed();
            }
        }
    }
}