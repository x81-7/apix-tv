import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  PlayIcon,
  PauseIcon,
  FullscreenIcon,
  FullscreenExitIcon,
  AudioIcon,
  QualityIcon,
  AspectRatioIcon,
  CloseIcon,
  BackIcon,
  ErrorIcon,
  CheckIcon,
} from './PlayerIcons';

declare const shaka: any;

declare global {
  interface Window {
    YT: any;
    onYouTubeIframeAPIReady: any;
    openProPlayer: (url: string, title: string, drm?: string, headers?: string, preferredPlayer?: string) => void;
  }
}

interface StreamData {
  url: string;
  title: string;
  drm?: string;
  headers?: string;
}

interface ProPlayerProps {
  stream: StreamData;
  onClose: () => void;
}

const CONTROL_BUTTON_CLASS = "control-btn";
const AUTO_HIDE_DELAY = 3000;

const ProPlayer: React.FC<ProPlayerProps> = ({ stream, onClose }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const ytContainerRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [player, setPlayer] = useState<any>(null);
  const [ytPlayer, setYtPlayer] = useState<any>(null);
  
  // UI State
  const [showHUD, setShowHUD] = useState(true);
  const [isBuffering, setIsBuffering] = useState(true);
  const [aspectRatio, setAspectRatio] = useState<'contain' | 'cover'>('contain');
  const [isYoutube, setIsYoutube] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [menu, setMenu] = useState<{ open: boolean; type: 'audio' | 'subtitle' | 'quality' }>({ open: false, type: 'quality' });
  const [isFullScreen, setIsFullScreen] = useState(false);

  // Stream Data State
  const [tracks, setTracks] = useState<{
    audio: any[];
    quality: any[];
    currentAudio: string;
    currentQuality: string;
  }>({ audio: [], quality: [], currentAudio: '', currentQuality: 'auto' });

  // Playback State
  const [controls, setControls] = useState({
    playing: true,
    progress: 0,
    currentTime: "00:00",
    totalTime: "LIVE",
    volume: 1
  });
  
  const timerRef = useRef<any>(null);
  const controlButtonsRef = useRef<HTMLButtonElement[]>([]);

  // Check for YouTube
  const isYoutubeUrl = (url: string) => url?.includes('youtube.com') || url?.includes('youtu.be');

  // Reset auto-hide timer
  const resetHUDTimer = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (!menu.open) {
      timerRef.current = setTimeout(() => {
        setShowHUD(false);
      }, AUTO_HIDE_DELAY);
    }
  }, [menu.open]);

  // Handle user activity (mouse move)
  const handleUserActivity = useCallback(() => {
    if (!showHUD) {
      setShowHUD(true);
    }
    resetHUDTimer();
  }, [resetHUDTimer, showHUD]);

  // Toggle controls visibility on click/tap - FIXED: Instant single tap toggle
  const handleContainerClick = useCallback((e: React.MouseEvent | React.TouchEvent) => {
    // Don't toggle if clicking on controls
    const target = e.target as HTMLElement;
    if (target.closest('button') || target.closest('input') || target.closest('.control-btn')) {
      return;
    }
    
    // Instant toggle - no delay
    setShowHUD(prev => {
      const newValue = !prev;
      if (newValue) {
        // If showing, start auto-hide timer
        if (timerRef.current) clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => {
          setShowHUD(false);
        }, AUTO_HIDE_DELAY);
      }
      return newValue;
    });
  }, []);

  // TV Remote Navigation for control buttons
  const handleKeyNavigation = useCallback((e: KeyboardEvent) => {
    const buttons = controlButtonsRef.current.filter(btn => btn !== null);
    if (buttons.length === 0) return;

    const currentIndex = buttons.findIndex(btn => btn === document.activeElement);

    switch (e.key) {
      case 'ArrowLeft':
        e.preventDefault();
        if (currentIndex > 0) {
          buttons[currentIndex - 1].focus();
        } else if (currentIndex === -1) {
          buttons[0].focus();
        }
        break;
      case 'ArrowRight':
        e.preventDefault();
        if (currentIndex < buttons.length - 1 && currentIndex !== -1) {
          buttons[currentIndex + 1].focus();
        } else if (currentIndex === -1) {
          buttons[0].focus();
        }
        break;
      case 'Enter':
      case ' ':
        if (document.activeElement?.classList.contains(CONTROL_BUTTON_CLASS)) {
          e.preventDefault();
          (document.activeElement as HTMLButtonElement).click();
        }
        break;
      case 'Escape':
        e.preventDefault();
        if (menu.open) {
          setMenu(m => ({ ...m, open: false }));
        } else {
          onClose();
        }
        break;
    }

    // Show controls when navigating
    handleUserActivity();
  }, [menu.open, onClose, handleUserActivity]);

  // Set up keyboard navigation
  useEffect(() => {
    document.addEventListener('keydown', handleKeyNavigation);
    return () => document.removeEventListener('keydown', handleKeyNavigation);
  }, [handleKeyNavigation]);

  // Initialize player
  useEffect(() => {
    if (isYoutubeUrl(stream.url)) {
      setIsYoutube(true);
      const videoId = stream.url.match(/(?:youtube\.com\/(?:live\/|watch\?v=)|youtu\.be\/)([a-zA-Z0-9_-]+)/)?.[1];
      if (videoId) initYoutube(videoId);
    } else {
      setIsYoutube(false);
      setTimeout(() => initShaka(), 100);
    }

    return () => {
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      }
      if (player) {
        player.destroy();
        setPlayer(null);
      }
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [stream.url]);

  // YouTube Initialization
  const initYoutube = (videoId: string) => {
    const startPlayer = () => {
      if (!window.YT || !window.YT.Player || !ytContainerRef.current) return;
      try {
        const ytp = new window.YT.Player(ytContainerRef.current, {
          videoId: videoId,
          width: '100%',
          height: '100%',
          playerVars: {
            autoplay: 1, controls: 0, modestbranding: 1, rel: 0,
            playsinline: 1, enablejsapi: 1, fs: 0, iv_load_policy: 3
          },
          events: {
            onReady: (e: any) => {
              setYtPlayer(e.target);
              setIsBuffering(false);
              setControls(c => ({ ...c, playing: true }));
            },
            onStateChange: (e: any) => {
              if (e.data === window.YT.PlayerState.BUFFERING) setIsBuffering(true);
              else if (e.data === window.YT.PlayerState.PLAYING) {
                setIsBuffering(false);
                setControls(c => ({ ...c, playing: true }));
              }
              else if (e.data === window.YT.PlayerState.PAUSED) setControls(c => ({ ...c, playing: false }));
            },
            onError: () => setError('Failed to load video.')
          }
        });
      } catch (err) { setError('YouTube player failed.'); }
    };

    if (window.YT && window.YT.Player) {
      startPlayer();
    } else {
      const tag = document.createElement('script');
      tag.src = 'https://www.youtube.com/iframe_api';
      document.body.appendChild(tag);
      window.onYouTubeIframeAPIReady = startPlayer;
    }
  };

  // Parse DRM config (supports combined format, URL, and complex keys)
  const parseDrmConfig = async (drmString: string): Promise<{ keyId: string; key: string } | null> => {
    if (!drmString) return null;

    // Check if it's a URL
    if (drmString.startsWith('http://') || drmString.startsWith('https://')) {
      try {
        const response = await fetch(drmString);
        const text = await response.text();
        // Parse response - expected format: KeyID:Key
        const [keyId, key] = text.trim().split(':');
        if (keyId && key) {
          return { keyId: cleanHexKey(keyId), key: cleanHexKey(key) };
        }
      } catch (e) {
        console.error('Failed to fetch DRM keys from URL:', e);
      }
      return null;
    }

    // It's a combined format KeyID:Key
    const colonIndex = drmString.indexOf(':');
    if (colonIndex > 0) {
      const keyId = drmString.substring(0, colonIndex);
      const key = drmString.substring(colonIndex + 1);
      
      // Check if key part is a URL (like "keyId:https://...")
      if (key.startsWith('http')) {
        try {
          const response = await fetch(key);
          const text = await response.text();
          const [fetchedKeyId, fetchedKey] = text.trim().split(':');
          if (fetchedKeyId && fetchedKey) {
            return { keyId: cleanHexKey(fetchedKeyId), key: cleanHexKey(fetchedKey) };
          }
        } catch (e) {
          console.error('Failed to fetch DRM keys from URL in key:', e);
        }
        return null;
      }
      
      if (keyId && key) {
        return { keyId: cleanHexKey(keyId), key: cleanHexKey(key) };
      }
    }

    return null;
  };

  // Clean hex key - remove non-hex characters and truncate if needed
  const cleanHexKey = (hex: string): string => {
    const cleaned = hex.replace(/[^a-fA-F0-9]/g, '');
    // Standard ClearKey uses 16-byte (32 hex chars) keys
    return cleaned.length > 32 ? cleaned.substring(0, 32) : cleaned;
  };

  // Shaka Player
  const initShaka = async () => {
    if (!videoRef.current) return;
    if (typeof shaka === 'undefined') {
      setError('Player library not loaded');
      return;
    }
    shaka.polyfill.installAll();
    if (!shaka.Player.isBrowserSupported()) {
      setError('Browser not supported');
      return;
    }
    const shakaPlayer = new shaka.Player(videoRef.current);
    
    shakaPlayer.configure({
      abr: { enabled: true },
      streaming: { bufferingGoal: 10, rebufferingGoal: 5, bufferBehind: 30 },
    });

    // DRM config - supports combined format and URL
    if (stream.drm) {
      const drmConfig = await parseDrmConfig(stream.drm);
      if (drmConfig) {
        shakaPlayer.configure('drm.clearKeys', { [drmConfig.keyId]: drmConfig.key });
      }
    }

    // Headers config
    if (stream.headers) {
      try {
        const headersObj = JSON.parse(stream.headers);
        shakaPlayer.getNetworkingEngine().registerRequestFilter((_type: any, request: any) => {
          Object.assign(request.headers, headersObj);
        });
      } catch (e) { console.error('Invalid headers format', e); }
    }

    shakaPlayer.addEventListener('trackschanged', () => updateShakaTracks(shakaPlayer));
    shakaPlayer.addEventListener('variantchanged', () => updateShakaTracks(shakaPlayer));
    shakaPlayer.addEventListener('buffering', (e: any) => setIsBuffering(e.buffering));
    shakaPlayer.addEventListener('error', (e: any) => { console.error(e); setError(`Error: ${e.detail.code}`); });

    setPlayer(shakaPlayer);

    try {
      await shakaPlayer.load(stream.url);
      videoRef.current.play();
      setIsBuffering(false);
    } catch (e: any) {
      console.error(e);
      setError(`Load Error: ${e.code}`);
      setIsBuffering(false);
    }
  };

  const updateShakaTracks = (p: any) => {
    const variants = p.getVariantTracks();
    const audioTracks = variants.reduce((acc: any[], v: any) => {
      if (!acc.find((a: any) => a.language === v.language)) acc.push({ language: v.language, label: v.label || v.language });
      return acc;
    }, []);
    const qualities = variants.filter((v: any, i: number, arr: any[]) => arr.findIndex((q: any) => q.height === v.height) === i).map((q: any) => ({ id: q.id, height: q.height, bandwidth: q.bandwidth })).sort((a: any, b: any) => b.height - a.height);
    const activeVariant = variants.find((v: any) => v.active);
    setTracks({ audio: audioTracks, quality: qualities, currentAudio: activeVariant?.language || '', currentQuality: p.getConfiguration().abr.enabled ? 'auto' : (activeVariant?.height?.toString() || 'auto') });
  };

  const changeAudio = (lang: string) => {
    if (!player) return;
    const variants = player.getVariantTracks();
    const target = variants.find((v: any) => v.language === lang);
    if (target) player.selectVariantTrack(target, true);
    setMenu(m => ({ ...m, open: false }));
    setTracks(t => ({ ...t, currentAudio: lang }));
  };

  const changeQuality = (height: number | 'auto') => {
    if (!player) return;
    if (height === 'auto') {
      player.configure('abr.enabled', true);
      setTracks(t => ({ ...t, currentQuality: 'auto' }));
    } else {
      player.configure('abr.enabled', false);
      const variants = player.getVariantTracks();
      const target = variants.find((v: any) => v.height === height);
      if (target) player.selectVariantTrack(target, true);
      setTracks(t => ({ ...t, currentQuality: height.toString() }));
    }
    setMenu(m => ({ ...m, open: false }));
  };

  const toggleFullScreen = async () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      await containerRef.current.requestFullscreen();
      if (screen.orientation && (screen.orientation as any).lock) {
        try { await (screen.orientation as any).lock('landscape'); } catch (e) { console.log(e); }
      }
      setIsFullScreen(true);
    } else {
      await document.exitFullscreen();
      if (screen.orientation && (screen.orientation as any).unlock) {
        try { (screen.orientation as any).unlock(); } catch (e) { console.log(e); }
      }
      setIsFullScreen(false);
    }
  };

  const togglePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isYoutube && ytPlayer) {
      controls.playing ? ytPlayer.pauseVideo() : ytPlayer.playVideo();
    } else if (videoRef.current) {
      videoRef.current.paused ? videoRef.current.play() : videoRef.current.pause();
    }
    setControls(c => ({ ...c, playing: !c.playing }));
    resetHUDTimer();
  };

  const formatTime = (s: number) => {
    if (isNaN(s) || s === Infinity) return "LIVE";
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
  };

  const handleClose = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (document.fullscreenElement) document.exitFullscreen();
    onClose();
  };

  // Register control button ref
  const registerButtonRef = (index: number) => (el: HTMLButtonElement | null) => {
    if (el) controlButtonsRef.current[index] = el;
  };

  return (
    <div
      ref={containerRef}
      className="fixed inset-0 z-[9999] bg-black flex items-center justify-center touch-manipulation"
      onClick={handleContainerClick}
      onTouchEnd={handleContainerClick}
      onMouseMove={handleUserActivity}
    >
      {/* Video Container - Centered */}
      <div className="relative w-full h-full flex items-center justify-center">
      {/* Buffering Spinner */}
      {isBuffering && (
        <div className="absolute inset-0 z-50 flex items-center justify-center pointer-events-none">
          <div className="w-16 h-16 border-4 border-white/20 border-t-[hsl(45,100%,50%)] rounded-full animate-spin" />
        </div>
      )}

      {/* Error State */}
      {error && (
        <div className="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/95 text-white p-4 text-center">
          <ErrorIcon className="text-red-600 mb-4" size={64} />
          <p className="text-xl font-bold mb-6">{error}</p>
          <button onClick={handleClose} className="px-8 py-3 bg-white/10 rounded-full hover:bg-white/20 border border-white/10 transition-colors">
            إغلاق
          </button>
        </div>
      )}

      {/* Video Layer */}
      {isYoutube ? (
        <div className={`w-full h-full pointer-events-none transition-transform duration-300 ${aspectRatio === 'cover' ? 'scale-[1.35]' : 'scale-100'}`}>
          <div ref={ytContainerRef} className="w-full h-full" />
          <div className="absolute inset-0 z-10" />
        </div>
      ) : (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          className={`w-full h-full transition-all duration-300 ${aspectRatio === 'contain' ? 'object-contain' : 'object-cover'}`}
          onTimeUpdate={() => {
            if (!videoRef.current) return;
            const v = videoRef.current;
            setControls(c => ({
              ...c,
              progress: (v.currentTime / v.duration) * 100 || 0,
              currentTime: formatTime(v.currentTime),
              totalTime: formatTime(v.duration)
            }));
          }}
          onPlay={() => setControls(c => ({ ...c, playing: true }))}
          onPause={() => setControls(c => ({ ...c, playing: false }))}
          onWaiting={() => setIsBuffering(true)}
          onPlaying={() => setIsBuffering(false)}
        />
      )}

      {/* HUD Overlay */}
      <div
        className={`absolute inset-0 z-20 flex flex-col justify-between p-4 md:p-8 transition-opacity duration-300 ${showHUD ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
        style={{ background: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.7) 100%)' }}
      >
        {/* Top Bar */}
        <div className="flex items-start justify-between" onClick={e => e.stopPropagation()}>
          <button 
            ref={registerButtonRef(0)}
            onClick={handleClose} 
            className={`${CONTROL_BUTTON_CLASS} player-btn group`}
            tabIndex={0}
          >
            <BackIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={28} />
          </button>
          <div className="flex flex-col items-end text-right">
            <h2 className="text-white text-lg md:text-2xl font-bold tracking-wide drop-shadow-lg">{stream.title}</h2>
            <div className="flex items-center gap-2 mt-1">
              <span className="w-2 h-2 bg-red-600 rounded-full animate-pulse live-indicator" />
              <span className="text-[10px] font-bold text-white/60 tracking-widest uppercase">
                {isYoutube ? 'YOUTUBE LIVE' : (tracks.currentQuality === 'auto' ? 'AUTO HD' : `${tracks.currentQuality}p`)}
              </span>
            </div>
          </div>
        </div>

        {/* Center Play Indicator */}
        <div className="flex-1 flex items-center justify-center pointer-events-none">
          {!controls.playing && (
            <div className="w-24 h-24 bg-black/60 backdrop-blur-sm rounded-full flex items-center justify-center border-2 border-white/10">
              <PlayIcon className="text-white ml-1" size={40} />
            </div>
          )}
        </div>

        {/* Bottom Controls */}
        <div className="space-y-4 pb-2 md:pb-6" onClick={e => e.stopPropagation()}>
          {/* Progress Bar */}
          <div className="flex flex-col gap-2 group">
            <div className="progress-track">
              <div className="progress-fill" style={{ width: `${controls.progress}%` }} />
              <input
                type="range"
                min="0"
                max="100"
                step="0.1"
                value={controls.progress}
                className="absolute inset-0 opacity-0 w-full h-full cursor-pointer"
                tabIndex={0}
                onChange={(e) => {
                  const val = parseFloat(e.target.value);
                  if (videoRef.current && videoRef.current.duration) {
                    videoRef.current.currentTime = (val / 100) * videoRef.current.duration;
                  }
                  setControls(c => ({ ...c, progress: val }));
                }}
              />
            </div>
            <div className="flex justify-between text-xs font-mono text-white/70">
              <span>{controls.currentTime}</span>
              <span>{controls.totalTime}</span>
            </div>
          </div>

          {/* Control Buttons Row */}
          <div className="flex justify-between items-center">
            {/* Left Group */}
            <div className="flex items-center gap-2 md:gap-4">
              <button 
                ref={registerButtonRef(1)}
                onClick={togglePlay} 
                className={`${CONTROL_BUTTON_CLASS} player-btn group`}
                tabIndex={0}
              >
                {controls.playing ?
                  <PauseIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={32} /> :
                  <PlayIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={32} />
                }
              </button>

              <div className="w-px h-8 bg-white/10 mx-1" />

              {/* Audio Button */}
              <button 
                ref={registerButtonRef(2)}
                onClick={() => setMenu({ open: true, type: 'audio' })} 
                className={`${CONTROL_BUTTON_CLASS} player-btn group flex flex-col items-center gap-1`}
                tabIndex={0}
              >
                <AudioIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={24} />
                <span className="control-label">AUDIO</span>
              </button>

              {/* Quality Button */}
              <button 
                ref={registerButtonRef(3)}
                onClick={() => setMenu({ open: true, type: 'quality' })} 
                className={`${CONTROL_BUTTON_CLASS} player-btn group flex flex-col items-center gap-1`}
                tabIndex={0}
              >
                <QualityIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={24} />
                <span className="control-label">QUALITY</span>
              </button>
            </div>

            {/* Right Group */}
            <div className="flex gap-2 md:gap-4 items-center">
              <button 
                ref={registerButtonRef(4)}
                onClick={() => setAspectRatio(aspectRatio === 'contain' ? 'cover' : 'contain')} 
                className={`${CONTROL_BUTTON_CLASS} player-btn group flex flex-col items-center gap-1`}
                tabIndex={0}
              >
                <AspectRatioIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={24} />
                <span className="control-label">{aspectRatio.toUpperCase()}</span>
              </button>

              {/* Fullscreen Button */}
              <button 
                ref={registerButtonRef(5)}
                onClick={toggleFullScreen} 
                className={`${CONTROL_BUTTON_CLASS} player-btn group`}
                tabIndex={0}
              >
                {isFullScreen ?
                  <FullscreenExitIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={32} /> :
                  <FullscreenIcon className="text-white group-hover:text-[hsl(45,100%,50%)]" size={32} />
                }
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Settings Menu Modal */}
      {menu.open && (
        <div
          className="absolute inset-0 z-30 flex items-end md:items-center justify-center bg-black/80 backdrop-blur-sm"
          onClick={() => setMenu(m => ({ ...m, open: false }))}
        >
          <div
            className="w-full max-w-sm bg-gradient-to-b from-neutral-900 to-black rounded-t-3xl md:rounded-3xl p-6 shadow-2xl border border-white/10"
            onClick={e => e.stopPropagation()}
          >
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-xl font-bold text-white">
                {menu.type === 'audio' ? 'Audio Track' : 'Quality'}
              </h3>
              <button 
                onClick={() => setMenu(m => ({ ...m, open: false }))} 
                className="player-btn p-2"
                tabIndex={0}
              >
                <CloseIcon className="text-white/60 hover:text-white" size={20} />
              </button>
            </div>

            <div className="space-y-2 max-h-80 overflow-y-auto">
              {menu.type === 'audio' ? (
                tracks.audio.map(a => (
                  <button
                    key={a.language}
                    onClick={() => changeAudio(a.language)}
                    tabIndex={0}
                    className={`w-full p-4 rounded-xl flex justify-between items-center font-bold transition-all ${
                      tracks.currentAudio === a.language
                        ? 'bg-[hsl(45,100%,50%)] text-black'
                        : 'bg-white/5 text-white hover:bg-white/10'
                    }`}
                  >
                    <span>{a.label || a.language}</span>
                    {tracks.currentAudio === a.language && <CheckIcon size={20} />}
                  </button>
                ))
              ) : (
                <>
                  <button
                    onClick={() => changeQuality('auto')}
                    tabIndex={0}
                    className={`w-full p-4 rounded-xl flex justify-between items-center font-bold transition-all ${
                      tracks.currentQuality === 'auto'
                        ? 'bg-[hsl(45,100%,50%)] text-black'
                        : 'bg-white/5 text-white hover:bg-white/10'
                    }`}
                  >
                    <span>Auto <span className="text-xs opacity-60">(Recommended)</span></span>
                    {tracks.currentQuality === 'auto' && <CheckIcon size={20} />}
                  </button>
                  {tracks.quality.map(q => (
                    <button
                      key={q.id}
                      onClick={() => changeQuality(q.height)}
                      tabIndex={0}
                      className={`w-full p-4 rounded-xl flex justify-between items-center font-bold transition-all ${
                        tracks.currentQuality === q.height.toString()
                          ? 'bg-[hsl(45,100%,50%)] text-black'
                          : 'bg-white/5 text-white hover:bg-white/10'
                      }`}
                    >
                      <span>{q.height}p <span className="text-xs opacity-60">({Math.round(q.bandwidth / 1000)} kbps)</span></span>
                      {tracks.currentQuality === q.height.toString() && <CheckIcon size={20} />}
                    </button>
                  ))}
                </>
              )}
            </div>
          </div>
        </div>
      )}
      </div>
    </div>
  );
};

export default ProPlayer;
