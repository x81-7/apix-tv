/**
 * Android Bridge - Communication layer between Web App and Android Native Shell
 * Supports the split Web/Android architecture with full DRM support
 */

interface AndroidInterface {
  playVideo: (jsonConfig: string) => void;
  showToast: (message: string) => void;
  isAndroidApp: () => boolean;
  getAppVersion?: () => string;
  checkAdGate?: (categoryId: string) => void;
}

interface AndroidHeaders {
  'User-Agent'?: string;
  'Referer'?: string;
  'Cookie'?: string;
  'Origin'?: string;
}

interface AndroidDrmConfig {
  licenseUrl?: string;
  scheme?: 'widevine' | 'clearkey' | 'playready';
  keyId?: string;
  key?: string;
}

interface AndroidCustomHeader {
  key: string;
  value: string;
}

interface AndroidAudioSource {
  name?: string;
  url: string;
}

interface AndroidDynamicApi {
  enabled: boolean;
  endpoint?: string;
  method?: 'GET' | 'POST';
  channelIdParam?: string;
  headers?: Record<string, string>;
}

interface AndroidLogoOverlay {
  url?: string;
  position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
  offsetX?: number;
  offsetY?: number;
  width?: number;
  height?: number;
  opacity?: number;
}

interface AndroidStreamData {
  url: string;
  title: string;
  actionType?: 'native' | 'webview' | 'intent' | 'youtube' | 'shaka_web' | 'jw_web';
  webViewOrientation?: 'auto' | 'landscape' | 'portrait';
  headers?: AndroidHeaders;
  drm?: AndroidDrmConfig;
  intentUri?: string;
  servers?: Array<{ name: string; url: string }>;
  customHeaders?: Record<string, string>;
  drmLicenseHeaders?: Record<string, string>;
  backupUrl?: string;
  audioSources?: AndroidAudioSource[];
  subtitleUrl?: string;
  dynamicApi?: AndroidDynamicApi;
  forcedAspectRatio?: string;
  lockAspectRatio?: boolean;
  logoOverlay?: AndroidLogoOverlay;
}

declare global {
  interface Window {
    Android?: AndroidInterface;
  }
}

/**
 * Check if running inside Android WebView
 */
export function isAndroidApp(): boolean {
  return typeof window !== 'undefined' && 
         typeof window.Android !== 'undefined' && 
         typeof window.Android.isAndroidApp === 'function' &&
         window.Android.isAndroidApp();
}

/**
 * Get Android app version
 */
