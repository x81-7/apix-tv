package com.apix.app;

import android.net.Uri;
import android.util.Base64;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import java.nio.charset.StandardCharsets;

/**
 * Builds a MediaItem with intelligent MimeType detection.
 *
 * Some channels disguise HLS streams behind .png / .json / .jpg URLs (or a
 * trailing #hls fragment). When we detect any of those signals we force
 * APPLICATION_M3U8 so ExoPlayer doesn't try to decode them as images.
 */
public class MediaSourceBuilder {

    /**
     * Build using the resolved URL (after dynamic JSON resolution).
     *
     * @param config         original config (DRM lives here)
     * @param resolvedUrl    the actual stream URL to play
     * @param hintForceHls   resolver-side hint (e.g. response sniffed as HLS)
     */
    public static MediaItem build(StreamConfig config, String resolvedUrl, boolean hintForceHls) {
        if (resolvedUrl == null || resolvedUrl.isEmpty()) return null;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(resolvedUrl));

        String forced = forcedMime(resolvedUrl, hintForceHls);
        if (forced != null) builder.setMimeType(forced);

        applyDrm(builder, config);
        return builder.build();
    }

    /** Back-compat: legacy callers without a resolver pass through. */
    public static MediaItem build(StreamConfig config, String formatHint) {
        if (config == null || config.url == null) return null;
        String url = config.url.contains("|") ? config.url.split("\\|")[0] : config.url;
        boolean hls = "hls".equalsIgnoreCase(formatHint);
        return build(config, url, hls);
    }

    private static String forcedMime(String url, boolean hint) {
        if (url == null) return null;
        String l = url.toLowerCase();
        if (hint) return MimeTypes.APPLICATION_M3U8;
        if (l.contains(".m3u8") || l.contains("#hls")) return MimeTypes.APPLICATION_M3U8;
        if (l.contains(".mpd")) return MimeTypes.APPLICATION_MPD;
        // HLS hidden behind fake image / json extensions (no real content type).
        if (l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg")
                || l.endsWith(".webp") || l.endsWith(".json")) {
            return MimeTypes.APPLICATION_M3U8;
        }
        return null;
    }

    private static void applyDrm(MediaItem.Builder builder, StreamConfig config) {
        if (config == null || config.drm == null) return;
        StreamConfig.DrmConfig drm = config.drm;
        String scheme = drm.scheme != null ? drm.scheme.toLowerCase() : "clearkey";

        if ("widevine".equals(scheme) && drm.licenseUrl != null) {
            builder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drm.licenseUrl)
                            .build());
            return;
        }
        if ("playready".equals(scheme) && drm.licenseUrl != null) {
            builder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.PLAYREADY_UUID)
                            .setLicenseUri(drm.licenseUrl)
                            .build());
            return;
        }
        if (drm.keyId != null && drm.key != null && !drm.keyId.isEmpty() && !drm.key.isEmpty()) {
            String dataUri = buildClearKeyDataUri(drm.keyId, drm.key);
            if (!dataUri.isEmpty()) {
                builder.setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                                .setLicenseUri(dataUri)
                                .build());
            }
        } else if (drm.licenseUrl != null) {
            builder.setDrmConfiguration(
                    new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri(drm.licenseUrl)
                            .build());
        }
    }

    // ---- ClearKey helpers -------------------------------------------------

    private static String buildClearKeyDataUri(String hexKid, String hexKey) {
        String cleanKid = hexKid.replaceAll("[^a-fA-F0-9]", "");
        String cleanKey = hexKey.replaceAll("[^a-fA-F0-9]", "");
        if (cleanKid.length() != 32 || cleanKey.length() != 32) return "";

        String b64Kid = hexToBase64Url(cleanKid);
        String b64Key = hexToBase64Url(cleanKey);

        String reversedKid = cleanKid.substring(6, 8) + cleanKid.substring(4, 6)
                + cleanKid.substring(2, 4) + cleanKid.substring(0, 2)
                + cleanKid.substring(10, 12) + cleanKid.substring(8, 10)
                + cleanKid.substring(14, 16) + cleanKid.substring(12, 14)
                + cleanKid.substring(16);
        String b64ReversedKid = hexToBase64Url(reversedKid);

        String json = "{\"keys\":["
                + "{\"kty\":\"oct\",\"k\":\"" + b64Key + "\",\"kid\":\"" + b64Kid + "\"},"
                + "{\"kty\":\"oct\",\"k\":\"" + b64Key + "\",\"kid\":\"" + b64ReversedKid + "\"}"
                + "],\"type\":\"temporary\"}";

        String base64Json = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "data:application/json;base64," + base64Json;
    }

    private static String hexToBase64Url(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return Base64.encodeToString(data, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }
}
