import React, { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Cloud, Upload, HardDrive, Volume2, Plus, Trash2, RefreshCw, Github, KeyRound, Rocket, Eye, EyeOff } from 'lucide-react';
import { adminDb } from '@/lib/adminDb';
import { toast } from 'sonner';
import { supabase } from '@/integrations/supabase/client';
import ServerSchemaExporter from './ServerSchemaExporter';

type Settings = {
  github_repo?: string;
  github_branch?: string;
  encrypted_file_url?: string;
  fetch_strategy?: 'local_first' | 'remote_first';
  rotation_days?: number;
};

type AudioSource = { name: string; url: string };
type SubChannelOption = { id: string; name: string; side_menu_id: string; android_stream?: any; menuName?: string };

const SETTINGS_KEY = 'hybrid_cloud_config';

const SystemSettingsManager: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [bulkCaching, setBulkCaching] = useState<null | 'on' | 'off'>(null);
  const [importing, setImporting] = useState(false);
  const [uploadingAudio, setUploadingAudio] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const [audioSources, setAudioSources] = useState<AudioSource[]>([{ name: '', url: '' }]);
  const [subChannelOptions, setSubChannelOptions] = useState<SubChannelOption[]>([]);
  const [selectedSubChannels, setSelectedSubChannels] = useState<string[]>([]);
  const [settings, setSettings] = useState<Settings>({
    fetch_strategy: 'local_first',
    rotation_days: 7,
    github_branch: 'main',
  });

  // ── Native / System config (dynamic key-fetch path + JWT signing secret) ──
  const CF_TOKEN_KEY = 'apix_cf_api_token';
  const [dynamicPath, setDynamicPath] = useState('');
  const [jwtSecret, setJwtSecret] = useState('');
  const [showJwt, setShowJwt] = useState(false);
  const [ghRepo, setGhRepo] = useState('');
  const [ghToken, setGhToken] = useState('');
  const [cfAccountId, setCfAccountId] = useState('');
  const [cfScript, setCfScript] = useState('apix-gateway');
  const [syncingGh, setSyncingGh] = useState(false);
  const [deployingCf, setDeployingCf] = useState(false);

  useEffect(() => {
    (async () => {
      const [sysRes, secRes, cfRes] = await Promise.all([
        supabase.from('system_settings').select('value').eq('key', 'system_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'security_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'cloudflare_config').maybeSingle(),
      ]);
      const sys = (sysRes.data?.value as any) || {};
      setDynamicPath(sys.dynamicPath || '');
      setJwtSecret(sys.jwtSecret || '');
      const sec = (secRes.data?.value as any) || {};
      setGhRepo(sec.githubRepo || '');
      setGhToken(sec.githubToken || '');
      const cf = (cfRes.data?.value as any) || {};
      setCfAccountId(cf.accountId || '');
      setCfScript(cf.scriptName || 'apix-gateway');
    })();
  }, []);

  const persistSystemConfig = async () => {
    await adminDb.upsert(
      'system_settings',
      { key: 'system_config', value: { dynamicPath: dynamicPath.trim(), jwtSecret: jwtSecret.trim() }, description: 'Dynamic key-fetch path + JWT signing secret' },
      true,
    );
  };

  const handleSyncGithub = async () => {
    const path = dynamicPath.trim();
    const secret = jwtSecret.trim();
    if (!path && !secret) { toast.error('أدخل المسار الديناميكي أو مفتاح JWT'); return; }
    if (!ghRepo || !ghToken) { toast.error('اضبط GitHub Repo + Token في قسم الحماية أولاً'); return; }
    setSyncingGh(true);
    try {
      await persistSystemConfig();
      const jobs: Array<Promise<void>> = [];
      const push = async (name: string, value: string) => {
        const { data, error } = await supabase.functions.invoke('update-github-secret', {
          body: { name, value, githubToken: ghToken, githubRepo: ghRepo },
        });
        if (error) throw new Error(error.message);
        if (data?.error) throw new Error(data.error);
      };
      if (path) jobs.push(push('DYNAMIC_API_PATH', path));
      if (secret) jobs.push(push('VIP_JWT_SECRET', secret));
      await Promise.all(jobs);
      toast.success('تم رفع المسار الديناميكي ومفتاح JWT إلى GitHub Secrets');
    } catch (e: any) {
      toast.error(`فشل المزامنة: ${e?.message ?? 'خطأ'}`);
    } finally {
      setSyncingGh(false);
    }
  };

  const handleDeployCloudflare = async () => {
    const apiToken = localStorage.getItem(CF_TOKEN_KEY) || '';
    if (!cfAccountId || !apiToken) { toast.error('اضبط Account ID و API Token في قسم Cloudflare أولاً'); return; }
    const secret = jwtSecret.trim();
    if (!secret) { toast.error('أدخل مفتاح JWT لحقنه في الوركر'); return; }
    setDeployingCf(true);
    try {
      await persistSystemConfig();
      const { data, error } = await supabase.functions.invoke('cloudflare-manager', {
        body: {
          action: 'update-secrets',
          accountId: cfAccountId.trim(),
          apiToken: apiToken.trim(),
          scriptName: (cfScript || 'apix-gateway').trim(),
          secrets: { VIP_JWT_SECRET: secret, DYNAMIC_API_PATH: dynamicPath.trim() },
        },
      });
      if (error) throw error;
      if (!data?.success) throw new Error(data?.error || 'فشل النشر');
      toast.success('تم حقن مفتاح JWT في الوركر ونشره');
    } catch (e: any) {
      toast.error(`فشل نشر الوركر: ${e?.message ?? 'خطأ'}`);
    } finally {
      setDeployingCf(false);
    }
  };

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', SETTINGS_KEY)
        .maybeSingle();
      if (data?.value) setSettings((s) => ({ ...s, ...(data.value as Settings) }));
      setLoading(false);
    })();
  }, []);

  useEffect(() => {
    (async () => {
      const [{ data: subs }, { data: menus }] = await Promise.all([
        supabase.from('sub_channels').select('id,name,side_menu_id,android_stream').order('sort_order'),
        supabase.from('side_menus').select('id,name'),
      ]);
      const menuMap = new Map((menus ?? []).map((m: any) => [m.id, m.name]));
      setSubChannelOptions((subs ?? []).map((s: any) => ({ ...s, menuName: menuMap.get(s.side_menu_id) || '' })));
    })();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      const { data, error } = await supabase.functions.invoke('save-system-settings', {
        body: { key: SETTINGS_KEY, value: settings, description: 'Hybrid Cloud Config' },
      });
      if (error) throw error;
      if (!data?.success) throw new Error(data?.error ?? 'فشل الحفظ');
      toast.success('تم حفظ الإعدادات');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setSaving(false);
    }
  };


  const handleBulkCache = async (enabled: boolean) => {
    const label = enabled ? 'تفعيل' : 'تعطيل';
    if (!confirm(`سيتم ${label} التخزين المحلي المشفّر لجميع القنوات والقنوات الفرعية. هل أنت متأكد؟`)) return;
    setBulkCaching(enabled ? 'on' : 'off');
    try {
      const [chRes, subRes] = await Promise.all([
        supabase.from('channels').select('id'),
        supabase.from('sub_channels').select('id'),
      ]);
      const chIds = (chRes.data ?? []).map((r) => r.id);
      const subIds = (subRes.data ?? []).map((r) => r.id);

      // Single bulk update per table using `.in()` (handled by admin-write)
      if (chIds.length > 0) {
        await adminDb.update('channels', { id: { in: chIds } }, { offline_cache_enabled: enabled }, true);
      }
      if (subIds.length > 0) {
        await adminDb.update('sub_channels', { id: { in: subIds } }, { offline_cache_enabled: enabled }, true);
      }

      // Trigger re-encrypt + push once at the end
      await adminDb.forceReencrypt().catch(() => null);
      toast.success(`تم ${label} التخزين المحلي لـ ${chIds.length} قناة و ${subIds.length} قناة فرعية`);
    } catch (e: any) {
      toast.error(`فشل العملية: ${e?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setBulkCaching(null);
    }
  };

  const handleImportFile = async (file: File) => {
    setImporting(true);
    try {
      const text = await file.text();
      const json = JSON.parse(text);
      const { data, error } = await supabase.functions.invoke('import-json', { body: json });
      if (error) throw error;
      if (!data?.success) throw new Error(data?.error ?? 'فشل الاستيراد');
      const c = data.counts;
      toast.success(
        `تم الاستيراد: ${c.categories} أقسام • ${c.sideMenus} قوائم • ${c.subChannels} أقسام فرعية • ${c.channels} قنوات`
      );
    } catch (e: any) {
      toast.error(`فشل الاستيراد: ${e?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setImporting(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const handleApplyAudioSources = async () => {
    const sources = audioSources.map((a) => ({ name: a.name.trim(), url: a.url.trim() })).filter((a) => a.name && a.url);
    if (sources.length === 0 || selectedSubChannels.length === 0) {
      toast.error('أضف مصدر صوت واحد على الأقل واختر القنوات الفرعية');
      return;
    }
    setUploadingAudio(true);
    try {
      for (const id of selectedSubChannels) {
        const target = subChannelOptions.find((s) => s.id === id);
        const currentStream = target?.android_stream && typeof target.android_stream === 'object' ? target.android_stream : {};
        const existing = Array.isArray(currentStream.audioSources) ? currentStream.audioSources : [];
        const merged = [...existing.filter((a: any) => !sources.some((s) => s.url === a?.url)), ...sources];
        await adminDb.update('sub_channels', { id }, { android_stream: { ...currentStream, audioSources: merged } }, true);
      }
      await adminDb.forceReencrypt().catch(() => null);
      toast.success(`تم رفع ${sources.length} مصدر صوت إلى ${selectedSubChannels.length} قناة فرعية`);
    } catch (e: any) {
      toast.error(`فشل رفع الأصوات: ${e?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setUploadingAudio(false);
    }
  };

  const handleResetCache = async () => {
    if (!confirm('سيتم مسح كاش القنوات القديم وإجبار جميع الأجهزة على إعادة الجلب من الكلاود الحالي. هل أنت متأكد؟')) return;
    try {
      // 1. Clear stale cloud config keys saved from old projects
      await adminDb.delete('system_settings', { key: 'legacy_cloud_url' }).catch(() => null);
      await adminDb.delete('system_settings', { key: 'old_cloud_config' }).catch(() => null);

      // 2. Bump cache_version on every channel/sub_channel so clients invalidate local cache
      const [chRes, subRes] = await Promise.all([
        supabase.from('channels').select('id,cache_version'),
        supabase.from('sub_channels').select('id,cache_version'),
      ]);
      for (const c of chRes.data ?? []) {
        await adminDb.update('channels', { id: c.id }, { cache_version: (c.cache_version ?? 1) + 1 }, true);
      }
      for (const s of subRes.data ?? []) {
        await adminDb.update('sub_channels', { id: s.id }, { cache_version: (s.cache_version ?? 1) + 1 }, true);
      }

      // 3. Mark a global cache reset timestamp the apps read on launch
      await adminDb.upsert('system_settings', {
        key: 'cache_reset',
        value: { resetAt: Date.now() },
        description: 'Force clients to flush local cache and refetch from current cloud',
      }, true);

      // 4. Re-encrypt + push so the new payload reaches GitHub immediately
      await adminDb.forceReencrypt().catch(() => null);

      toast.success('تم إعادة ضبط الكاش — ستجلب الأجهزة البيانات من الكلاود الحالي');
    } catch (e: any) {
      toast.error(`فشل إعادة الضبط: ${e?.message ?? 'خطأ'}`);
    }
  };

  if (loading) {
    return (
      <div className="bg-card rounded-2xl p-8 border border-border flex items-center justify-center">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Server schema (single + multi server) JSON export */}
      <ServerSchemaExporter />

      {/* System / Native config: dynamic key-fetch path + JWT signing secret */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <KeyRound className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">إعدادات النظام (المسار الديناميكي + مفتاح JWT)</h3>
            <p className="text-sm text-muted-foreground">
              يُحقن المسار ومفتاح التوقيع في الطبقة الأصلية عبر GitHub Secrets (CMakeLists/build.gradle) وفي الوركر عبر Cloudflare.
            </p>
          </div>
        </div>

        <div className="space-y-2">
          <Label>المسار الديناميكي لجلب مفتاح فك التشفير (API Path)</Label>
          <Input
            value={dynamicPath}
            onChange={(e) => setDynamicPath(e.target.value.trim())}
            placeholder="api-v2-secure"
            className="bg-secondary border-border font-mono text-xs"
            dir="ltr"
          />
        </div>

        <div className="space-y-2">
          <Label>JWT Secret (مفتاح توقيع الجلسات HS256)</Label>
          <div className="flex items-center gap-2">
            <Input
              type={showJwt ? 'text' : 'password'}
              value={jwtSecret}
              onChange={(e) => setJwtSecret(e.target.value.trim())}
              placeholder="مفتاح سري طويل"
              className="flex-1 bg-secondary border-border font-mono text-xs"
              dir="ltr"
            />
            <Button variant="outline" size="sm" onClick={() => setShowJwt((v) => !v)}>
              {showJwt ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap gap-3 pt-2">
          <Button onClick={handleSyncGithub} disabled={syncingGh} className="bg-primary text-primary-foreground hover:bg-primary/90">
            {syncingGh ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Github className="w-4 h-4 ml-2" />}
            مزامنة GitHub Secrets
          </Button>
          <Button onClick={handleDeployCloudflare} disabled={deployingCf} variant="outline">
            {deployingCf ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Rocket className="w-4 h-4 ml-2" />}
            نشر الوركر (Cloudflare)
          </Button>
        </div>
      </div>

      {/* Import JSON */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <Upload className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">استيراد ملف قنوات JSON</h3>
            <p className="text-sm text-muted-foreground">
              يحذف القنوات الحالية ويستبدلها من الملف فقط، مع تجاهل مفاتيح وإعدادات وتحديثات المشروع القديم.
            </p>
          </div>
        </div>
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) handleImportFile(f);
          }}
        />
        <Button
          onClick={() => fileRef.current?.click()}
          disabled={importing}
          variant="outline"
        >
          {importing ? (
            <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ الاستيراد...</>
          ) : (
            <><Upload className="w-4 h-4 mr-2" /> اختر ملف JSON واستورده</>
          )}
        </Button>
      </div>

      {/* Bulk offline cache toggle */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <HardDrive className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">فرض التخزين المحلي المشفّر لجميع القنوات</h3>
            <p className="text-sm text-muted-foreground">
              يفعّل (أو يعطّل) خاصية "Offline Cache" لكل القنوات والقنوات الفرعية دفعة واحدة، ثم يُعيد التشفير ويرفع الملف تلقائياً.
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button
            onClick={() => handleBulkCache(true)}
            disabled={bulkCaching !== null}
            className="bg-primary text-primary-foreground hover:bg-primary/90"
          >
            {bulkCaching === 'on' ? (
              <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ التفعيل...</>
            ) : (
              <><HardDrive className="w-4 h-4 mr-2" /> تفعيل لكل القنوات</>
            )}
          </Button>
          <Button
            onClick={() => handleBulkCache(false)}
            disabled={bulkCaching !== null}
            variant="outline"
          >
            {bulkCaching === 'off' ? (
              <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ التعطيل...</>
            ) : (
              <>تعطيل لكل القنوات</>
            )}
          </Button>
        </div>
      </div>

      {/* Reset cache */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-destructive/10 flex items-center justify-center">
            <RefreshCw className="w-5 h-5 text-destructive" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">إعادة ضبط الكاش</h3>
            <p className="text-sm text-muted-foreground">
              يمسح بيانات القنوات و Cloud URL القديمة المخزنة في الأجهزة، ويجبر التطبيقات على إعادة الجلب من الكلاود الحالي.
            </p>
          </div>
        </div>
        <Button onClick={handleResetCache} variant="destructive" className="w-full">
          <RefreshCw className="w-4 h-4 ml-2" /> إعادة ضبط الكاش الآن
        </Button>
      </div>

      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <Volume2 className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">نظام الأصوات الخارجية</h3>
            <p className="text-sm text-muted-foreground">أنشئ مجموعة أصوات ثم ارفعها دفعة واحدة إلى القنوات الفرعية المحددة.</p>
          </div>
        </div>
        <div className="space-y-2">
          {audioSources.map((audio, index) => (
            <div key={index} className="grid grid-cols-1 md:grid-cols-[180px_1fr_40px] gap-2 items-center">
              <Input value={audio.name} onChange={(e) => setAudioSources((list) => list.map((a, i) => i === index ? { ...a, name: e.target.value } : a))} placeholder="اسم المصدر" className="bg-secondary border-border" />
              <Input value={audio.url} onChange={(e) => setAudioSources((list) => list.map((a, i) => i === index ? { ...a, url: e.target.value } : a))} placeholder="https://audio.example.com/live.m3u8" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
              <Button type="button" variant="ghost" size="icon" onClick={() => setAudioSources((list) => list.filter((_, i) => i !== index).length ? list.filter((_, i) => i !== index) : [{ name: '', url: '' }])}>
                <Trash2 className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          ))}
          <Button type="button" variant="outline" size="sm" onClick={() => setAudioSources((list) => [...list, { name: '', url: '' }])}>
            <Plus className="w-4 h-4 ml-2" /> إضافة مصدر صوتي
          </Button>
        </div>
        <div className="space-y-2">
          <Label>القنوات الفرعية</Label>
          <div className="max-h-64 overflow-auto rounded-lg border border-border bg-secondary/40 p-2 space-y-1">
            {subChannelOptions.length === 0 ? <p className="text-sm text-muted-foreground p-3">لا توجد قنوات فرعية</p> : subChannelOptions.map((ch) => (
              <label key={ch.id} className="flex items-center gap-3 rounded-md px-3 py-2 hover:bg-background cursor-pointer">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-primary"
                  checked={selectedSubChannels.includes(ch.id)}
                  onChange={(e) => setSelectedSubChannels((ids) => e.target.checked ? [...ids, ch.id] : ids.filter((id) => id !== ch.id))}
                />
                <span className="text-sm text-foreground">{ch.name}</span>
                {ch.menuName && <span className="text-xs text-muted-foreground">{ch.menuName}</span>}
              </label>
            ))}
          </div>
        </div>
        <Button onClick={handleApplyAudioSources} disabled={uploadingAudio || selectedSubChannels.length === 0} className="w-full bg-primary text-primary-foreground hover:bg-primary/90">
          {uploadingAudio ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Upload className="w-4 h-4 ml-2" />}
          رفع الأصوات إلى القنوات المحددة
        </Button>
      </div>

      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <Cloud className="w-5 h-5 text-foreground" />
          <h3 className="text-lg font-bold text-foreground">سلوك التطبيق</h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>استراتيجية الجلب</Label>
            <select
              className="w-full bg-background border border-border rounded-md h-10 px-3 text-foreground"
              value={settings.fetch_strategy ?? 'local_first'}
              onChange={(e) =>
                setSettings({ ...settings, fetch_strategy: e.target.value as Settings['fetch_strategy'] })
              }
            >
              <option value="local_first">المفتاح المحلي أولاً (موصى به)</option>
              <option value="remote_first">جلب المفتاح من السحابة دائماً</option>
            </select>
          </div>
          <div className="space-y-2">
            <Label>دورة تدوير المفتاح (أيام)</Label>
            <Input
              type="number"
              min={1}
              value={settings.rotation_days ?? 7}
              onChange={(e) => setSettings({ ...settings, rotation_days: Number(e.target.value) })}
            />
          </div>
        </div>
      </div>

      <div className="flex justify-end">
        <Button onClick={handleSave} disabled={saving}
          className="bg-primary text-primary-foreground hover:bg-primary/90">
          {saving ? (
            <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ الحفظ...</>
          ) : 'حفظ الإعدادات'}
        </Button>
      </div>
    </div>
  );
};

export default SystemSettingsManager;
