import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { FileJson, Download, Loader2 } from 'lucide-react';
import { supabase } from '@/integrations/supabase/client';
import { toast } from 'sonner';

/**
 * Exports a JSON template + documentation describing exactly how the players
 * (ExoPlayer / Hybrid / Shaka) receive a SINGLE server and MULTIPLE (fallback)
 * servers per channel. This lets the operator wire the app to EXTERNAL servers
 * by matching the documented shape.
 *
 * Two files are offered:
 *  1. Schema/template with inline field docs (always available).
 *  2. A live sample built from the first real channel in the database (best
 *     effort) so the operator sees a concrete, working example.
 */

const SCHEMA_TEMPLATE = {
  _readme: {
    ar: 'صيغة موحّدة لكل المنصّات (أندرويد/آيفون/ويندوز). المشغّل في كل منصّة يقرأ نفس الشكل: androidStream = إعدادات السيرفر الواحد، fallbackServers = قائمة السيرفرات المتعددة التي ينتقل بينها المشغّل تلقائياً عند الفشل. الآيفون والويندوز يفهمان نفس الحقول (iosStream/windowsStream اختياريان، وإن غابا يُستخدم androidStream). playerType لكل سيرفر يحدد محرك التشغيل (auto/exo/hybrid/shaka).',
    en: 'Unified shape for ALL platforms (Android/iOS/Windows). Every platform reads the same structure: androidStream = the primary single-server config, fallbackServers = the ordered multi-server list auto-switched on failure. iOS and Windows understand the same fields (iosStream/windowsStream are optional and fall back to androidStream). Each fallback server may force its own engine via playerType (auto/exo/hybrid/shaka).',
  },
  platforms: ['android', 'ios', 'windows'],
  channel: {
    id: 'unique-channel-id',
    name: 'اسم القناة',
    imageUrl: 'https://cdn.example.com/logo.png',
    actionType: 'direct_play',
    // === SINGLE SERVER (primary) ===
    androidStream: {
      url: 'https://server1.example.com/live/stream.m3u8',
      headers: {
        userAgent: 'Optional-User-Agent',
        referrer: 'https://referer.example.com',
        origin: 'https://origin.example.com',
        cookie: 'name=value',
      },
      customHeaders: [{ key: 'X-Custom', value: 'value' }],
      // DRM (optional): scheme = widevine | clearkey | playready
      drmScheme: 'clearkey',
      drmLicenseUrl: 'https://license.example.com',
      drmKeyId: 'HEX_KEY_ID',
      drmKey: 'HEX_KEY',
      drmClearKeyCombined: 'KID:KEY',
      drmClearKeyMode: 'combined',
      drmLicenseHeaders: [{ key: 'Authorization', value: 'Bearer ...' }],
      // === MULTIPLE SERVERS (auto-failover, ordered) ===
      fallbackServers: [
        {
          id: 'srv-2',
          name: 'سيرفر بديل 1',
          url: 'https://server2.example.com/live/stream.m3u8',
          // playerType: auto = same engine as primary, otherwise force one
          playerType: 'auto',
          userAgent: '',
          referer: '',
          origin: '',
          cookie: '',
          customHeaders: [],
          drmScheme: '',
          drmLicenseUrl: '',
          drmKeyId: '',
          drmKey: '',
          drmClearKeyCombined: '',
          drmClearKeyMode: 'combined',
          drmLicenseHeaders: [],
        },
        {
          id: 'srv-3',
          name: 'سيرفر بديل 2 (Shaka)',
          url: 'https://server3.example.com/manifest.mpd',
          playerType: 'shaka',
        },
      ],
    },
  },
  fieldDocs: {
    'androidStream.url': 'رابط البث الأساسي (m3u8 / mpd / mp4).',
    'androidStream.headers': 'الهيدرات القياسية: userAgent, referrer, origin, cookie.',
    'androidStream.customHeaders': 'هيدرات إضافية بصيغة [{key,value}].',
    'androidStream.drmScheme': 'نوع الحماية: widevine | clearkey | playready (اتركه فارغاً لبث بدون حماية).',
    'androidStream.fallbackServers': 'قائمة مرتّبة من السيرفرات البديلة. ينتقل المشغل للتالي عند الفشل.',
    'fallbackServers[].playerType': 'محرك التشغيل لهذا السيرفر: auto | exo | hybrid | shaka.',
  },
} as const;

function downloadJson(filename: string, data: unknown) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

// Deep-link payload the Android player receives from an EXTERNAL server for a
// SINGLE server (multiServer disabled).
const SINGLE_SERVER_DEEPLINK = {
  _readme: {
    ar: 'نموذج الرابط الخارجي (Deep Link) عند وجود سيرفر واحد. multiServer=false والمشغل يشغّل server مباشرة.',
    en: 'External deep-link payload for a SINGLE server. multiServer=false; the player uses the single `server` directly.',
  },
  version: 1,
  channelId: 'external-channel-id',
  name: 'اسم القناة',
  multiServer: false,
  playerType: 'auto',
  server: {
    url: 'https://server1.example.com/live/stream.m3u8',
    userAgent: '',
    referer: '',
    origin: '',
    cookie: '',
    customHeaders: [{ key: 'X-Custom', value: 'value' }],
    drmScheme: '',
    drmLicenseUrl: '',
    drmClearKeyCombined: 'KID:KEY',
  },
} as const;

