export type ActionType = 'direct_play' | 'open_submenu' | 'external_link';

// Web-specific player types
export type WebPlayerType = 'default' | 'custom' | 'iframe' | 'secure' | 'external_ios';
export type IOSPlayerType = 'native' | 'webview';

// iOS external player apps
export type iOSPlayerApp = 'vlc' | 'outplayer' | 'infuse' | 'kmplayer';

export interface iOSPlayerConfig {
  app: iOSPlayerApp;
  fallbackToWeb?: boolean;
}

// Android-specific action types
export type AndroidActionType = 'native' | 'webview' | 'intent' | 'youtube' | 'shaka_web' | 'jw_web';

// DRM schemes for Android
export type DrmScheme = 'widevine' | 'clearkey' | 'playready';

export type ClearKeyMode = 'separate' | 'combined' | 'url';

// Aspect ratio modes for forced display
export type AspectRatioMode = 'original' | 'fit' | 'stretch' | '16:9' | '4:3';

export type WebViewOrientationMode = 'auto' | 'landscape' | 'portrait';

export interface DRMConfig {
  clearKeyId?: string;
  clearKeyKey?: string;
  clearKeyCombined?: string;
  clearKeyUrl?: string;
  clearKeyMode?: ClearKeyMode;
}

// Headers configuration (shared structure)
export interface StreamHeaders {
  userAgent?: string;
  referrer?: string;
  cookie?: string;
  origin?: string;
}

// Custom header entry (Key:Value pair)
export interface CustomHeader {
  key: string;
  value: string;
}

// External audio source
export interface AudioSource {
  name: string;
  url: string;
}

// Logo overlay configuration
export interface LogoOverlay {
  url: string;
  position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
  offsetX?: number;
  offsetY?: number;
  width?: number;
  height?: number;
  opacity?: number;
}

// Dynamic API fetching configuration
export interface DynamicApiConfig {
  enabled: boolean;
  endpoint: string;
  method: 'GET' | 'POST';
  channelIdParam?: string;
  headers?: Record<string, string>;
}

export interface StreamConfig {
  url: string;
  userAgent?: string;
  referrer?: string;
  cookies?: string;
  origin?: string;
  customHeaders?: CustomHeader[];
  drmLicenseHeaders?: CustomHeader[];
  backupUrl?: string;
  audioSources?: AudioSource[];
  subtitleUrl?: string;
  dynamicApi?: DynamicApiConfig;
  forcedAspectRatio?: AspectRatioMode;
  lockAspectRatio?: boolean;
  logoOverlay?: LogoOverlay;
  drm?: DRMConfig;
}

// Web-specific stream configuration
export interface WebStreamConfig {
  url: string;
  headers?: StreamHeaders;
  drm?: DRMConfig;
}

// Android-specific stream configuration
export interface AndroidStreamConfig {
  url: string;
  webViewOrientation?: WebViewOrientationMode;
  headers?: StreamHeaders;
  customHeaders?: CustomHeader[];
  intentUri?: string;
  drmLicenseUrl?: string;
  drmScheme?: DrmScheme;
  drmKeyId?: string;
  drmKey?: string;
  drmClearKeyCombined?: string;
  drmClearKeyMode?: ClearKeyMode;
  drmLicenseHeaders?: CustomHeader[];
  servers?: Array<{ name: string; url: string }>;
  backupUrl?: string;
  audioSources?: AudioSource[];
  subtitleUrl?: string;
  dynamicApi?: DynamicApiConfig;
  forcedAspectRatio?: AspectRatioMode;
  lockAspectRatio?: boolean;
  logoOverlay?: LogoOverlay;
}

export interface SubChannel {
  id: string;
  name: string;
  imageUrl: string;
  stream: StreamConfig;
  sortOrder: number;
  preferredPlayer?: WebPlayerType;
  iosPlayerApp?: iOSPlayerApp;
  hidden?: boolean;
  
  // Android-specific
  androidStream?: AndroidStreamConfig;
  androidActionType?: AndroidActionType;
}

export interface SideMenu {
  id: string;
  name: string;
  sortOrder?: number;
  channels: Record<string, SubChannel>;
}

export interface Channel {
  id: string;
  name: string;
  imageUrl: string;
  sortOrder: number;
  actionType: ActionType;
  hidden?: boolean;
  
  // === Web Settings ===
  stream?: StreamConfig;
  sideMenuId?: string;
  externalUrl?: string;
  preferredPlayer?: WebPlayerType;
  iosPlayerType?: IOSPlayerType;
  iosPlayerApp?: iOSPlayerApp;
  
  // === Android Settings ===
  androidStream?: AndroidStreamConfig;
  androidActionType?: AndroidActionType;
}

export interface Category {
  id: string;
  name: string;
  sortOrder: number;
  channels: Record<string, Channel>;
  hidden?: boolean;
  adGateEnabled?: boolean;
}

// AdMob configuration
export interface AdConfig {
  adProvider?: 'admob' | 'applovin';
  rewardedAdUnitId?: string;
  admobRewardedId?: string;
  adsEnabled?: boolean;
  gateMode?: 'app_open_once' | 'unlock_channel';
  premiumCategoryIds?: string[];
  lockedChannelIds?: string[];
}

export interface CustomAd {
  id: string;
  name: string;
  video_url: string;
  sort_order: number;
  hidden?: boolean;
}

export interface AdminData {
  categories: Record<string, Category>;
  sideMenus: Record<string, SideMenu>;
}

// Legacy type alias for backward compatibility
export type PlayerType = WebPlayerType;
