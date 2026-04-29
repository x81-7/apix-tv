import React, { useEffect, useMemo, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import PlayerWrapper from '@/components/player/PlayerWrapper';
import { useIptvData } from '@/hooks/useIptvData';
import { isAndroidApp, sendToAndroid, buildAndroidStreamConfig } from '@/lib/androidBridge';
import type { Channel, SubChannel, StreamConfig, AndroidStreamConfig, PlayerType } from '@/types/admin';

/**
 * Sub-Channel Page - shows sub-menu channels in a dedicated page
 * URL format: /:slug (matched from channel name)
 * Also supports direct play: /:slug-player
 */
const ChannelPlayer: React.FC = () => {
  const params = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { categories, sideMenus, loading } = useIptvData();
  const [error, setError] = useState<string | null>(null);
  const [playerOpened, setPlayerOpened] = useState(false);

  const rawSlug = params.slug || '';
  const isPlayerMode = rawSlug.endsWith('-player');
  const slug = isPlayerMode ? rawSlug.replace(/-player$/, '') : rawSlug;

  const nameToSlug = (name: string): string => {
    return name
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^\w\-]/g, '')
      .replace(/--+/g, '-')
      .replace(/^-+|-+$/g, '');
  };

  const buildDrmString = (drm: StreamConfig['drm']): string | undefined => {
    if (!drm) return undefined;
    const mode = drm.clearKeyMode || 'separate';
    if (mode === 'combined' && drm.clearKeyCombined) return drm.clearKeyCombined;
    if (mode === 'url' && drm.clearKeyUrl) return drm.clearKeyUrl;
    if (drm.clearKeyId && drm.clearKeyKey) return `${drm.clearKeyId}:${drm.clearKeyKey}`;
    return undefined;
  };

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

  // Find the channel that has this slug and has a submenu
  const channelWithSubmenu = useMemo(() => {
    if (!slug || loading) return null;
    for (const category of Object.values(categories)) {
      for (const channel of Object.values(category.channels || {})) {
        if (nameToSlug(channel.name) === slug) {
          return channel;
        }
      }
    }
    // Also search side menu sub-channels
    for (const menu of Object.values(sideMenus)) {
      for (const subChannel of Object.values(menu.channels || {})) {
        if (nameToSlug(subChannel.name) === slug) {
          return subChannel;
        }
      }
    }
    return null;
  }, [slug, categories, sideMenus, loading]);

  // Get sub-channels if channel has a submenu
  const subChannels = useMemo(() => {
    if (!channelWithSubmenu) return [];
    const ch = channelWithSubmenu as Channel;
    if (ch.actionType === 'open_submenu' && ch.sideMenuId && sideMenus[ch.sideMenuId]) {
      return Object.values(sideMenus[ch.sideMenuId].channels || {})
        .filter((sc) => !sc.hidden)
        .sort((a, b) => a.sortOrder - b.sortOrder);
    }
    return [];
  }, [channelWithSubmenu, sideMenus]);

  // Open player for a channel/subchannel
  const openPlayer = useCallback((item: Channel | SubChannel) => {
    if (isAndroidApp()) {
      const androidStream = item.androidStream;
      const drmConfig = buildAndroidDrmConfig(androidStream);
      const streamUrl = androidStream?.url || (item as any).stream?.url;
      if (!streamUrl) { alert('لا يوجد رابط بث'); return; }
      
      const streamData = buildAndroidStreamConfig(item.name, {
        url: streamUrl,
        actionType: (item as any).androidActionType || 'native',
        headers: androidStream?.headers || {
          userAgent: (item as any).stream?.userAgent,
          referrer: (item as any).stream?.referrer,
          cookie: (item as any).stream?.cookies,
        },
        intentUri: androidStream?.intentUri,
        servers: androidStream?.servers,
        customHeaders: androidStream?.customHeaders,
        drmLicenseHeaders: androidStream?.drmLicenseHeaders,
        backupUrl: androidStream?.backupUrl,
        audioSources: androidStream?.audioSources,
        subtitleUrl: androidStream?.subtitleUrl,
        dynamicApi: androidStream?.dynamicApi,
        forcedAspectRatio: androidStream?.forcedAspectRatio,
        lockAspectRatio: androidStream?.lockAspectRatio,
        logoOverlay: androidStream?.logoOverlay,
        ...drmConfig,
      });
      if (sendToAndroid(streamData)) return;
    }

    const stream = (item as any).stream as StreamConfig | undefined;
    if (!stream?.url) { alert('لا يوجد رابط بث'); return; }

    const drm = buildDrmString(stream.drm);
    const headers: Record<string, string> = {};
    if (stream.userAgent) headers['User-Agent'] = stream.userAgent;
    if (stream.referrer) headers['Referer'] = stream.referrer;
    if (stream.cookies) headers['Cookie'] = stream.cookies;
    const headersStr = Object.keys(headers).length ? JSON.stringify(headers) : undefined;

    if ((window as any).openProPlayer) {
      (window as any).openProPlayer(stream.url, item.name, drm, headersStr, (item as any).preferredPlayer, (item as any).iosPlayerApp);
      setPlayerOpened(true);
    }
  }, []);

  // If player mode (/:slug-player), auto-play the channel
  useEffect(() => {
    if (loading || playerOpened || !isPlayerMode) return;
    if (channelWithSubmenu) {
      openPlayer(channelWithSubmenu as any);
    } else if (!loading) {
      setError('القناة غير موجودة');
    }
  }, [channelWithSubmenu, loading, playerOpened, isPlayerMode, openPlayer]);

  // If not player mode and no submenu, show error
  useEffect(() => {
    if (loading) return;
    if (!isPlayerMode && !channelWithSubmenu && !loading) {
      setError('الصفحة غير موجودة');
    }
  }, [loading, isPlayerMode, channelWithSubmenu]);

  // Make sub-channel page fully independent from main TV layout styles
  useEffect(() => {
    document.body.classList.add('subchannel-page-active');
    return () => {
      document.body.classList.remove('subchannel-page-active');
    };
  }, []);

  if (loading) {
    return (
      <div style={{
        position: 'fixed', inset: 0, background: '#000',
        display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 9999,
      }}>
        <div style={{
          width: 50, height: 50, border: '5px solid #333',
          borderTop: '5px solid #FFC107', borderRadius: '50%',
          animation: 'spin 0.8s linear infinite',
        }} />
      </div>
    );
  }

  if (error) {
    return (
      <div style={{
        position: 'fixed', inset: 0, background: '#000',
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        color: '#fff', padding: 20,
      }}>
        <p style={{ fontSize: 20, marginBottom: 16 }}>{error}</p>
        <button
          onClick={() => navigate('/')}
          style={{
            padding: '10px 24px', background: '#1a1a1a', border: '1px solid #333',
            borderRadius: 8, color: '#fff', cursor: 'pointer', fontSize: 16,
          }}
        >
          العودة للرئيسية
        </button>
      </div>
    );
  }

  // If player mode, show player
  if (isPlayerMode) {
    return <PlayerWrapper />;
  }

  // Sub-channel page - styled like the template
  const title = channelWithSubmenu ? (channelWithSubmenu as any).name : '';

  return (
    <div style={{
      background: '#000',
      position: 'fixed',
      inset: 0,
      width: '100vw',
      minHeight: '100vh',
      padding: 20,
      overflowY: 'auto',
      overflowX: 'hidden',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'stretch',
      boxSizing: 'border-box',
    }}>
      {/* Header */}
      <header style={{
        width: '100%', textAlign: 'center', marginBottom: 25, paddingTop: 10,
        animation: 'slideDown 0.6s ease-out',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
          <button
            onClick={() => navigate('/')}
            tabIndex={0}
            style={{
              background: 'none', border: 'none', color: '#fff', fontSize: 28,
              cursor: 'pointer', padding: '4px 8px',
            }}
          >
            ←
          </button>
          <h1 style={{
            color: '#fff', fontSize: 28, fontWeight: 800,
            textTransform: 'uppercase', letterSpacing: 2, margin: 0,
          }}>
            {title}
          </h1>
        </div>
      </header>

      {/* Channel Grid */}
      <div style={{
        width: '100%', maxWidth: '100%', display: 'grid',
        gap: 16, paddingBottom: 40, paddingLeft: 20, paddingRight: 20,
        animation: 'fadeIn 0.5s ease-in-out',
      }}
        className="subchannel-grid"
      >
        {subChannels.map((sc, idx) => (
          <a
            key={sc.id || idx}
            href={`/${nameToSlug(sc.name)}-player`}
            onClick={(e) => {
              e.preventDefault();
              openPlayer(sc);
            }}
            tabIndex={0}
            className="subchannel-card"
            style={{
              position: 'relative', backgroundColor: '#1a1a1a', borderRadius: 12,
              border: '3px solid transparent', aspectRatio: '16/9', overflow: 'hidden',
              display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
              textDecoration: 'none', transition: 'transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease',
              WebkitTapHighlightColor: 'transparent',
            }}
            onKeyDown={(e) => { if (e.key === 'Enter') openPlayer(sc); }}
          >
            <img
              src={sc.imageUrl || 'https://via.placeholder.com/300x170?text=TV'}
              alt={sc.name}
              style={{
                position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
                objectFit: 'cover', zIndex: 1, opacity: 0.8, transition: 'opacity 0.3s',
              }}
              onError={(e) => { (e.target as HTMLImageElement).src = 'https://via.placeholder.com/300x170?text=TV'; }}
            />
            <div style={{
              position: 'absolute', bottom: 0, left: 0, width: '100%', height: '70%',
              background: 'linear-gradient(to top, rgba(0,0,0,0.95), transparent)', zIndex: 2,
            }} />
            <div style={{ position: 'relative', zIndex: 3, padding: '8px 12px', textAlign: 'right', width: '100%' }}>
              <span className="subchannel-title" style={{
                color: '#fff', fontWeight: 700,
                textShadow: '0 2px 4px rgba(0,0,0,0.9)',
                display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
              }}>
                {sc.name}
              </span>
            </div>
          </a>
        ))}
      </div>

      {subChannels.length === 0 && !isPlayerMode && channelWithSubmenu && (
        <div style={{ textAlign: 'center', padding: 40, color: '#888' }}>
          <p>لا توجد قنوات فرعية</p>
        </div>
      )}

      {/* Player */}
      <PlayerWrapper />

      <style>{`
        @keyframes slideDown {
          from { transform: translateY(-20px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .subchannel-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
          width: 100%;
          max-width: none;
          gap: 16px;
        }
        .subchannel-title {
          font-size: 17px;
        }
        @media (orientation: landscape) {
          body.subchannel-page-active {
            padding-right: 0 !important;
            overflow: auto !important;
            flex-direction: column !important;
          }
          /* Smart sizing: calculate card width from viewport height so 2 rows fit on screen */
          /* Available height = 100vh - 100px (header+padding), each row = half, aspect 16:9 */
          .subchannel-grid {
            grid-template-columns: repeat(auto-fill, minmax(calc((100vh - 100px) / 2 * 16 / 9), 1fr)) !important;
            gap: 16px !important;
            max-width: none !important;
            width: 100% !important;
          }
          .subchannel-card {
            max-height: calc((100vh - 100px) / 2) !important;
          }
          .subchannel-title {
            font-size: clamp(14px, 2vh, 20px);
          }
        }
        .subchannel-card {
          aspect-ratio: 16/9 !important;
        }
        .subchannel-card:focus {
          border-color: #FFC107 !important;
          transform: scale(1.05);
          box-shadow: 0 0 25px rgba(255, 193, 7, 0.4);
          z-index: 10;
          outline: none;
        }
        .subchannel-card:focus img {
          opacity: 1 !important;
        }
      `}</style>
    </div>
  );
};

export default ChannelPlayer;
