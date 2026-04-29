import React, { useEffect, useMemo, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import PlayerWrapper from '@/components/player/PlayerWrapper';
import { useIptvData } from '@/hooks/useIptvData';
import { useTVNavigation } from '@/hooks/useTVNavigation';
import { useClock } from '@/hooks/useClock';
import { ChannelCard, Sidebar, BottomNav, SearchOverlay, Loader, SettingsSection } from '@/components/tv';
import { isAndroidApp, sendToAndroid, buildAndroidStreamConfig } from '@/lib/androidBridge';
import type { Channel, StreamConfig, SubChannel, AndroidStreamConfig } from '@/types/admin';
import type { PlayerType } from '@/types/admin';

const SETTINGS_ID = '__settings';

// Category icons
const sportIcon = (
  <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"></path></svg>
);

const movieIcon = (
  <svg viewBox="0 0 24 24"><path d="M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z"></path></svg>
);

const networkIcon = (
  <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93z"></path></svg>
);

const religionIcon = (
  <svg viewBox="0 0 24 24"><path d="M12 2C9.5 2 7.2 2.9 5.4 4.4c.5 0 1.1-.1 1.6-.1 5.5 0 10 4.5 10 10 0 1.9-.5 3.7-1.4 5.3 1.4-.4 2.6-1.1 3.7-2.1 2.8-2.6 3.1-6.9.7-9.8C18.2 4.6 15.3 2 12 2zm-2 9l-1 3-3 1 3 1 1 3 1-3 3-1-3-1-1-3z"></path></svg>
);

const settingsIcon = (
  <svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"></path></svg>
);

const searchIcon = (
  <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path>
);

// Map category names to icons
const iconMap: Record<string, React.ReactNode> = {
  sport: sportIcon,
  sports: sportIcon,
  movie: movieIcon,
  movies: movieIcon,
  network: networkIcon,
  networks: networkIcon,
  religion: religionIcon,
  settings: settingsIcon,
};

// Helper to generate slug from name
const nameToSlug = (name: string): string => {
  return name
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\w\-]/g, '')
    .replace(/--+/g, '-')
    .replace(/^-+|-+$/g, '');
};

