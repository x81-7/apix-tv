import React, { useEffect, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { toast } from 'sonner';
import { Film, Tv, Loader2, PlugZap, ShieldCheck } from 'lucide-react';

type AppMode = 'HYBRID' | 'CINEMA_ONLY' | 'SPORTS_ONLY';

interface CinemaConfig {
  configured: boolean;
  app_mode: AppMode;
  host?: string;
  port?: number | null;
  username?: string;
  has_password?: boolean;
  has_tmdb?: boolean;
  vod_enabled?: boolean;
  series_enabled?: boolean;
  live_enabled?: boolean;
  anime_enabled?: boolean;
  movie_link_template?: string | null;
  series_link_template?: string | null;
}

const call = async (action: string, extra: Record<string, unknown> = {}) => {
  const { data, error } = await supabase.functions.invoke('cinema-gateway', {
    body: { action, ...extra },
  });
  if (error) throw error;
  if (data && data.success === false) throw new Error(data.error || 'فشل الطلب');
  return data;
};

const CinemaManager: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const [appMode, setAppMode] = useState<AppMode>('HYBRID');
  const [host, setHost] = useState('');
  const [port, setPort] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [tmdbKey, setTmdbKey] = useState('');
  const [hasPassword, setHasPassword] = useState(false);
  const [hasTmdb, setHasTmdb] = useState(false);
  const [vodEnabled, setVodEnabled] = useState(true);
  const [seriesEnabled, setSeriesEnabled] = useState(true);
  const [liveEnabled, setLiveEnabled] = useState(true);
  const [animeEnabled, setAnimeEnabled] = useState(true);
  const [movieTpl, setMovieTpl] = useState('');
  const [seriesTpl, setSeriesTpl] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const cfg = (await call('get-config')) as CinemaConfig;
        setAppMode((cfg.app_mode as AppMode) || 'HYBRID');
        if (cfg.configured) {
          setHost(cfg.host ?? '');
          setPort(cfg.port != null ? String(cfg.port) : '');
          setUsername(cfg.username ?? '');
          setHasPassword(!!cfg.has_password);
          setHasTmdb(!!cfg.has_tmdb);
          setVodEnabled(cfg.vod_enabled ?? true);
          setSeriesEnabled(cfg.series_enabled ?? true);
          setLiveEnabled(cfg.live_enabled ?? true);
        }
      } catch (e) {
        console.error(e);
        toast.error('تعذر تحميل إعدادات السينما');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      await call('save-config', {
        host: host.trim(),
        port: port ? Number(port) : null,
        username: username.trim(),
        password: password || undefined, // blank keeps existing
        tmdb_api_key: tmdbKey || (hasTmdb ? undefined : ''),
        vod_enabled: vodEnabled,
        series_enabled: seriesEnabled,
        live_enabled: liveEnabled,
      });
      if (password) setHasPassword(true);
      if (tmdbKey) setHasTmdb(true);
      setPassword('');
      setTmdbKey('');
      toast.success('تم حفظ إعدادات السينما');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'فشل الحفظ');
    } finally {
      setSaving(false);
    }
  };

  const saveMode = async (mode: AppMode) => {
    setAppMode(mode);
    try {
      await call('set-app-mode', { mode });
      toast.success('تم تحديث وضع التطبيق');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'فشل تحديث الوضع');
    }
  };

  const test = async () => {
    setTesting(true);
    try {
      const res = await call('test');
      const info = res?.info?.user_info;
      if (info) {
        toast.success(`الاتصال ناجح — الحالة: ${info.status ?? 'active'}${info.exp_date ? ` · ينتهي: ${new Date(Number(info.exp_date) * 1000).toLocaleDateString()}` : ''}`);
      } else {
        toast.success('تم الاتصال');
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'فشل الاتصال بحساب IPTV');
    } finally {
      setTesting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="w-6 h-6 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* App mode */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-2">
          <Film className="w-5 h-5 text-primary" />
          <h3 className="text-lg font-bold text-foreground">وضع التطبيق (App Mode)</h3>
        </div>
        <p className="text-xs text-muted-foreground">
          يتحكم بالأقسام الظاهرة في التطبيق: هجين (أفلام + بث مباشر)، أفلام ومسلسلات فقط، أو بث مباشر فقط.
        </p>
        <Select value={appMode} onValueChange={(v) => saveMode(v as AppMode)}>
          <SelectTrigger className="max-w-sm"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="HYBRID">هجين — أفلام/مسلسلات + بث مباشر</SelectItem>
            <SelectItem value="CINEMA_ONLY">سينما فقط — أفلام ومسلسلات</SelectItem>
            <SelectItem value="SPORTS_ONLY">بث مباشر فقط</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* IPTV account */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-2">
          <Tv className="w-5 h-5 text-primary" />
          <h3 className="text-lg font-bold text-foreground">حساب IPTV (Xtream Codes)</h3>
        </div>
        <p className="text-xs text-muted-foreground">
          تُحفظ بيانات الحساب على الخادم فقط ولا تصل للتطبيق إطلاقاً — الخادم يجلب المحتوى نيابةً عن المستخدم.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="md:col-span-2">
            <Label className="text-foreground">المضيف (Host)</Label>
            <Input value={host} onChange={(e) => setHost(e.target.value)} placeholder="http://example.com أو example.com" />
          </div>
          <div>
            <Label className="text-foreground">المنفذ (Port)</Label>
            <Input value={port} onChange={(e) => setPort(e.target.value)} placeholder="8080" inputMode="numeric" />
          </div>
          <div>
            <Label className="text-foreground">اسم المستخدم</Label>
            <Input value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div>
            <Label className="text-foreground">كلمة المرور</Label>
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              placeholder={hasPassword ? '•••••••• (محفوظة)' : ''} />
          </div>
          <div>
            <Label className="text-foreground">مفتاح TMDB (اختياري)</Label>
            <Input type="password" value={tmdbKey} onChange={(e) => setTmdbKey(e.target.value)}
              placeholder={hasTmdb ? '•••••••• (محفوظ)' : 'لإثراء الصور والمعلومات'} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
          <div className="flex items-center justify-between rounded-xl border border-border px-3 py-2">
            <span className="text-sm text-foreground">الأفلام (VOD)</span>
            <Switch checked={vodEnabled} onCheckedChange={setVodEnabled} />
          </div>
          <div className="flex items-center justify-between rounded-xl border border-border px-3 py-2">
            <span className="text-sm text-foreground">المسلسلات</span>
            <Switch checked={seriesEnabled} onCheckedChange={setSeriesEnabled} />
          </div>
          <div className="flex items-center justify-between rounded-xl border border-border px-3 py-2">
            <span className="text-sm text-foreground">البث المباشر</span>
            <Switch checked={liveEnabled} onCheckedChange={setLiveEnabled} />
          </div>
        </div>

        <div className="flex flex-wrap gap-3 pt-2">
          <Button onClick={save} disabled={saving}>
            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <ShieldCheck className="w-4 h-4 mr-2" />}
            حفظ الإعدادات
          </Button>
          <Button variant="outline" onClick={test} disabled={testing}>
            {testing ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <PlugZap className="w-4 h-4 mr-2" />}
            اختبار الاتصال
          </Button>
        </div>
      </div>
    </div>
  );
};

export default CinemaManager;
