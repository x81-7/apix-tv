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
    ar: 'هذا الملف يوضّح الشكل الذي يستقبل به المشغل السيرفرات. androidStream = الإعدادات الأساسية للسيرفر الواحد. fallbackServers = قائمة السيرفرات المتعددة التي ينتقل بينها المشغل تلقائياً عند الفشل. playerType لكل سيرفر يحدد محرك التشغيل (auto/exo/hybrid/shaka).',
    en: 'Describes how the player consumes servers. androidStream = the primary single-server config. fallbackServers = the ordered multi-server list the player auto-switches through on failure. Each fallback server may force its own engine via playerType (auto/exo/hybrid/shaka).',
  },
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

const ServerSchemaExporter: React.FC = () => {
  const [loadingSample, setLoadingSample] = useState(false);

  const handleDownloadTemplate = () => {
    downloadJson('apix-servers-schema.json', SCHEMA_TEMPLATE);
    toast.success('تم تنزيل قالب صيغة السيرفرات');
  };

  const handleDownloadLiveSample = async () => {
    setLoadingSample(true);
    try {
      const { data, error } = await supabase
        .from('channels')
        .select('id,name,image_url,action_type,android_stream,stream')
        .not('android_stream', 'is', null)
        .limit(3);
      if (error) throw error;
      const sample = {
        _readme: SCHEMA_TEMPLATE._readme,
        generatedAt: new Date().toISOString(),
        channels: (data ?? []).map((c: any) => ({
          id: c.id,
          name: c.name,
          imageUrl: c.image_url,
          actionType: c.action_type,
          androidStream: c.android_stream,
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
        <Button onClick={handleDownloadLiveSample} disabled={loadingSample} variant="outline">
          {loadingSample ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Download className="w-4 h-4 ml-2" />}
          تنزيل عيّنة حيّة من القنوات
        </Button>
      </div>
    </div>
  );
};

export default ServerSchemaExporter;
