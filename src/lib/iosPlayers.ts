import type { iOSPlayerApp } from '@/types/admin';

interface PlayerInfo {
  name: string;
  scheme: (url: string) => string;
  appStoreUrl: string;
  icon: string;
}

export const IOS_PLAYERS: Record<iOSPlayerApp, PlayerInfo> = {
  vlc: {
    name: 'VLC',
    scheme: (url) => `vlc://${url}`,
    appStoreUrl: 'https://apps.apple.com/app/vlc-media-player/id650377962',
    icon: '🎬',
  },
  outplayer: {
    name: 'Outplayer',
    scheme: (url) => `outplayer://${url}`,
    appStoreUrl: 'https://apps.apple.com/app/outplayer/id1449923287',
    icon: '📺',
  },
  infuse: {
    name: 'Infuse',
    scheme: (url) => `infuse://x-callback-url/play?url=${encodeURIComponent(url)}`,
    appStoreUrl: 'https://apps.apple.com/app/infuse-7/id1136220934',
    icon: '🎥',
  },
  kmplayer: {
    name: 'KMPlayer',
    scheme: (url) => `kmplayer://${url}`,
    appStoreUrl: 'https://apps.apple.com/app/kmplayer-play-videos-music/id835843824',
    icon: '🎞️',
  },
};

export const isIOS = (): boolean => {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
};

export const launchExternalPlayer = (app: iOSPlayerApp, streamUrl: string): void => {
  const player = IOS_PLAYERS[app];
  if (!player) return;

  const schemeUrl = player.scheme(streamUrl);
  
  // Try to open the app
  const iframe = document.createElement('iframe');
  iframe.style.display = 'none';
  iframe.src = schemeUrl;
  document.body.appendChild(iframe);

  // Fallback: if app doesn't open in 2.5s, redirect to App Store
  setTimeout(() => {
    document.body.removeChild(iframe);
  }, 3000);
};