const Index = () => {
  const { categories: fbCategories, sideMenus: fbSideMenus, loading: fbLoading, error: fbError } = useIptvData();
  const time = useClock();
  const navigate = useNavigate();

  const sortedCategories = useMemo(
    () => Object.values(fbCategories)
      .filter((cat) => !cat.hidden)
      .sort((a, b) => a.sortOrder - b.sortOrder),
    [fbCategories]
  );

  const [activeSectionId, setActiveSectionId] = useState<string>('');
  const initialCategorySet = useRef(false);
  const [activeSideMenuId, setActiveSideMenuId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showSearch, setShowSearch] = useState(false);
  const [startupLoading, setStartupLoading] = useState(true);
  const [contentLoading, setContentLoading] = useState(false);
  const [searchResults, setSearchResults] = useState<Array<Channel | SubChannel>>([]);
  const [showSearchResults, setShowSearchResults] = useState(false);

  // TV Navigation
  useTVNavigation({
    isSearchOpen: showSearch,
    onCloseSearch: () => {
      setShowSearch(false);
      setSearchQuery('');
      if (sortedCategories.length > 0) {
        setActiveSectionId(sortedCategories[0].id);
      }
    },
  });

  // Initial load - wait for data to be ready, then show content
  useEffect(() => {
    if (!fbLoading && sortedCategories.length > 0 && startupLoading) {
      // Data is loaded, hide startup loader
      setTimeout(() => {
        setStartupLoading(false);
        setTimeout(() => {
          const searchIconEl = document.getElementById('mainSearchIcon');
          if (searchIconEl) searchIconEl.focus();
        }, 500);
      }, 300);
    }
  }, [fbLoading, sortedCategories, startupLoading]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const channelId = params.get('channel');
    const menuId = params.get('menu');
    
    if (channelId || menuId) {
      window.history.replaceState({}, '', '/');
      if (menuId) {
        setActiveSideMenuId(menuId);
      }
    }
  }, []);

  // Set first category when data loads (only on initial load)
  useEffect(() => {
    if (sortedCategories.length > 0 && !initialCategorySet.current) {
      setActiveSectionId(sortedCategories[0].id);
      initialCategorySet.current = true;
    }
  }, [sortedCategories]);

  // Build nav items from remote categories (no settings in nav)
  const navItems = useMemo(() => {
    return sortedCategories.map((cat) => ({
      id: cat.id,
      name: cat.name.toUpperCase(),
      icon: iconMap[cat.name.toLowerCase()] || sportIcon,
    }));
  }, [sortedCategories]);

  // Handle section change with loader
  const handleSectionChange = useCallback((id: string) => {
    setContentLoading(true);
    setShowSearchResults(false);
    setActiveSideMenuId(null);

    setTimeout(() => {
      setActiveSectionId(id);
      setContentLoading(false);
    }, 500);
  }, []);

  // Get title for current section
  const getTitle = () => {
    if (showSearchResults) return `SEARCH RESULTS (${searchResults.length})`;
    if (activeSideMenuId) return fbSideMenus[activeSideMenuId]?.name?.toUpperCase() || 'SUBMENU';
    if (activeSectionId === SETTINGS_ID) return 'SETTINGS';
    return fbCategories[activeSectionId]?.name?.toUpperCase() || 'APiX';
  };

  // Build DRM string from config (supports all modes)
  const buildDrmString = useCallback((drm: StreamConfig['drm']): string | undefined => {
    if (!drm) return undefined;
    const mode = drm.clearKeyMode || 'separate';
    if (mode === 'combined' && drm.clearKeyCombined) return drm.clearKeyCombined;
    if (mode === 'url' && drm.clearKeyUrl) return drm.clearKeyUrl;
    if (drm.clearKeyId && drm.clearKeyKey) return `${drm.clearKeyId}:${drm.clearKeyKey}`;
    return undefined;
  }, []);

  // Open player
  const openFromStreamConfig = useCallback((
    webStream: StreamConfig | undefined, 
    title: string, 
    preferredPlayer?: PlayerType,
    androidConfig?: {
      url?: string;
      actionType?: 'native' | 'webview' | 'intent' | 'youtube' | 'shaka_web' | 'jw_web';
      headers?: { userAgent?: string; referrer?: string; cookie?: string; origin?: string; };
      intentUri?: string;
      drmLicenseUrl?: string;
      drmScheme?: 'widevine' | 'clearkey' | 'playready';
      drmKeyId?: string;
      drmKey?: string;
      servers?: Array<{ name: string; url: string }>;
      customHeaders?: Array<{ key: string; value: string }>;
      drmLicenseHeaders?: Array<{ key: string; value: string }>;
      backupUrl?: string;
      audioSources?: Array<{ name?: string; url: string }>;
      subtitleUrl?: string;
      dynamicApi?: {
        enabled: boolean;
        endpoint?: string;
        method?: 'GET' | 'POST';
        channelIdParam?: string;
        headers?: Record<string, string>;
      };
      forcedAspectRatio?: string;
      lockAspectRatio?: boolean;
      logoOverlay?: {
        url?: string;
        position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
        offsetX?: number;
        offsetY?: number;
        width?: number;
        height?: number;
        opacity?: number;
      };
    }
  ) => {
    if (isAndroidApp()) {
      const streamUrl = androidConfig?.url || webStream?.url;
      if (!streamUrl) {
        alert('لا يوجد رابط بث لهذه القناة.');
        return;
      }
      const streamData = buildAndroidStreamConfig(title, {
        url: streamUrl,
        actionType: androidConfig?.actionType || 'native',
        headers: androidConfig?.headers || {
          userAgent: webStream?.userAgent,
          referrer: webStream?.referrer,
          cookie: webStream?.cookies,
        },
        intentUri: androidConfig?.intentUri,
        drmLicenseUrl: androidConfig?.drmLicenseUrl,
        drmScheme: androidConfig?.drmScheme,
        drmKeyId: androidConfig?.drmKeyId,
        drmKey: androidConfig?.drmKey,
        servers: androidConfig?.servers,
        customHeaders: androidConfig?.customHeaders,
        drmLicenseHeaders: androidConfig?.drmLicenseHeaders,
        backupUrl: androidConfig?.backupUrl,
        audioSources: androidConfig?.audioSources,
        subtitleUrl: androidConfig?.subtitleUrl,
        dynamicApi: androidConfig?.dynamicApi,
        forcedAspectRatio: androidConfig?.forcedAspectRatio,
        lockAspectRatio: androidConfig?.lockAspectRatio,
        logoOverlay: androidConfig?.logoOverlay,
      });
      if (sendToAndroid(streamData)) return;
    }

    if (!webStream?.url) {
      alert('لا يوجد رابط بث لهذه القناة.');
      return;
    }

    const drm = buildDrmString(webStream.drm);
    const headers: Record<string, string> = {};
    if (webStream.userAgent) headers['User-Agent'] = webStream.userAgent;
    if (webStream.referrer) headers['Referer'] = webStream.referrer;
    if (webStream.cookies) headers['Cookie'] = webStream.cookies;
    const headersStr = Object.keys(headers).length ? JSON.stringify(headers) : undefined;

    if (window.openProPlayer) {
      window.openProPlayer(webStream.url, title, drm, headersStr, preferredPlayer);
    }
  }, [buildDrmString]);

  // Handle channel click
  const handleChannelClick = useCallback((item: Channel | SubChannel) => {
    const buildAndroidDrmConfig = (androidStream?: AndroidStreamConfig) => {
      if (!androidStream) return {};
      let drmKeyId = androidStream.drmKeyId;
      let drmKey = androidStream.drmKey;
      if (androidStream.drmClearKeyMode === 'combined' && androidStream.drmClearKeyCombined) {
        const parts = androidStream.drmClearKeyCombined.split(':');
        if (parts.length === 2) { drmKeyId = parts[0]; drmKey = parts[1]; }
      }
      return { drmLicenseUrl: androidStream.drmLicenseUrl, drmScheme: androidStream.drmScheme, drmKeyId, drmKey };
    };
    
    // Side menu items - navigate to sub-channel page
    if (activeSideMenuId || !('actionType' in item)) {
      const sc = item as SubChannel;
      const androidStream = sc.androidStream;
      const drmConfig = buildAndroidDrmConfig(androidStream);
      
      openFromStreamConfig(
        sc.stream, sc.name, sc.preferredPlayer,
        androidStream ? {
          url: androidStream.url,
          actionType: sc.androidActionType,
          headers: androidStream.headers,
          intentUri: androidStream.intentUri,
          servers: androidStream.servers,
          customHeaders: androidStream.customHeaders,
          drmLicenseHeaders: androidStream.drmLicenseHeaders,
          backupUrl: androidStream.backupUrl,
          audioSources: androidStream.audioSources,
          subtitleUrl: androidStream.subtitleUrl,
          dynamicApi: androidStream.dynamicApi,
          forcedAspectRatio: androidStream.forcedAspectRatio,
          lockAspectRatio: androidStream.lockAspectRatio,
          logoOverlay: androidStream.logoOverlay,
          ...drmConfig,
        } : undefined
      );
      return;
    }

    const ch = item as Channel;
    
    // Handle external link
    if (ch.actionType === 'external_link') {
      if (ch.externalUrl) { window.location.href = ch.externalUrl; }
      else { alert('لا يوجد رابط خارجي لهذه القناة.'); }
      return;
    }
    
    // Handle submenu - navigate to separate page
    if (ch.actionType === 'open_submenu') {
      if (!ch.sideMenuId || !fbSideMenus[ch.sideMenuId]) {
        alert('القائمة الفرعية غير موجودة.');
        return;
      }
      // Navigate to /channel-slug with submenu
      const slug = nameToSlug(ch.name);
      navigate(`/${slug}`);
      return;
    }

    // Default: direct play
    const androidStream = ch.androidStream;
    const drmConfig = buildAndroidDrmConfig(androidStream);
    
    openFromStreamConfig(
      ch.stream, ch.name, ch.preferredPlayer,
      androidStream ? {
        url: androidStream.url,
        actionType: ch.androidActionType,
        headers: androidStream.headers,
        intentUri: androidStream.intentUri,
        servers: androidStream.servers,
        customHeaders: androidStream.customHeaders,
        drmLicenseHeaders: androidStream.drmLicenseHeaders,
        backupUrl: androidStream.backupUrl,
        audioSources: androidStream.audioSources,
        subtitleUrl: androidStream.subtitleUrl,
        dynamicApi: androidStream.dynamicApi,
        forcedAspectRatio: androidStream.forcedAspectRatio,
        lockAspectRatio: androidStream.lockAspectRatio,
        logoOverlay: androidStream.logoOverlay,
        ...drmConfig,
      } : undefined
    );
  }, [activeSideMenuId, fbSideMenus, openFromStreamConfig, navigate]);

  // Get visible channels for current section
  const getVisibleChannels = useCallback((): Array<Channel | SubChannel> => {
    if (showSearchResults) return searchResults;
    if (activeSectionId === SETTINGS_ID) return [];

    if (activeSideMenuId) {
      return Object.values(fbSideMenus[activeSideMenuId]?.channels || {})
        .filter((ch) => !(ch as any).hidden)
        .sort((a, b) => a.sortOrder - b.sortOrder);
    }

    return Object.values(fbCategories[activeSectionId]?.channels || {})
      .filter((ch) => !ch.hidden)
      .sort((a, b) => a.sortOrder - b.sortOrder);
  }, [activeSectionId, activeSideMenuId, fbCategories, fbSideMenus, showSearchResults, searchResults]);

  // Search functionality
  const handleSearch = useCallback(() => {
    const filter = searchQuery.toLowerCase().trim();
    if (!filter) return;

    const results: Array<Channel | SubChannel> = [];
    Object.values(fbCategories).forEach((cat) => {
      Object.values(cat.channels || {}).forEach((channel) => {
        if (channel.name.toLowerCase().includes(filter)) results.push(channel);
      });
    });
    Object.values(fbSideMenus).forEach((menu) => {
      Object.values(menu.channels || {}).forEach((subChannel) => {
        if (subChannel.name.toLowerCase().includes(filter)) results.push(subChannel);
      });
    });

    setSearchResults(results);
    setShowSearchResults(true);
    setShowSearch(false);
    setActiveSideMenuId(null);
  }, [searchQuery, fbCategories, fbSideMenus]);

  const closeSearch = useCallback(() => {
    setShowSearch(false);
    setSearchQuery('');
    if (sortedCategories.length > 0) {
      setActiveSectionId(sortedCategories[0].id);
      setShowSearchResults(false);
    }
  }, [sortedCategories]);

  const channels = getVisibleChannels();

  return (
    <>
      {/* Startup Loader - stays until all data loads */}
      <Loader type="startup" visible={startupLoading} />

      {/* Mobile Header */}
      <div className="mobile-header">
        <svg fill="currentColor" width="24" height="24" viewBox="0 0 24 24" onClick={() => setShowSearch(true)}>
          {searchIcon}
        </svg>
        <div className="page-title">
          APi<span style={{ color: 'hsl(var(--gold))' }}>X</span>
        </div>
      </div>

      {/* Search Overlay */}
      <SearchOverlay
        visible={showSearch}
        value={searchQuery}
        onChange={setSearchQuery}
        onSearch={handleSearch}
        onClose={closeSearch}
      />

      {/* Sidebar (TV Mode) */}
      <Sidebar
        navItems={navItems}
        activeId={activeSectionId}
        onSelect={handleSectionChange}
      />

      {/* Main Content */}
      <div id="main-content">
        {/* Content Loader */}
        <Loader type="content" visible={contentLoading || fbLoading} />

        {/* TV Top Bar */}
        <div className="tv-top-bar">
          <div className="tv-left-part">
            <svg
              id="mainSearchIcon"
              className="tv-search-icon"
              tabIndex={0}
              viewBox="0 0 24 24"
              onClick={() => setShowSearch(true)}
              onKeyDown={(e) => { if (e.key === 'Enter') setShowSearch(true); }}
            >
              {searchIcon}
            </svg>
            <div className="time-box" id="topClock">{time}</div>
          </div>
          <div className="tv-section-title" id="tvTitle">{getTitle()}</div>
        </div>

        {/* Mobile Section Title */}
        <h2 className="mobile-section-title" id="mobileTitle">{getTitle()}</h2>

        {/* Error State */}
        {fbError && (
          <div style={{ textAlign: 'center', padding: '40px', color: 'hsl(var(--text-gray))' }}>
            <p>لم يتم تحميل البيانات</p>
            <p style={{ fontSize: '14px', marginTop: '10px' }}>{fbError}</p>
          </div>
        )}

        {/* Channels Grid */}
        {activeSectionId && activeSectionId !== SETTINGS_ID && !fbLoading && !contentLoading && (
          <div className="section active">
            {activeSideMenuId && (
              <button
                onClick={() => setActiveSideMenuId(null)}
                style={{
                  marginBottom: '20px',
                  padding: '10px 20px',
                  background: 'hsl(0 0% 15%)',
                  border: 'none',
                  borderRadius: '8px',
                  color: 'hsl(var(--foreground))',
                  cursor: 'pointer',
                }}
              >
                ← Back
              </button>
            )}
            <div className="channels-grid">
              {channels.map((item, idx) => (
                <ChannelCard
                  key={(item as any).id || idx}
                  name={(item as any).name}
                  imageUrl={(item as any).imageUrl}
                  onClick={() => handleChannelClick(item)}
                  tabIndex={0}
                />
              ))}
            </div>
            {channels.length === 0 && (
              <div style={{ textAlign: 'center', padding: '40px', color: 'hsl(var(--text-gray))' }}>
                <p>No channels available</p>
                <p style={{ fontSize: '14px', marginTop: '10px' }}>
                  Add channels from the admin panel at /admin-h
                </p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Bottom Navigation (Mobile) - no settings */}
      <BottomNav
        navItems={navItems.slice(0, 5)}
        activeId={activeSectionId}
        onSelect={handleSectionChange}
      />

      {/* Player Container */}
      <PlayerWrapper />
    </>
  );
};

export default Index;
