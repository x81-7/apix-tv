package com.apix.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native Home Activity - matches website design exactly
 * Portrait: bottom nav (fixed equal width), 2-col channels, APiX header + search
 * Landscape/TV: right sidebar, clock + search, 2-col channels
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // Portrait views
    private LinearLayout portraitLayout;
    private RecyclerView categoriesRecyclerPortrait;
    private RecyclerView channelsRecyclerPortrait;
    private TextView categoryTitlePortrait;

    // Landscape views
    private LinearLayout landscapeLayout;
    private RecyclerView categoriesRecyclerLandscape;
    private RecyclerView channelsRecyclerLandscape;
    private TextView categoryTitleLandscape;
    private TextView clockText;

    // Search
    private LinearLayout searchOverlay;
    private EditText searchInput;
    private TextView searchCancel;
    private RecyclerView searchResultsRecycler;

    private ProgressBar loadingBar;
    private LinearLayout errorLayout;
    private TextView errorText;

    private Gson gson = new Gson();

    private List<RemoteModels.Category> categories = new ArrayList<>();
    private Map<String, RemoteModels.SideMenu> sideMenus = new HashMap<>();
    private RemoteModels.Category selectedCategory;

    private List<RemoteModels.Channel> allChannels = new ArrayList<>();

    private CategoryAdapter categoryAdapterPortrait;
    private CategoryAdapter categoryAdapterLandscape;
    private ChannelAdapter channelAdapterPortrait;
    private ChannelAdapter channelAdapterLandscape;
    private ChannelAdapter searchAdapter;

    private boolean isLandscape = false;

    // Clock updater
    private Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        loadData();
        startClock();
    }

    private void startClock() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                if (clockText != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    clockText.setText(sdf.format(new Date()));
                }
                clockHandler.postDelayed(this, 30000);
            }
        };
        clockHandler.post(clockRunnable);
    }

    private void setApixBranding(TextView tv) {
        // "APiX" - AP white bold, iX gold bold
        SpannableString spannable = new SpannableString("APiX");
        // AP = white
        spannable.setSpan(new ForegroundColorSpan(Color.WHITE), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // iX = gold
        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#FFD700")), 2, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 2, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(spannable);
    }

    private void initViews() {
        loadingBar = findViewById(R.id.loading_bar);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);

        // Portrait
        portraitLayout = findViewById(R.id.portrait_layout);
        categoriesRecyclerPortrait = findViewById(R.id.categories_recycler);
        channelsRecyclerPortrait = findViewById(R.id.channels_recycler);
        categoryTitlePortrait = findViewById(R.id.category_title);

        // Set APiX branding on portrait header
        TextView appNamePortrait = findViewById(R.id.app_name_portrait);
        setApixBranding(appNamePortrait);

        // Landscape
        landscapeLayout = findViewById(R.id.landscape_layout);
        categoriesRecyclerLandscape = findViewById(R.id.categories_recycler_landscape);
        channelsRecyclerLandscape = findViewById(R.id.channels_recycler_landscape);
        categoryTitleLandscape = findViewById(R.id.category_title_landscape);
        clockText = findViewById(R.id.clock_text);

        // Search
        searchOverlay = findViewById(R.id.search_overlay);
        searchInput = findViewById(R.id.search_input);
        searchCancel = findViewById(R.id.search_cancel);
        searchResultsRecycler = findViewById(R.id.search_results_recycler);

        // Search buttons
        ImageButton searchBtnPortrait = findViewById(R.id.search_button_portrait);
        ImageButton searchBtnLandscape = findViewById(R.id.search_button_landscape);

        searchBtnPortrait.setOnClickListener(v -> showSearch());
        searchBtnLandscape.setOnClickListener(v -> showSearch());
        searchCancel.setOnClickListener(v -> hideSearch());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                performSearch(s.toString());
            }
        });

        // Search results
        searchAdapter = new ChannelAdapter(this, new ArrayList<>(), this::onChannelClick);
        int searchSpan = 2;
        searchResultsRecycler.setLayoutManager(new GridLayoutManager(this, searchSpan));
        searchResultsRecycler.setAdapter(searchAdapter);

        // Portrait: bottom nav - use GridLayoutManager for equal width distribution
        // Will be set after data loads (need category count)
        categoryAdapterPortrait = new CategoryAdapter(this, categories, this::onCategorySelected);
        categoryAdapterPortrait.setSideMode(false);
        categoriesRecyclerPortrait.setAdapter(categoryAdapterPortrait);

        channelsRecyclerPortrait.setLayoutManager(new GridLayoutManager(this, 2));
        channelAdapterPortrait = new ChannelAdapter(this, new ArrayList<>(), this::onChannelClick);
        channelsRecyclerPortrait.setAdapter(channelAdapterPortrait);

        // Landscape: vertical side categories, 2 columns
        categoriesRecyclerLandscape.setLayoutManager(
            new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        categoryAdapterLandscape = new CategoryAdapter(this, categories, this::onCategorySelected);
        categoryAdapterLandscape.setSideMode(true);
        categoriesRecyclerLandscape.setAdapter(categoryAdapterLandscape);

        channelsRecyclerLandscape.setLayoutManager(new GridLayoutManager(this, 2));
        channelAdapterLandscape = new ChannelAdapter(this, new ArrayList<>(), this::onChannelClick);
        channelsRecyclerLandscape.setAdapter(channelAdapterLandscape);

        applyLayout();
    }

    private void setupBottomNavLayout() {
        // Use GridLayoutManager with span = category count for equal distribution
        int count = categories.size();
        if (count > 0) {
            GridLayoutManager glm = new GridLayoutManager(this, count);
            categoriesRecyclerPortrait.setLayoutManager(glm);
        } else {
            categoriesRecyclerPortrait.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, true));
        }
    }

    private void showSearch() {
        searchOverlay.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideSearch() {
        searchOverlay.setVisibility(View.GONE);
        searchInput.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchAdapter.updateData(new ArrayList<>());
            return;
        }

        String filter = query.toLowerCase().trim();
        List<RemoteModels.Channel> results = new ArrayList<>();

        for (RemoteModels.Channel ch : allChannels) {
            if (ch.name != null && ch.name.toLowerCase().contains(filter)) {
                results.add(ch);
            }
        }

        for (RemoteModels.SideMenu menu : sideMenus.values()) {
            if (menu.channels != null) {
                for (RemoteModels.SubChannel sc : menu.channels.values()) {
                    if (sc.name != null && sc.name.toLowerCase().contains(filter)) {
                        RemoteModels.Channel ch = new RemoteModels.Channel();
                        ch.id = sc.id;
                        ch.name = sc.name;
                        ch.imageUrl = sc.imageUrl;
                        ch.actionType = "direct_play";
                        ch.stream = sc.stream;
                        ch.androidStream = sc.androidStream;
                        ch.androidActionType = sc.androidActionType;
                        ch.forcedAspectRatio = sc.forcedAspectRatio;
                        ch.lockAspectRatio = sc.lockAspectRatio;
                        results.add(ch);
                    }
                }
            }
        }

        searchAdapter.updateData(results);
    }

    private void applyLayout() {
        isLandscape = getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            portraitLayout.setVisibility(View.GONE);
            landscapeLayout.setVisibility(View.VISIBLE);
        } else {
            portraitLayout.setVisibility(View.VISIBLE);
            landscapeLayout.setVisibility(View.GONE);
        }
    }

    private void onCategorySelected(RemoteModels.Category category) {
        selectedCategory = category;

        categoryTitlePortrait.setText(category.name);
        categoryTitleLandscape.setText(category.name);

        int pos = categories.indexOf(category);
        if (pos >= 0) {
            categoryAdapterPortrait.setSelected(pos);
            categoryAdapterLandscape.setSelected(pos);
        }

        updateChannels();
    }

    private void loadData() {
        loadingBar.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);

        // Load cached data first for instant display
        SupabaseDataManager.DataBundle cached = SupabaseDataManager.loadCached(this);
        if (cached != null) {
            applyData(cached);
            loadingBar.setVisibility(View.GONE);
        }

        // Fetch fresh data from Supabase
        SupabaseDataManager.fetchRemote(this, new SupabaseDataManager.DataCallback() {
            @Override
            public void onSuccess(SupabaseDataManager.DataBundle data) {
                runOnUiThread(() -> {
                    applyData(data);
                    // Reconcile encrypted cache against server cache_version —
                    // any panel edit bumps cache_version which replaces the
                    // cached JSON forcibly on the next refresh.
                    try {
                        org.json.JSONArray remote = new org.json.JSONArray();
                        for (RemoteModels.Channel c : data.allChannels) {
                            org.json.JSONObject j = new org.json.JSONObject();
                            j.put("id", c.id);
                            j.put("cache_version", c.cacheVersion);
                            j.put("offline_cache_enabled", c.offlineCacheEnabled);
                            remote.put(j);
                        }
                        SecureCacheManager.reconcile(HomeActivity.this, remote);
                    } catch (Throwable ignored) {}
                    loadingBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loadingBar.setVisibility(View.GONE);
                    if (cached == null) {
                        errorLayout.setVisibility(View.VISIBLE);
                        errorText.setText("لم نستطع الوصول للقنوات: " + error);
                    }
                });
            }
        });
    }

    private void applyData(SupabaseDataManager.DataBundle data) {
        categories.clear();
        allChannels.clear();
        sideMenus.clear();

        categories.addAll(data.categories);
        allChannels.addAll(data.allChannels);
        sideMenus.putAll(data.sideMenus);

        setupBottomNavLayout();
        categoryAdapterPortrait.updateData(categories);
        categoryAdapterLandscape.updateData(categories);

        if (!categories.isEmpty() && selectedCategory == null) {
            selectedCategory = categories.get(0);
            categoryTitlePortrait.setText(selectedCategory.name);
            categoryTitleLandscape.setText(selectedCategory.name);
            categoryAdapterPortrait.setSelected(0);
            categoryAdapterLandscape.setSelected(0);
        }
        updateChannels();
    }

    private void updateChannels() {
        if (selectedCategory == null || selectedCategory.channels == null) return;

        List<RemoteModels.Channel> channels = new ArrayList<>();
        for (RemoteModels.Channel ch : selectedCategory.channels.values()) {
            if (!ch.hidden) channels.add(ch);
        }

        Collections.sort(channels, (a, b) -> a.sortOrder - b.sortOrder);

        channelAdapterPortrait.updateData(channels);
        channelAdapterLandscape.updateData(channels);
    }

    private void onChannelClick(RemoteModels.Channel channel) {
        if (channel == null) return;

        if (searchOverlay.getVisibility() == View.VISIBLE) {
            hideSearch();
        }

        String actionType = channel.actionType != null ? channel.actionType : "direct_play";

        switch (actionType) {
            case "open_submenu":
                openSubMenu(channel);
                break;
            case "external_link":
                if (channel.externalUrl != null) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(channel.externalUrl));
                    startActivity(browserIntent);
                }
                break;
            default:
                playChannel(channel);
                break;
        }
    }

    private void openSubMenu(RemoteModels.Channel channel) {
        if (channel.sideMenuId == null) return;
        RemoteModels.SideMenu menu = sideMenus.get(channel.sideMenuId);
        if (menu == null || menu.channels == null) {
            Toast.makeText(this, "لا توجد قنوات فرعية", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SubMenuActivity.class);
        intent.putExtra("menuId", channel.sideMenuId);
        intent.putExtra("menuName", channel.name);
        intent.putExtra("menuJson", gson.toJson(menu));
        startActivity(intent);
    }

    private void playChannel(RemoteModels.Channel channel) {
        // Panel: offline_cache_enabled=false → refetch link every time so users
        // never use a stale/cached stream URL.
        if (!channel.offlineCacheEnabled && channel.id != null) {
            Toast.makeText(this, "جاري تحديث الرابط…", Toast.LENGTH_SHORT).show();
            SupabaseDataManager.fetchChannelFresh(channel.id, new SupabaseDataManager.FreshChannelCallback() {
                @Override public void onSuccess(RemoteModels.Channel fresh) {
                    runOnUiThread(() -> playChannelInternal(fresh));
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> playChannelInternal(channel)); // fallback to local
                }
            });
            return;
        }
        playChannelInternal(channel);
    }

    private void playChannelInternal(RemoteModels.Channel channel) {
        String androidAction = channel.androidActionType != null ? channel.androidActionType : "native";

        if ("intent".equals(androidAction) && channel.androidStream != null &&
            channel.androidStream.intentUri != null) {
            launchIntent(channel.androidStream.intentUri);
            return;
        }

        if ("shaka_web".equals(androidAction) || "jw_web".equals(androidAction)) {
            Intent intent = new Intent(this, ComposeActivity.class);
            intent.putExtra("streamConfig", gson.toJson(buildStreamConfig(channel)));
            startActivity(intent);
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

        StreamConfig config = buildStreamConfig(channel);
        if (config == null || config.url == null || config.url.isEmpty()) {
            Toast.makeText(this, "لا يوجد رابط بث", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("streamConfig", gson.toJson(config));
        startActivity(intent);
    }

    private StreamConfig buildStreamConfig(RemoteModels.Channel channel) {
        StreamConfig config = new StreamConfig();
        config.title = channel.name;
        // Panel aspect-ratio lock passthrough → PlayerActivity applies it.
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
                config.customHeaders = new HashMap<>();
                for (RemoteModels.CustomHeader header : channel.androidStream.customHeaders) {
                    if (header != null && header.key != null && header.value != null && !header.key.isEmpty()) {
                        config.customHeaders.put(header.key, header.value);
                    }
                }
            }

            if (channel.androidStream.drmLicenseHeaders != null) {
                config.drmLicenseHeaders = new HashMap<>();
                for (RemoteModels.CustomHeader header : channel.androidStream.drmLicenseHeaders) {
                    if (header != null && header.key != null && header.value != null && !header.key.isEmpty()) {
                        config.drmLicenseHeaders.put(header.key, header.value);
                    }
                }
            }

            if (channel.androidStream.servers != null) {
                config.servers = new ArrayList<>();
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
                config.audioSources = new ArrayList<>();
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

        return config;
    }

    private void launchIntent(String intentUri) {
        try {
            Intent intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                String packageName = intent.getPackage();
                if (packageName != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=" + packageName)));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "فشل التشغيل", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (searchOverlay.getVisibility() == View.VISIBLE) {
            hideSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLayout();

        if (!categories.isEmpty()) {
            setupBottomNavLayout();
            categoryAdapterPortrait.updateData(categories);
            categoryAdapterLandscape.updateData(categories);
            updateChannels();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockHandler != null && clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }
}
