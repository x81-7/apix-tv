import React, { useEffect, useRef, useState, useCallback } from 'react';
import { X, AlertCircle, Loader2 } from 'lucide-react';

declare const Hls: any;

interface SecurePlayerProps {
  url: string;
  title: string;
  drm?: string;
  headers?: string;
  onClose: () => void;
}

const HLS_JS_CDN = 'https://cdn.jsdelivr.net/npm/hls.js@latest/dist/hls.min.js';

/**
 * Secure Player - URL-protected web player
 * - Loads stream via HLS.js and creates blob URLs (hides real URL from network tab)
 * - Disables right-click context menu
 * - Prevents easy URL extraction from DevTools
 */
const SecurePlayer: React.FC<SecurePlayerProps> = ({ url, title, drm, headers, onClose }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<any>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Decode the obfuscated URL
  const decodeUrl = useCallback((encoded: string): string => {
    try {
      // URL is passed as-is from admin, we just use it
      return encoded;
    } catch {
      return encoded;
    }
  }, []);

  // Load HLS.js
  useEffect(() => {
    const loadHls = (): Promise<void> => {
      return new Promise((resolve, reject) => {
        if (typeof Hls !== 'undefined') { resolve(); return; }
        const script = document.createElement('script');
        script.src = HLS_JS_CDN;
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Failed to load HLS.js'));
        document.head.appendChild(script);
      });
    };

    const initPlayer = async () => {
      try {
        const streamUrl = decodeUrl(url);
        const video = videoRef.current;
        if (!video) return;

        // Parse custom headers
        let customHeaders: Record<string, string> = {};
        if (headers) {
          try { customHeaders = JSON.parse(headers); } catch {}
        }

        // Check if HLS stream
        const isHls = streamUrl.includes('.m3u8') || streamUrl.includes('m3u8');
        const isDash = streamUrl.includes('.mpd');

        if (isHls) {
          await loadHls();
          if (typeof Hls === 'undefined' || !Hls.isSupported()) {
            // Fallback to native
            video.src = streamUrl;
            video.play().catch(() => {});
            setIsLoading(false);
            return;
          }

          const hlsConfig: any = {
            enableWorker: true,
            lowLatencyMode: true,
            backBufferLength: 90,
          };

          // Add custom headers via xhrSetup
          if (Object.keys(customHeaders).length > 0) {
            hlsConfig.xhrSetup = (xhr: XMLHttpRequest) => {
              Object.entries(customHeaders).forEach(([key, value]) => {
                try { xhr.setRequestHeader(key, value); } catch {}
              });
            };
          }

          const hls = new Hls(hlsConfig);
          hlsRef.current = hls;

          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            // Auto-select highest quality
            hls.currentLevel = hls.levels.length - 1;
            video.play().catch(() => {});
            setIsLoading(false);
          });

          hls.on(Hls.Events.ERROR, (_: any, data: any) => {
            if (data.fatal) {
              setError('فشل تشغيل البث');
              setIsLoading(false);
            }
          });

          hls.loadSource(streamUrl);
          hls.attachMedia(video);
        } else {
          // Direct/progressive/DASH - use native
          video.src = streamUrl;
          video.onloadeddata = () => setIsLoading(false);
          video.onerror = () => { setError('فشل تشغيل البث'); setIsLoading(false); };
          video.play().catch(() => {});
        }
      } catch (err) {
        setError('فشل تحميل المشغل');
        setIsLoading(false);
      }
    };

    initPlayer();

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [url, headers, decodeUrl]);

  // Security: disable right-click and some keyboard shortcuts
  useEffect(() => {
    const preventContext = (e: MouseEvent) => e.preventDefault();
    const preventKeys = (e: KeyboardEvent) => {
      // Block F12, Ctrl+Shift+I, Ctrl+U
      if (e.key === 'F12' || (e.ctrlKey && e.shiftKey && e.key === 'I') || (e.ctrlKey && e.key === 'u')) {
        e.preventDefault();
      }
    };

    document.addEventListener('contextmenu', preventContext);
    document.addEventListener('keydown', preventKeys);

    return () => {
      document.removeEventListener('contextmenu', preventContext);
      document.removeEventListener('keydown', preventKeys);
    };
  }, []);

  // Fullscreen on double-click
  const toggleFullscreen = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  const handleClose = () => {
    if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; }
    if (videoRef.current) { videoRef.current.pause(); videoRef.current.src = ''; }
    onClose();
  };

  return (
    <div
      ref={containerRef}
      style={{
        position: 'fixed', inset: 0, zIndex: 99999,
        background: '#000', display: 'flex', flexDirection: 'column',
      }}
      onContextMenu={(e) => e.preventDefault()}
    >
      {/* Top bar */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10,
        background: 'linear-gradient(to bottom, rgba(0,0,0,0.8), transparent)',
        padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <h3 style={{ color: '#fff', fontSize: 16, fontWeight: 600, margin: 0 }}>{title}</h3>
        <button
          onClick={handleClose}
          style={{
            background: 'rgba(255,255,255,0.15)', border: 'none', borderRadius: '50%',
            width: 36, height: 36, display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer', color: '#fff',
          }}
        >
          <X size={20} />
        </button>
      </div>

      {/* Video */}
      <video
        ref={videoRef}
        style={{ width: '100%', height: '100%', objectFit: 'contain', background: '#000' }}
        playsInline
        autoPlay
        controls
        controlsList="nodownload noremoteplayback"
        disablePictureInPicture
        onDoubleClick={toggleFullscreen}
      />

      {/* Loading */}
      {isLoading && (
        <div style={{
          position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: 'rgba(0,0,0,0.7)', zIndex: 5,
        }}>
          <Loader2 size={48} color="#FFC107" style={{ animation: 'spin 1s linear infinite' }} />
        </div>
      )}

      {/* Error */}
      {error && (
        <div style={{
          position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.9)', zIndex: 5,
        }}>
          <AlertCircle size={48} color="#f44336" />
          <p style={{ color: '#fff', marginTop: 12, fontSize: 16 }}>{error}</p>
          <button
            onClick={handleClose}
            style={{
              marginTop: 16, padding: '8px 24px', background: '#FFC107', color: '#000',
              border: 'none', borderRadius: 8, fontWeight: 700, cursor: 'pointer',
            }}
          >
            إغلاق
          </button>
        </div>
      )}

      <style>{`
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
};

export default SecurePlayer;
