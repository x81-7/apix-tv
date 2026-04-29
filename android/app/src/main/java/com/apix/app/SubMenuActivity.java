package com.apix.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sub Menu Activity - shows sub-channels for a category
 * NO fullscreen - system bars visible like HomeActivity
 */
public class SubMenuActivity extends AppCompatActivity {

    private RecyclerView channelsRecycler;
    private TextView titleText;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submenu);

        // NO fullscreen here - keep system bars

        titleText = findViewById(R.id.submenu_title);
        channelsRecycler = findViewById(R.id.submenu_channels_recycler);
        ImageButton backButton = findViewById(R.id.submenu_back);

        String menuName = getIntent().getStringExtra("menuName");
        String menuJson = getIntent().getStringExtra("menuJson");

        titleText.setText(menuName != null ? menuName : "");
        backButton.setOnClickListener(v -> finish());

        if (menuJson == null) {
            Toast.makeText(this, "خطأ في تحميل القائمة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        RemoteModels.SideMenu menu = gson.fromJson(menuJson, RemoteModels.SideMenu.class);
        if (menu == null || menu.channels == null) {
            Toast.makeText(this, "لا توجد قنوات فرعية", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<RemoteModels.Channel> channels = new ArrayList<>();
        for (RemoteModels.SubChannel sc : menu.channels.values()) {
            if (!sc.hidden) {
                RemoteModels.Channel ch = new RemoteModels.Channel();
                ch.id = sc.id;
                ch.name = sc.name;
                ch.imageUrl = sc.imageUrl;
                ch.sortOrder = sc.sortOrder;
                ch.actionType = "direct_play";
                ch.stream = sc.stream;
                ch.androidStream = sc.androidStream;
                ch.androidActionType = sc.androidActionType;
                ch.preferredPlayer = sc.preferredPlayer;
                ch.forcedAspectRatio = sc.forcedAspectRatio;
                ch.lockAspectRatio = sc.lockAspectRatio;
                ch.offlineCacheEnabled = sc.offlineCacheEnabled;
                ch.cacheVersion = sc.cacheVersion;
                channels.add(ch);
            }
        }

        Collections.sort(channels, (a, b) -> a.sortOrder - b.sortOrder);

        // Always 2 columns
        channelsRecycler.setLayoutManager(new GridLayoutManager(this, 2));

        ChannelAdapter adapter = new ChannelAdapter(this, channels, this::playSubChannel);
        channelsRecycler.setAdapter(adapter);
    }

    private void playSubChannel(RemoteModels.Channel channel) {
        String androidAction = channel.androidActionType != null ? channel.androidActionType : "native";

        if ("intent".equals(androidAction) && channel.androidStream != null &&
            channel.androidStream.intentUri != null) {
            try {
                Intent intent = Intent.parseUri(channel.androidStream.intentUri, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            } catch (Exception e) {
                Toast.makeText(this, "فشل التشغيل", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if ("webview".equals(androidAction)) {
            Intent intent = new Intent(this, WebViewActivity.class);
            String url = channel.androidStream != null ? channel.androidStream.url :
                (channel.stream != null ? channel.stream.url : null);
            intent.putExtra("url", url);
            intent.putExtra("title", channel.name);
            intent.putExtra("orientation", channel.androidStream != null ? channel.androidStream.webViewOrientation : null);
            startActivity(intent);
            return;
        }

        // بناء الـ config أولاً قبل استخدامه
        StreamConfig config = new StreamConfig();
        config.title = channel.name;
        config.forcedAspectRatio = channel.androidStream != null && channel.androidStream.forcedAspectRatio != null
            ? channel.androidStream.forcedAspectRatio : channel.forcedAspectRatio;
        config.lockAspectRatio = channel.androidStream != null
            ? channel.androidStream.lockAspectRatio : channel.lockAspectRatio;

        if (channel.androidStream != null && channel.androidStream.url != null) {
            config.url = channel.androidStream.url;
            config.actionType = channel.androidActionType;

            if (channel.androidStream.headers != null) {
                config.headers = new StreamConfig.Headers();
                config.headers.userAgent = channel.androidStream.headers.get("userAgent");
                config.headers.referer = channel.androidStream.headers.get("referrer");
                config.headers.cookie = channel.androidStream.headers.get("cookie");
                config.headers.origin = channel.androidStream.headers.get("origin");
            }

            config.webViewOrientation = channel.androidStream.webViewOrientation;

            if (channel.androidStream.drmScheme != null) {
                config.drm = new StreamConfig.DrmConfig();
                config.drm.scheme = channel.androidStream.drmScheme;
                config.drm.licenseUrl = channel.androidStream.drmLicenseUrl;
                String keyId = channel.androidStream.drmKeyId;
                String key = channel.androidStream.drmKey;
                if ("combined".equals(channel.androidStream.drmClearKeyMode) &&
                    channel.androidStream.drmClearKeyCombined != null) {
                    String[] parts = channel.androidStream.drmClearKeyCombined.split(":");
                    if (parts.length == 2) { keyId = parts[0]; key = parts[1]; }
                }
                config.drm.keyId = keyId;
                config.drm.key = key;
            }

            if (channel.androidStream.customHeaders != null) {
                config.customHeaders = new java.util.HashMap<>();
                for (RemoteModels.CustomHeader header : channel.androidStream.customHeaders) {
                    if (header != null && header.key != null && header.value != null && !header.key.isEmpty()) {
                        config.customHeaders.put(header.key, header.value);
                    }
                }
            }

            if (channel.androidStream.drmLicenseHeaders != null) {
                config.drmLicenseHeaders = new java.util.HashMap<>();
                for (RemoteModels.CustomHeader header : channel.androidStream.drmLicenseHeaders) {
                    if (header != null && header.key != null && header.value != null && !header.key.isEmpty()) {
                        config.drmLicenseHeaders.put(header.key, header.value);
                    }
                }
            }

            if (channel.androidStream.servers != null) {
                config.servers = new java.util.ArrayList<>();
                for (RemoteModels.Server s : channel.androidStream.servers) {
                    StreamConfig.Server server = new StreamConfig.Server();
                    server.name = s.name;
                    server.url = s.url;
                    config.servers.add(server);
                }
            }

            config.backupUrl = channel.androidStream.backupUrl;
            config.subtitleUrl = channel.androidStream.subtitleUrl;
            config.forcedAspectRatio = channel.androidStream.forcedAspectRatio != null ? channel.androidStream.forcedAspectRatio : config.forcedAspectRatio;
            config.lockAspectRatio = channel.androidStream.lockAspectRatio;
            if (channel.androidStream.logoOverlay != null) {
                config.logoOverlay = new StreamConfig.LogoOverlay();
                config.logoOverlay.url = channel.androidStream.logoOverlay.url;
                config.logoOverlay.position = channel.androidStream.logoOverlay.position;
                config.logoOverlay.offsetX = channel.androidStream.logoOverlay.offsetX;
                config.logoOverlay.offsetY = channel.androidStream.logoOverlay.offsetY;
                config.logoOverlay.width = channel.androidStream.logoOverlay.width;
                config.logoOverlay.height = channel.androidStream.logoOverlay.height;
                config.logoOverlay.opacity = channel.androidStream.logoOverlay.opacity;
            }

            if (channel.androidStream.audioSources != null) {
                config.audioSources = new java.util.ArrayList<>();
                for (RemoteModels.AudioSource src : channel.androidStream.audioSources) {
                    if (src != null && src.url != null && !src.url.isEmpty()) {
                        StreamConfig.AudioSource audioSource = new StreamConfig.AudioSource();
                        audioSource.name = src.name;
                        audioSource.url = src.url;
                        config.audioSources.add(audioSource);
                    }
                }
            }
        } else if (channel.stream != null) {
            config.url = channel.stream.url;
            if (channel.stream.userAgent != null || channel.stream.referrer != null) {
                config.headers = new StreamConfig.Headers();
                config.headers.userAgent = channel.stream.userAgent;
                config.headers.referer = channel.stream.referrer;
                config.headers.cookie = channel.stream.cookies;
            }
        }

        if (config.url == null || config.url.isEmpty()) {
            Toast.makeText(this, "لا يوجد رابط بث", Toast.LENGTH_SHORT).show();
            return;
        }

        // الآن وبعد أن أصبح config ممتلئاً بالبيانات، يمكننا إرساله للمشغل الهجين
        if ("shaka_web".equals(androidAction) || "jw_web".equals(androidAction)) {
            // إضافة نوع المشغل الهجين للكونفج ليعرفه تطبيق الـ Kotlin
            config.hybridPlayerType = "jw_web".equals(androidAction) ? "jw" : "shaka";
            
            Intent intent = new Intent(this, ComposeActivity.class);
            intent.putExtra("streamConfig", gson.toJson(config));
            startActivity(intent);
            return;
        }

        // المشغل الأصلي الافتراضي
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("streamConfig", gson.toJson(config));
        startActivity(intent);
    }
}
