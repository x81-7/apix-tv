package com.apix.app;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Data class representing stream configuration passed from WebView/Compose
 */
public class StreamConfig {
    
    @SerializedName("url")
    public String url;
    
    @SerializedName("title")
    public String title;
    
    @SerializedName("actionType")
    public String actionType;

    @SerializedName("webViewOrientation")
    public String webViewOrientation;
    
    @SerializedName("headers")
    public Headers headers;
    
    @SerializedName("customHeaders")
    public Map<String, String> customHeaders;
    
    @SerializedName("drm")
    public DrmConfig drm;
    
    @SerializedName("drmLicenseHeaders")
    public Map<String, String> drmLicenseHeaders;
    
    @SerializedName("intentUri")
    public String intentUri;
    
    @SerializedName("servers")
    public List<Server> servers;
    
    @SerializedName("backupUrl")
    public String backupUrl;
    
    @SerializedName("audioSources")
    public List<AudioSource> audioSources;
    
    @SerializedName("subtitleUrl")
    public String subtitleUrl;
    
    @SerializedName("hybridPlayerType")
    public String hybridPlayerType; 

    // 🔥 الإضافات الجديدة للميزات المتقدمة لكي لا تفشل عملية البناء
    @SerializedName("dynamicApi")
    public DynamicApi dynamicApi;

    @SerializedName("logoOverlay")
    public LogoOverlay logoOverlay;

    @SerializedName("forcedAspectRatio")
    public String forcedAspectRatio;

    @SerializedName("lockAspectRatio")
    public boolean lockAspectRatio;
    
    public static class Headers {
        @SerializedName("User-Agent")
        public String userAgent;
        @SerializedName("Referer")
        public String referer;
        @SerializedName("Cookie")
        public String cookie;
        @SerializedName("Origin")
        public String origin;
    }
    
    public static class DrmConfig {
        @SerializedName("licenseUrl")
        public String licenseUrl;
        @SerializedName("scheme")
        public String scheme;
        @SerializedName("keyId")
        public String keyId;
        @SerializedName("key")
        public String key;
    }
    
    public static class Server {
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;
    }
    
    public static class AudioSource {
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;
    }

    // 🔥 كلاسات الميزات الجديدة (API الديناميكي واللوجو)
    public static class DynamicApi {
        @SerializedName("enabled")
        public boolean enabled;
        @SerializedName("endpoint")
        public String endpoint;
        @SerializedName("channelIdParam")
        public String channelIdParam;
        @SerializedName("method")
        public String method;
        @SerializedName("headers")
        public Map<String, String> headers;
    }

    public static class LogoOverlay {
        @SerializedName("url")
        public String url;
        @SerializedName("position")
        public String position;
        @SerializedName("offsetX")
        public int offsetX;
        @SerializedName("offsetY")
        public int offsetY;
        @SerializedName("width")
        public int width;
        @SerializedName("height")
        public int height;
        @SerializedName("opacity")
        public float opacity;
    }
    
    public String getUserAgent() { return headers != null && headers.userAgent != null ? headers.userAgent : ""; }
    public String getReferer() { return headers != null && headers.referer != null ? headers.referer : ""; }
    public String getCookie() { return headers != null && headers.cookie != null ? headers.cookie : ""; }
    public String getOrigin() { return headers != null && headers.origin != null ? headers.origin : ""; }
    
    public boolean hasDrm() {
        return drm != null && ((drm.licenseUrl != null && !drm.licenseUrl.isEmpty()) || 
               (drm.keyId != null && !drm.keyId.isEmpty()) ||
               (drm.key != null && !drm.key.isEmpty()));
    }
    
    public boolean hasHeaders() {
        return headers != null && (
            (headers.userAgent != null && !headers.userAgent.isEmpty()) ||
            (headers.referer != null && !headers.referer.isEmpty()) ||
            (headers.cookie != null && !headers.cookie.isEmpty()) ||
            (headers.origin != null && !headers.origin.isEmpty()));
    }
    
    public boolean hasCustomHeaders() {
        return customHeaders != null && !customHeaders.isEmpty();
    }
    
    public boolean hasDrmLicenseHeaders() {
        return drmLicenseHeaders != null && !drmLicenseHeaders.isEmpty();
    }
    
    public boolean hasAudioSources() {
        return audioSources != null && !audioSources.isEmpty();
    }
    
    public boolean hasServers() { return servers != null && !servers.isEmpty(); }
    public boolean isNativeAction() { return actionType == null || actionType.isEmpty() || "native".equals(actionType); }
    public boolean isWebViewAction() { return "webview".equals(actionType); }
    public boolean isHybridAction() { return "shaka_web".equals(actionType) || "jw_web".equals(actionType); }
    public boolean isIntentAction() { return "intent".equals(actionType); }
}
