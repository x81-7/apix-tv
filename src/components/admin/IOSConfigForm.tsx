import React from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tv, Globe, Shield, Smartphone } from 'lucide-react';

// iOS player choice — kept narrow to what RootView actually supports.
export type IOSPlayerType = 'native' | 'webview';

export interface IOSStreamConfig {
  url?: string;
  playerType?: IOSPlayerType;
  // Optional headers — applied via AVURLAssetHTTPHeaderFieldsKey for native, or as request headers in WebView.
  userAgent?: string;
  referrer?: string;
  origin?: string;
  cookie?: string;
  // DRM (only WebView/Shaka path supports clearkey).
  drmClearKeyId?: string;
  drmClearKey?: string;
  // External player launch app (mirrors WebConfigForm.iosPlayerApp). Optional.
  externalApp?: 'none' | 'vlc' | 'outplayer' | 'infuse' | 'kmplayer';
}

interface Props {
  config: IOSStreamConfig;
  onChange: (next: IOSStreamConfig) => void;
}

const IOSConfigForm: React.FC<Props> = ({ config, onChange }) => {
  const set = <K extends keyof IOSStreamConfig>(k: K, v: IOSStreamConfig[K]) => onChange({ ...config, [k]: v });

  return (
    <div className="space-y-4 rounded-lg border border-border bg-secondary p-4">
      <div className="flex items-center gap-2">
        <Tv className="w-4 h-4 text-primary" />
        <h4 className="font-semibold text-foreground">إعدادات نظام iPhone / iOS</h4>
      </div>

      <div className="space-y-2">
        <Label className="flex items-center gap-2"><Globe className="w-3 h-3" /> رابط البث للايفون</Label>
        <Input
          value={config.url || ''}
          onChange={(e) => set('url', e.target.value)}
          placeholder="https://example.com/stream.m3u8 أو .mp4 أو .mpd"
          className="bg-background border-border font-mono text-xs"
          dir="ltr"
        />
        <p className="text-xs text-muted-foreground">
          إذا تركته فارغاً سيستخدم التطبيق رابط الويب الافتراضي.
        </p>
      </div>

      <div className="space-y-2">
        <Label>نوع المشغل على iOS</Label>
        <Select value={config.playerType || 'native'} onValueChange={(v) => set('playerType', v as IOSPlayerType)}>
          <SelectTrigger className="bg-background border-border"><SelectValue /></SelectTrigger>
          <SelectContent className="bg-popover border-border z-50">
            <SelectItem value="native">Native AVPlayer (HLS / MP4)</SelectItem>
            <SelectItem value="webview">WebView (Shaka — للـ MPD / DRM)</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label className="flex items-center gap-2"><Smartphone className="w-3 h-3" /> فتح في تطبيق خارجي (اختياري)</Label>
        <Select value={config.externalApp || 'none'} onValueChange={(v) => set('externalApp', v as any)}>
          <SelectTrigger className="bg-background border-border"><SelectValue /></SelectTrigger>
          <SelectContent className="bg-popover border-border z-50">
            <SelectItem value="none">— لا أحد (تشغيل داخل التطبيق) —</SelectItem>
            <SelectItem value="vlc">VLC for Mobile</SelectItem>
            <SelectItem value="outplayer">OutPlayer</SelectItem>
            <SelectItem value="infuse">Infuse 7</SelectItem>
            <SelectItem value="kmplayer">KMPlayer</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <details className="rounded-md border border-border p-2">
        <summary className="cursor-pointer text-sm text-muted-foreground">ترويسات HTTP (اختياري)</summary>
        <div className="grid grid-cols-1 gap-2 pt-2">
          <div>
            <Label className="text-xs">User-Agent</Label>
            <Input value={config.userAgent || ''} onChange={(e) => set('userAgent', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
          </div>
          <div>
            <Label className="text-xs">Referer</Label>
            <Input value={config.referrer || ''} onChange={(e) => set('referrer', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
          </div>
          <div>
            <Label className="text-xs">Origin</Label>
            <Input value={config.origin || ''} onChange={(e) => set('origin', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
          </div>
          <div>
            <Label className="text-xs">Cookie</Label>
            <Input value={config.cookie || ''} onChange={(e) => set('cookie', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
          </div>
        </div>
      </details>

      {config.playerType === 'webview' && (
        <details className="rounded-md border border-border p-2">
          <summary className="cursor-pointer text-sm text-muted-foreground flex items-center gap-2"><Shield className="w-3 h-3" /> ClearKey DRM (WebView فقط)</summary>
          <div className="grid grid-cols-2 gap-2 pt-2">
            <div>
              <Label className="text-xs">Key ID</Label>
              <Input value={config.drmClearKeyId || ''} onChange={(e) => set('drmClearKeyId', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
            </div>
            <div>
              <Label className="text-xs">Key</Label>
              <Input value={config.drmClearKey || ''} onChange={(e) => set('drmClearKey', e.target.value)} className="bg-background border-border font-mono text-xs" dir="ltr" />
            </div>
          </div>
        </details>
      )}
    </div>
  );
};

export default IOSConfigForm;
