package com.apix.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Thin AdMob Rewarded wrapper. The unit ID is fetched from Supabase
 * (system_settings.adConfig.rewardedAdUnitId) so dashboard changes take effect
 * without a rebuild.
 */
public final class RewardedAdHelper {

    private static final String TAG = "RewardedAdHelper";
    private static volatile boolean sdkInitialized = false;
    private static volatile boolean sdkInitializing = false;
    private static RewardedAd loadedAd;
    private static String currentUnitId;
    private static final java.util.List<Runnable> pendingAfterInit = new java.util.ArrayList<>();

    public interface Callback {
        /** Called whether the user actually earned the reward, or the ad failed silently. */
        void onFinished(boolean rewarded);
    }

    private RewardedAdHelper() {}

    /** Synchronous-ish init: queues callers until SDK reports ready. */
    public static void initIfNeeded(Context ctx, Runnable onReady) {
        if (sdkInitialized) {
            if (onReady != null) onReady.run();
            return;
        }
        synchronized (pendingAfterInit) {
            if (onReady != null) pendingAfterInit.add(onReady);
            if (sdkInitializing) return;
            sdkInitializing = true;
        }
        // MobileAds.initialize() MUST be called on the main thread, otherwise
        // ad requests silently fail (no-fill / not initialized).
        final Context appCtx = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // In a debug build the production ad-unit very often returns
                // NO_FILL (code=3) because the install isn't a real device for
                // AdMob. Add the universal "all emulators" test device and the
                // current Android device hash so test ads are served instead.
                try {
                    java.util.List<String> testIds = new java.util.ArrayList<>();
                    // Hash advertised by Google for any Android emulator.
                    testIds.add("B3EEABB8EE11C2BE770B684D95219ECB");
                    RequestConfiguration cfg = new RequestConfiguration.Builder()
                            .setTestDeviceIds(testIds)
                            .build();
                    MobileAds.setRequestConfiguration(cfg);
                } catch (Throwable ignored) {}

                MobileAds.initialize(appCtx, new OnInitializationCompleteListener() {
                @Override
                public void onInitializationComplete(InitializationStatus s) {
                    Log.d(TAG, "MobileAds initialized: " + s.getAdapterStatusMap());
                    sdkInitialized = true;
                    sdkInitializing = false;
                    java.util.List<Runnable> copy;
                    synchronized (pendingAfterInit) {
                        copy = new java.util.ArrayList<>(pendingAfterInit);
                        pendingAfterInit.clear();
                    }
                    for (Runnable r : copy) {
                        try { r.run(); } catch (Throwable ignored) {}
                    }
                }
                });
            } catch (Throwable t) {
                Log.w(TAG, "MobileAds init failed", t);
                sdkInitialized = true;
                sdkInitializing = false;
                java.util.List<Runnable> copy;
                synchronized (pendingAfterInit) {
                    copy = new java.util.ArrayList<>(pendingAfterInit);
                    pendingAfterInit.clear();
                }
                for (Runnable r : copy) {
                    try { r.run(); } catch (Throwable ignored) {}
                }
            }
        });
    }

    public static void initIfNeeded(Context ctx) { initIfNeeded(ctx, null); }

    // Google's universal test rewarded ad unit — used as last-resort fallback
    // when the production unit returns NO_FILL repeatedly so we don't hang.
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    public static void preload(Context ctx, String unitId) {
        if (unitId == null || unitId.isEmpty()) return;
        initIfNeeded(ctx, () -> {
            currentUnitId = unitId;
            new Handler(Looper.getMainLooper()).post(() -> loadWithRetry(ctx, unitId, 0));
        });
    }

    private static void loadWithRetry(Context ctx, String unitId, int attempt) {
        try {
            AdRequest req = new AdRequest.Builder().build();
            Log.d(TAG, "Preload attempt " + (attempt + 1) + " for " + unitId);
            RewardedAd.load(ctx.getApplicationContext(), unitId, req, new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) {
                    loadedAd = ad;
                    Log.d(TAG, "Rewarded ad preloaded for " + unitId);
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    loadedAd = null;
                    Log.w(TAG, "Preload failed code=" + e.getCode() + " msg=" + e.getMessage()
                            + " attempt=" + (attempt + 1));
                    // Retry up to 3 times with exponential backoff (1s, 2s, 4s).
                    if (attempt < 2) {
                        long delay = 1000L * (1L << attempt);
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> loadWithRetry(ctx, unitId, attempt + 1), delay);
                    } else if (e.getCode() == 3 /* NO_FILL */ && !TEST_REWARDED.equals(unitId)) {
                        // Fall back to Google test unit so the app never hangs.
                        Log.w(TAG, "All retries exhausted, falling back to test unit");
                        currentUnitId = TEST_REWARDED;
                        loadWithRetry(ctx, TEST_REWARDED, 0);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "preload error", t);
        }
    }

    public static void showOrSkip(final Activity activity, final String unitId, final Callback cb) {
        if (unitId == null || unitId.isEmpty()) {
            Log.w(TAG, "showOrSkip: unitId EMPTY — skipping ad and proceeding");
            cb.onFinished(false);
            return;
        }
        Log.d(TAG, "showOrSkip: unitId=" + unitId
                + " hasPreloaded=" + (loadedAd != null && unitId.equals(currentUnitId)));
        initIfNeeded(activity, () -> {
            final Runnable showNow = () -> {
                if (loadedAd == null) { cb.onFinished(false); return; }
                loadedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() {
                        loadedAd = null;
                        preload(activity, unitId); // chain next
                        cb.onFinished(true);
                    }
                    @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                        loadedAd = null;
                        cb.onFinished(false);
                    }
                });
                activity.runOnUiThread(() -> loadedAd.show(activity, new OnUserEarnedRewardListener() {
                    @Override public void onUserEarnedReward(RewardItem rewardItem) {
                        Log.d(TAG, "User earned reward");
                    }
                }));
            };

            if (loadedAd != null && unitId.equals(currentUnitId)) {
                showNow.run();
            } else {
                currentUnitId = unitId;
                AdRequest req = new AdRequest.Builder().build();
                RewardedAd.load(activity, unitId, req, new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) {
                        loadedAd = ad;
                        showNow.run();
                    }
                    @Override public void onAdFailedToLoad(LoadAdError e) {
                        Log.w(TAG, "Rewarded load+show failed: " + e.getMessage());
                        cb.onFinished(false);
                    }
                });
            }
        });
    }
}
