package com.apix.app;

import android.util.Log;

public class StreamAnalyzer {

    public static void analyze(StreamConfig config) {
        if (config == null || config.url == null) return;

        if (config.url.contains(".mpd")) {
            Log.d("STREAM", "DASH");
        }

        if (config.drm != null) {
            if (config.drm.key != null) {
                Log.d("DRM", "ClearKey");
            } else if (config.drm.licenseUrl != null) {
                Log.d("DRM", "Widevine");
            }
        }
    }
}