// Deep-link payload for MULTIPLE servers (multiServer enabled → the player
// shows the "multi-server" switch button and auto-fails-over between them).
const MULTI_SERVER_DEEPLINK = {
  _readme: {
    ar: 'نموذج الرابط الخارجي عند وجود سيرفرات متعددة. multiServer=true فيظهر زر "تعدد السيرفرات" في المشغل، وينتقل تلقائياً للسيرفر التالي عند الفشل حسب الترتيب.',
    en: 'External deep-link payload for MULTIPLE servers. multiServer=true enables the multi-server switch in the player and auto-failover in order.',
  },
  version: 1,
  channelId: 'external-channel-id',
  name: 'اسم القناة',
  multiServer: true,
  defaultIndex: 0,
  servers: [
    {
      id: 'srv-1',
      name: 'سيرفر 1',
      url: 'https://server1.example.com/live/stream.m3u8',
      playerType: 'auto',
      userAgent: '',
      referer: '',
      customHeaders: [],
    },
    {
      id: 'srv-2',
      name: 'سيرفر 2 (بديل)',
      url: 'https://server2.example.com/live/stream.m3u8',
      playerType: 'exo',
    },
    {
      id: 'srv-3',
      name: 'سيرفر 3 (Shaka/DASH)',
      url: 'https://server3.example.com/manifest.mpd',
      playerType: 'shaka',
      drmScheme: 'clearkey',
      drmClearKeyCombined: 'KID:KEY',
    },
  ],
} as const;

const ServerSchemaExporter: React.FC = () => {
  const [loadingSample, setLoadingSample] = useState(false);

  const handleDownloadTemplate = () => {
    downloadJson('apix-servers-schema.json', SCHEMA_TEMPLATE);
    toast.success('تم تنزيل قالب صيغة السيرفرات');
  };

  const handleDownloadSingle = () => {
    downloadJson('apix-deeplink-single-server.json', SINGLE_SERVER_DEEPLINK);
    toast.success('تم تنزيل نموذج السيرفر الواحد');
  };

  const handleDownloadMulti = () => {
    downloadJson('apix-deeplink-multi-server.json', MULTI_SERVER_DEEPLINK);
    toast.success('تم تنزيل نموذج السيرفرات المتعددة');
  };

  const handleDownloadLiveSample = async () => {
    setLoadingSample(true);
    try {
      const { data, error } = await supabase
        .from('channels')
        .select('id,name,image_url,action_type,android_stream,ios_stream,windows_stream,web_stream,stream')
        .not('android_stream', 'is', null)
        .limit(3);
      if (error) throw error;
      const sample = {
        _readme: SCHEMA_TEMPLATE._readme,
        platforms: SCHEMA_TEMPLATE.platforms,
        generatedAt: new Date().toISOString(),
        channels: (data ?? []).map((c: any) => ({
          id: c.id,
          name: c.name,
          imageUrl: c.image_url,
          actionType: c.action_type,
          // Unified shape: iOS/Windows fall back to androidStream when their
          // own per-platform stream is not set.
          androidStream: c.android_stream,
          iosStream: c.ios_stream ?? c.android_stream,
          windowsStream: c.windows_stream ?? c.web_stream ?? c.android_stream,
        })),
      };
      downloadJson('apix-servers-live-sample.json', sample);
      toast.success(`تم تنزيل عيّنة حيّة من ${(data ?? []).length} قناة`);
    } catch (e: any) {
      toast.error(`فشل توليد العيّنة: ${e?.message ?? 'خطأ'}`);
    } finally {
      setLoadingSample(false);
    }
  };

  return (
    <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
          <FileJson className="w-5 h-5 text-primary" />
        </div>
        <div>
          <h3 className="text-lg font-bold text-foreground">صيغة السيرفرات (JSON) للربط الخارجي</h3>
          <p className="text-sm text-muted-foreground">
            نزّل ملف JSON يوضّح كيف يستقبل المشغل السيرفر المنفرد والسيرفرات المتعددة (مع نوع المشغل لكل سيرفر)،
            لتربط التطبيق بسيرفرات خارجية بنفس الصيغة.
          </p>
        </div>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button onClick={handleDownloadTemplate} className="bg-primary text-primary-foreground hover:bg-primary/90">
          <Download className="w-4 h-4 ml-2" /> تنزيل القالب + الشرح
        </Button>
        <Button onClick={handleDownloadSingle} variant="outline">
          <Download className="w-4 h-4 ml-2" /> نموذج سيرفر واحد (Deep Link)
        </Button>
        <Button onClick={handleDownloadMulti} variant="outline">
          <Download className="w-4 h-4 ml-2" /> نموذج سيرفرات متعددة (Deep Link)
        </Button>
        <Button onClick={handleDownloadLiveSample} disabled={loadingSample} variant="outline">
          {loadingSample ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Download className="w-4 h-4 ml-2" />}
          تنزيل عيّنة حيّة من القنوات
        </Button>
      </div>
    </div>
  );
};

export default ServerSchemaExporter;