export function getAndroidVersion(): string | null {
  if (isAndroidApp() && window.Android?.getAppVersion) {
    try {
      return window.Android.getAppVersion();
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * Send stream data to Android native player
 * Falls back to web player if not in Android app
 */
export function sendToAndroid(streamData: AndroidStreamData): boolean {
  if (isAndroidApp() && window.Android) {
    try {
      const jsonConfig = JSON.stringify(streamData);
      console.log('[AndroidBridge] Sending to native player:', jsonConfig);
      window.Android.playVideo(jsonConfig);
      return true;
    } catch (error) {
      console.error('[AndroidBridge] Error sending to Android:', error);
      return false;
    }
  }
  return false;
}

/**
 * Show a toast message on Android
 */
export function showAndroidToast(message: string): boolean {
  if (isAndroidApp() && window.Android) {
    try {
      window.Android.showToast(message);
      return true;
    } catch (error) {
      console.error('[AndroidBridge] Error showing Android toast:', error);
      return false;
    }
  }
  return false;
}

/**
 * Build Android stream config from channel data
 * Supports full DRM configuration including ClearKey with keyId:key format
 */
export function buildAndroidStreamConfig(
  title: string,
  androidConfig: {
    url?: string;
    actionType?: 'native' | 'webview' | 'intent' | 'youtube' | 'shaka_web' | 'jw_web';
    webViewOrientation?: 'auto' | 'landscape' | 'portrait';
    headers?: {
      userAgent?: string;
      referrer?: string;
      cookie?: string;
      origin?: string;
    };
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
    dynamicApi?: AndroidDynamicApi;
    forcedAspectRatio?: string;
    lockAspectRatio?: boolean;
    logoOverlay?: AndroidLogoOverlay;
  }
): AndroidStreamData {
  const config: AndroidStreamData = {
    url: androidConfig.url || '',
    title,
    actionType: androidConfig.actionType || 'native',
  };

  if (androidConfig.webViewOrientation) {
    config.webViewOrientation = androidConfig.webViewOrientation;
  }

  // Add headers if any are provided
  if (androidConfig.headers) {
    config.headers = {};
    if (androidConfig.headers.userAgent) {
      config.headers['User-Agent'] = androidConfig.headers.userAgent;
    }
    if (androidConfig.headers.referrer) {
      config.headers['Referer'] = androidConfig.headers.referrer;
    }
    if (androidConfig.headers.cookie) {
      config.headers['Cookie'] = androidConfig.headers.cookie;
    }
    if (androidConfig.headers.origin) {
      config.headers['Origin'] = androidConfig.headers.origin;
    }
  }

  // Add DRM configuration if provided
  if (androidConfig.drmLicenseUrl || androidConfig.drmScheme || androidConfig.drmKeyId) {
    config.drm = {
      scheme: androidConfig.drmScheme || 'clearkey',
    };
    
    if (androidConfig.drmLicenseUrl) {
      config.drm.licenseUrl = androidConfig.drmLicenseUrl;
    }
    
    // Support ClearKey with keyId and key (or combined keyId:key format)
    if (androidConfig.drmKeyId) {
      // Check if it's a combined format (keyId:key)
      if (androidConfig.drmKeyId.includes(':') && !androidConfig.drmKey) {
        const [keyId, key] = androidConfig.drmKeyId.split(':');
        config.drm.keyId = keyId;
        config.drm.key = key;
      } else {
        config.drm.keyId = androidConfig.drmKeyId;
        if (androidConfig.drmKey) {
          config.drm.key = androidConfig.drmKey;
        }
      }
    }
  }

  // Add intent URI if provided (for external player apps)
  if (androidConfig.intentUri) {
    config.intentUri = androidConfig.intentUri;
  }

  // Add servers if provided
  if (androidConfig.servers && androidConfig.servers.length > 0) {
    config.servers = androidConfig.servers;
  }

  if (androidConfig.customHeaders?.length) {
    config.customHeaders = Object.fromEntries(
      androidConfig.customHeaders
        .filter((item) => item.key?.trim())
        .map((item) => [item.key, item.value ?? ''])
    );
  }

  if (androidConfig.drmLicenseHeaders?.length) {
    config.drmLicenseHeaders = Object.fromEntries(
      androidConfig.drmLicenseHeaders
        .filter((item) => item.key?.trim())
        .map((item) => [item.key, item.value ?? ''])
    );
  }

  if (androidConfig.backupUrl) config.backupUrl = androidConfig.backupUrl;
  if (androidConfig.audioSources?.length) config.audioSources = androidConfig.audioSources;
  if (androidConfig.subtitleUrl) config.subtitleUrl = androidConfig.subtitleUrl;
  if (androidConfig.dynamicApi) config.dynamicApi = androidConfig.dynamicApi;
  if (androidConfig.forcedAspectRatio) config.forcedAspectRatio = androidConfig.forcedAspectRatio;
  if (typeof androidConfig.lockAspectRatio === 'boolean') config.lockAspectRatio = androidConfig.lockAspectRatio;
  if (androidConfig.logoOverlay) config.logoOverlay = androidConfig.logoOverlay;

  return config;
}

/**
 * Play a channel using the appropriate method (Android native or web player)
 */
export function playChannel(
  title: string,
  webConfig: {
    url: string;
    playerType?: 'default' | 'custom' | 'iframe';
    drm?: string;
    headers?: string;
  },
  androidConfig?: {
    url?: string;
    actionType?: 'native' | 'webview' | 'intent' | 'youtube' | 'shaka_web' | 'jw_web';
    headers?: {
      userAgent?: string;
      referrer?: string;
      cookie?: string;
      origin?: string;
    };
    intentUri?: string;
    drmLicenseUrl?: string;
    drmScheme?: 'widevine' | 'clearkey' | 'playready';
    drmKeyId?: string;
    drmKey?: string;
    servers?: Array<{ name: string; url: string }>;
  }
): boolean {
  // If running in Android app and we have Android config, use native player
  if (isAndroidApp() && androidConfig?.url) {
    const streamData = buildAndroidStreamConfig(title, androidConfig);
    return sendToAndroid(streamData);
  }

  // Fallback to web player
  if (typeof (window as any).openProPlayer === 'function') {
    (window as any).openProPlayer(
      webConfig.url,
      title,
      webConfig.drm,
      webConfig.headers,
      webConfig.playerType
    );
    return true;
  }

  return false;
}