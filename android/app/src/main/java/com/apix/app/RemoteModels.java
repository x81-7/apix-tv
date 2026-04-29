package com.apix.app;

import java.util.List;
import java.util.Map;

/**
 * Data models matching the remote backend (Supabase) structure.
 * All persistence and realtime sync go through Supabase REST + Realtime.
 */
public class RemoteModels {

    public static class Category {
        public String id;
        public String name;
        public int sortOrder;
        public Map<String, Channel> channels;
        public boolean hidden;
    }

    public static class Channel {
        public String id;
        public String name;
        public String imageUrl;
        public int sortOrder;
        public String actionType; // "direct_play", "open_submenu", "external_link"
        public boolean hidden;

        // Web settings
        public StreamConfig stream;
        public String sideMenuId;
        public String externalUrl;
        public String preferredPlayer;

        // Android settings
        public AndroidStreamConfig androidStream;
        public String androidActionType; // "native", "webview", "intent", "youtube", "shaka_web", "jw_web"

        // iOS settings
        public IosStreamConfig iosStream;
        public String iosActionType; // "native", "webview", "external"

        // Panel integration: offline cache toggle (per-channel).
        public boolean offlineCacheEnabled;
        // Per-channel PIN — when set, app prompts the user before playing.
        public String pinCode;
        // Panel integration: forced aspect ratio + lock (applied in PlayerActivity).
        public String forcedAspectRatio;   // "original" | "fit" | "stretch" | "16:9" | "4:3"
        public boolean lockAspectRatio;
        // Cache version — bumps on every panel edit. Used to force client to
        // discard its locally cached copy & persist the new one.
        public long cacheVersion;
    }

    public static class StreamConfig {
        public String url;
        public String userAgent;
        public String referrer;
        public String cookies;
        public DRMConfig drm;
    }

    public static class AndroidStreamConfig {
        public String url;
        public String webViewOrientation;
        public Map<String, String> headers;
        public List<CustomHeader> customHeaders;
        public String intentUri;
        public String drmLicenseUrl;
        public String drmScheme;
        public String drmKeyId;
        public String drmKey;
        public String drmClearKeyCombined;
        public String drmClearKeyMode;
        public List<CustomHeader> drmLicenseHeaders;
        public List<Server> servers;
        public String backupUrl;
        public List<AudioSource> audioSources;
        public String subtitleUrl;
        public String forcedAspectRatio;
        public boolean lockAspectRatio;
        public LogoOverlay logoOverlay;
    }

    /** iOS-specific stream configuration mirroring the Android shape. */
    public static class IosStreamConfig {
        public String url;
        public String userAgent;
        public String referrer;
        public String cookies;
        public String origin;
        public Map<String, String> headers;
        public List<CustomHeader> customHeaders;
        public String backupUrl;
        public String subtitleUrl;
        public String drmScheme;
        public String drmKeyId;
        public String drmKey;
        public String drmLicenseUrl;
    }

    public static class DRMConfig {
        public String clearKeyId;
        public String clearKeyKey;
        public String clearKeyCombined;
        public String clearKeyUrl;
        public String clearKeyMode;
    }

    public static class Server {
        public String name;
        public String url;
    }

    public static class CustomHeader {
        public String key;
        public String value;
    }

    public static class AudioSource {
        public String name;
        public String url;
    }

    public static class LogoOverlay {
        public String url;
        public String position;
        public int offsetX;
        public int offsetY;
        public int width;
        public int height;
        public float opacity;
    }

    public static class SideMenu {
        public String id;
        public String name;
        public String pinCode;
        public Map<String, SubChannel> channels;
    }

    public static class SubChannel {
        public String id;
        public String name;
        public String imageUrl;
        public StreamConfig stream;
        public int sortOrder;
        public String preferredPlayer;
        public String pinCode;
        public boolean hidden;
        public AndroidStreamConfig androidStream;
        public String androidActionType;
        public IosStreamConfig iosStream;
        public String iosActionType;
        public boolean offlineCacheEnabled;
        public String forcedAspectRatio;
        public boolean lockAspectRatio;
        public long cacheVersion;
    }
}
