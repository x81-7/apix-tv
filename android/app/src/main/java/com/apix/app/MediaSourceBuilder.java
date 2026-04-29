package com.apix.app;

import android.net.Uri;
import android.util.Base64;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.C;
import java.nio.charset.StandardCharsets;

public class MediaSourceBuilder {

    public static MediaItem build(StreamConfig config, String format) {
        if (config.url == null) return null;

        String url = config.url.contains("|") ? config.url.split("\\|")[0] : config.url;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));

        if ("dash".equals(format)) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if ("hls".equals(format)) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }

        // 🔥 تطبيق الخدعة السحرية للـ DRM في الجافا
        if (config.drm != null) {
            String scheme = config.drm.scheme != null ? config.drm.scheme.toLowerCase() : "clearkey";
            
            if (scheme.equals("widevine") && config.drm.licenseUrl != null) {
                builder.setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(config.drm.licenseUrl)
                                .build()
                );
            } else if (scheme.equals("playready") && config.drm.licenseUrl != null) {
                builder.setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.PLAYREADY_UUID)
                                .setLicenseUri(config.drm.licenseUrl)
                                .build()
                );
            } else {
                // تفعيل Data URI لـ ClearKey
                if (config.drm.keyId != null && config.drm.key != null && !config.drm.keyId.isEmpty() && !config.drm.key.isEmpty()) {
                    String dataUri = buildClearKeyDataUri(config.drm.keyId, config.drm.key);
                    builder.setDrmConfiguration(
                            new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                                    .setLicenseUri(dataUri)
                                    .build()
                    );
                } else if (config.drm.licenseUrl != null) {
                    builder.setDrmConfiguration(
                            new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                                    .setLicenseUri(config.drm.licenseUrl)
                                    .build()
                    );
                }
            }
        }

        return builder.build();
    }

    // ==========================================
    // دوال تحويل المفاتيح
    // ==========================================
    private static String buildClearKeyDataUri(String hexKid, String hexKey) {
        String cleanKid = hexKid.replaceAll("[^a-fA-F0-9]", "");
        String cleanKey = hexKey.replaceAll("[^a-fA-F0-9]", "");

        if (cleanKid.length() != 32 || cleanKey.length() != 32) return "";

        String b64Kid = hexToBase64Url(cleanKid);
        String b64Key = hexToBase64Url(cleanKey);

        // المفتاح المعكوس
        String reversedKid = cleanKid.substring(6, 8) + cleanKid.substring(4, 6) +
                             cleanKid.substring(2, 4) + cleanKid.substring(0, 2) +
                             cleanKid.substring(10, 12) + cleanKid.substring(8, 10) +
                             cleanKid.substring(14, 16) + cleanKid.substring(12, 14) +
                             cleanKid.substring(16);
        String b64ReversedKid = hexToBase64Url(reversedKid);

        String json = "{\"keys\":[" +
                "{\"kty\":\"oct\",\"k\":\"" + b64Key + "\",\"kid\":\"" + b64Kid + "\"}," +
                "{\"kty\":\"oct\",\"k\":\"" + b64Key + "\",\"kid\":\"" + b64ReversedKid + "\"}" +
                "],\"type\":\"temporary\"}";

        String base64Json = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "data:application/json;base64," + base64Json;
    }

    private static String hexToBase64Url(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return Base64.encodeToString(data, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }
}
