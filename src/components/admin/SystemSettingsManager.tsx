import React, { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Cloud, Upload, HardDrive, Volume2, Plus, Trash2 } from 'lucide-react';
import { adminDb } from '@/lib/adminDb';
import { toast } from 'sonner';
import { supabase } from '@/integrations/supabase/client';

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

  if (loading) {
    return (
      <div className="bg-card rounded-2xl p-8 border border-border flex items-center justify-center">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Import JSON */}
      <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <Upload className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-foreground">استيراد ملف JSON كامل</h3>
            <p className="text-sm text-muted-foreground">
              يحذف البيانات الحالية ويستبدلها بالكامل من الملف (الأقسام، القوائم الجانبية، الأقسام الفرعية، القنوات، والإعدادات).
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
