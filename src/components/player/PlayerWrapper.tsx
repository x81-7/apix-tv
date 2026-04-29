import React, { useState, useEffect } from 'react';
import ProPlayer from './ProPlayer';
import CustomPlayer from './CustomPlayer';
import IframePlayer from './IframePlayer';
import SecurePlayer from './SecurePlayer';
import ExternalPlayerLauncher from './ExternalPlayerLauncher';
import type { PlayerType, iOSPlayerApp } from '@/types/admin';

interface StreamData {
  url: string;
  title: string;
  drm?: string;
  headers?: string;
  preferredPlayer?: PlayerType;
  iosPlayerApp?: iOSPlayerApp;
}

const PlayerWrapper: React.FC = () => {
  const [stream, setStream] = useState<StreamData | null>(null);
  const [fallbackToWeb, setFallbackToWeb] = useState(false);

  useEffect(() => {
    // Expose global function
    (window as any).openProPlayer = (url: string, title: string, drm?: string, headers?: string, preferredPlayer?: PlayerType, iosPlayerApp?: iOSPlayerApp) => {
      setStream({ url, title, drm, headers, preferredPlayer, iosPlayerApp });
      setFallbackToWeb(false);
      history.pushState({ player: true }, '');
    };

    // Listen for custom event
    const handleOpen = (e: CustomEvent<StreamData>) => {
      setStream(e.detail);
      setFallbackToWeb(false);
    };
    window.addEventListener('open-player', handleOpen as EventListener);
    
    return () => {
      window.removeEventListener('open-player', handleOpen as EventListener);
    };
  }, []);

  if (!stream) return null;

  const handleClose = () => { setStream(null); setFallbackToWeb(false); };

  // External iOS player
  if (stream.preferredPlayer === 'external_ios' && stream.iosPlayerApp && !fallbackToWeb) {
    return (
      <ExternalPlayerLauncher
        url={stream.url}
        title={stream.title}
        playerApp={stream.iosPlayerApp}
        onClose={handleClose}
        onFallbackToWeb={() => setFallbackToWeb(true)}
      />
    );
  }

  // Secure player (or fallback from external)
  if (stream.preferredPlayer === 'secure' || fallbackToWeb) {
    return (
      <SecurePlayer
        url={stream.url}
        title={stream.title}
        drm={stream.drm}
        headers={stream.headers}
        onClose={handleClose}
      />
    );
  }

  // Custom (JWPlayer)
  if (stream.preferredPlayer === 'custom') {
    return (
      <CustomPlayer
        url={stream.url}
        title={stream.title}
        drm={stream.drm}
        onClose={handleClose}
      />
    );
  }

  // Iframe
  if (stream.preferredPlayer === 'iframe') {
    return (
      <IframePlayer
        url={stream.url}
        title={stream.title}
        onClose={handleClose}
      />
    );
  }

  // Default player
  return <ProPlayer stream={stream} onClose={handleClose} />;
};

export default PlayerWrapper;
