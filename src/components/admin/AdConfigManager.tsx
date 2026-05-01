import React, { useState, useEffect } from 'react';
import { z } from 'zod';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Trash2, Plus, Gift, Video, KeyRound, Globe } from 'lucide-react';
import { toast } from 'sonner';

interface AdConfig {
  adProvider?: 'admob' | 'applovin';
  rewardedAdUnitId?: string;
  admobRewardedId?: string;
  adsEnabled?: boolean;
  gateMode?: 'app_open_once' | 'unlock_channel';
  forceExternal?: boolean;
}

interface LocalAdsConfig {
  trigger?: 'off' | 'app_open' | 'on_channel' | 'both';
  channelIds?: string[];
  forceExternal?: boolean;
}

interface WebAdsConfig {
  enabled?: boolean;
  url?: string;
  /** apply only on external link redirects */
  externalOnly?: boolean;
  /** seconds until skip becomes available */
  skipAfter?: number;
  /** Telegram / contact URL for VIP activation */
  sellerContactUrl?: string;
}

interface CustomAdRow {
  id: string;
  name: string;
  video_url: string;
  sort_order: number;
  hidden: boolean;
}

const adSchema = z.object({
  name: z.string().trim().min(1, 'اسم الإعلان مطلوب').max(120),
  videoUrl: z.string().trim().url('رابط الفيديو غير صالح').max(1000),
});

const forcedCountSchema = z.coerce.number().int().min(1).max(10);
const appIdSchema = z.string().trim().regex(/^ca-app-pub-\d{16}~\d{10}$/, 'صيغة AdMob App ID غير صحيحة');

const AdConfigManager: React.FC = () => {
  const [adConfig, setAdConfig] = useState<AdConfig>({});
  const [saving, setSaving] = useState(false);
  const [customAds, setCustomAds] = useState<CustomAdRow[]>([]);
  const [loadingAds, setLoadingAds] = useState(true);
  const [savingCustom, setSavingCustom] = useState(false);
  const [forcedAdsCount, setForcedAdsCount] = useState('1');
  const [savingForced, setSavingForced] = useState(false);
  const [newAd, setNewAd] = useState({ name: '', videoUrl: '' });
  const [admobAppId, setAdmobAppId] = useState('');
  const [savingAppId, setSavingAppId] = useState(false);
  const [localAds, setLocalAds] = useState<LocalAdsConfig>({ trigger: 'app_open', channelIds: [] });
  const [savingLocal, setSavingLocal] = useState(false);
  const [allChannels, setAllChannels] = useState<{ id: string; name: string }[]>([]);
  const [ghToken, setGhToken] = useState('');
  const [ghRepo, setGhRepo] = useState('');
  const [webAds, setWebAds] = useState<WebAdsConfig>({ enabled: false, externalOnly: true, skipAfter: 5, url: '', sellerContactUrl: '' });
  const [savingWebAds, setSavingWebAds] = useState(false);

  useEffect(() => {
    (async () => {
      const [configRes, countRes, adsRes, appIdRes, localRes, chanRes, secRes, webRes] = await Promise.all([
        supabase.from('system_settings').select('value').eq('key', 'adConfig').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'forced_custom_ads_count').maybeSingle(),
        supabase.from('custom_ads').select('*').order('sort_order').order('created_at'),
        supabase.from('system_settings').select('value').eq('key', 'admob_app_id_display').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'local_ads_config').maybeSingle(),
        supabase.from('channels').select('id,name').order('sort_order'),
        supabase.from('system_settings').select('value').eq('key', 'security_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'web_ads_config').maybeSingle(),
      ]);
      if (configRes.data?.value) setAdConfig(configRes.data.value as AdConfig);
      if (countRes.data?.value != null) setForcedAdsCount(String(countRes.data.value));
      if (appIdRes.data?.value) setAdmobAppId(String(appIdRes.data.value));
      if (localRes.data?.value) setLocalAds(localRes.data.value as LocalAdsConfig);
      if (chanRes.data) setAllChannels(chanRes.data);
      const sec = (secRes.data?.value as any) ?? {};
      setGhToken(String(sec.githubToken ?? ''));
      setGhRepo(String(sec.githubRepo ?? ''));
      if (webRes.data?.value) setWebAds({ ...webAds, ...(webRes.data.value as WebAdsConfig) });
      setCustomAds(adsRes.data ?? []);
      setLoadingAds(false);
    })();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      await adminDb.upsert('system_settings', { key: 'adConfig', value: adConfig, description: 'Ad Configuration' });
      toast.success('تم حفظ إعدادات إعلانات المكافأة');
    } catch {
      toast.error('فشل حفظ إعدادات الإعلانات');
    }
    setSaving(false);
  };

  const handleSaveAppId = async () => {
    const parsed = appIdSchema.safeParse(admobAppId);
    if (!parsed.success) {
      toast.error(parsed.error.issues[0]?.message ?? 'صيغة غير صحيحة');
      return;
    }
    if (!ghToken || !ghRepo) {
      toast.error('أدخل GitHub Token و Repository في قسم الحماية أولاً');
      return;
    }
    setSavingAppId(true);
    try {
      // Bake App ID directly into AndroidManifest.xml + build.gradle on GitHub.
      // No secret is used — the value is written into source so it cannot be lost.
      const { data, error } = await supabase.functions.invoke('update-android-config', {
        body: { admobAppId: parsed.data, githubToken: ghToken, githubRepo: ghRepo },
      });
      if (error) throw new Error(error.message ?? 'فشل الاتصال بالسيرفر');
      if (data?.error) throw new Error(data.error);
      if (!data?.ok) throw new Error('استجابة غير متوقعة من GitHub');
      await adminDb.upsert('system_settings', {
        key: 'admob_app_id_display',
        value: parsed.data,
        description: 'AdMob App ID (display copy; baked into AndroidManifest.xml in repo)',
      }, true);
      toast.success('تم تعديل AndroidManifest.xml و build.gradle مباشرة. سيُستخدم في البناء التالي.');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setSavingAppId(false);
    }
  };

  const saveLocalAdsConfig = async () => {
    setSavingLocal(true);
    try {
      await adminDb.upsert('system_settings', {
        key: 'local_ads_config',
        value: localAds,
        description: 'Local custom ads behavior (trigger + channel scope)',
      }, true);
      toast.success('تم حفظ إعدادات الإعلانات المحلية');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message ?? 'خطأ'}`);
    } finally {
      setSavingLocal(false);
    }
  };

  const toggleChannelScope = (id: string) => {
    const list = localAds.channelIds ?? [];
    const next = list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
    setLocalAds({ ...localAds, channelIds: next });
  };

  const reloadAds = async () => {
    const { data } = await supabase.from('custom_ads').select('*').order('sort_order').order('created_at');
    setCustomAds(data ?? []);
  };

  const saveCustomAd = async () => {
    const parsed = adSchema.safeParse(newAd);
    if (!parsed.success) { toast.error(parsed.error.issues[0]?.message ?? 'تحقق من البيانات'); return; }
    setSavingCustom(true);
    try {
      await adminDb.insert('custom_ads', {
        name: parsed.data.name, video_url: parsed.data.videoUrl,
        sort_order: customAds.length, hidden: false,
      }, true, true);
      setNewAd({ name: '', videoUrl: '' });
      await reloadAds();
      toast.success('تمت إضافة الإعلان المحلي');
    } catch (error: any) {
      toast.error(`فشل: ${error?.message ?? 'خطأ'}`);
    } finally { setSavingCustom(false); }
  };

  const toggleCustomAd = async (ad: CustomAdRow) => {
    try {
      await adminDb.update('custom_ads', { id: ad.id }, { hidden: !ad.hidden }, true);
      await reloadAds();
    } catch (error: any) { toast.error(`فشل: ${error?.message ?? 'خطأ'}`); }
  };

  const deleteCustomAd = async (id: string) => {
    if (!confirm('هل تريد حذف هذا الإعلان المحلي؟')) return;
    try {
      await adminDb.delete('custom_ads', { id }, true);
      await reloadAds();
      toast.success('تم حذف الإعلان');
    } catch (error: any) { toast.error(`فشل: ${error?.message ?? 'خطأ'}`); }
  };

  const saveForcedCount = async () => {
    const parsed = forcedCountSchema.safeParse(forcedAdsCount);
    if (!parsed.success) { toast.error(parsed.error.issues[0]?.message ?? 'قيمة غير صالحة'); return; }
    setSavingForced(true);
    try {
      await adminDb.upsert('system_settings', {
        key: 'forced_custom_ads_count', value: parsed.data,
        description: 'Forced Custom Ads Count',
      }, true);
      toast.success('تم حفظ عدد الإعلانات الإجبارية');
    } catch (error: any) { toast.error(`فشل: ${error?.message ?? 'خطأ'}`);
    } finally { setSavingForced(false); }
  };

  return (
    <div className="space-y-6">
      {/* CARD 1: Ad Network (Rewarded only) - independent from custom ads */}
      <Card className="border-border bg-card">
        <CardHeader>
          <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
            <Gift className="w-5 h-5 text-primary" />شبكة الإعلانات (Rewarded)
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <Label>تفعيل إعلانات الشبكة</Label>
            <Switch checked={adConfig.adsEnabled || false} onCheckedChange={(checked) => setAdConfig(prev => ({ ...prev, adsEnabled: checked }))} />
          </div>
          <Separator />
          <div className="space-y-2">
            <Label>شبكة الإعلانات</Label>
            <Select value={adConfig.adProvider ?? 'admob'} onValueChange={(value: 'admob' | 'applovin') => setAdConfig(prev => ({ ...prev, adProvider: value }))}>
              <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-card border-border">
                <SelectItem value="admob">AdMob Rewarded</SelectItem>
                <SelectItem value="applovin">AppLovin Rewarded</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>طريقة التشغيل</Label>
            <Select value={adConfig.gateMode ?? 'app_open_once'} onValueChange={(value: 'app_open_once' | 'unlock_channel') => setAdConfig(prev => ({ ...prev, gateMode: value }))}>
              <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-card border-border">
                <SelectItem value="app_open_once">مرة واحدة عند فتح التطبيق</SelectItem>
                <SelectItem value="unlock_channel">عند فتح قناة أو قسم مقفل</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Rewarded Ad Unit ID</Label>
            <Input value={adConfig.rewardedAdUnitId || adConfig.admobRewardedId || ''} onChange={(e) => setAdConfig(prev => ({ ...prev, rewardedAdUnitId: e.target.value.trim(), admobRewardedId: e.target.value.trim() }))} placeholder="ca-app-pub-xxxxx/xxxxx أو MAX_AD_UNIT_ID" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
          <div className="flex items-center justify-between rounded-lg border border-border bg-secondary/30 p-3">
            <div>
              <Label className="cursor-pointer">إجبارية على الروابط الخارجية</Label>
              <p className="text-xs text-muted-foreground mt-1">يفرض إعلان شبكة قبل تشغيل أي رابط خارجي مشفّر.</p>
            </div>
            <Switch checked={adConfig.forceExternal || false} onCheckedChange={(v) => setAdConfig(prev => ({ ...prev, forceExternal: v }))} />
          </div>
          <Button onClick={handleSave} disabled={saving} className="w-full bg-primary text-primary-foreground">{saving ? 'جارٍ الحفظ...' : 'حفظ إعدادات الشبكة'}</Button>
        </CardContent>
      </Card>

      {/* CARD 2: AdMob Application ID — written DIRECTLY into AndroidManifest.xml + build.gradle */}
      <Card className="border-border bg-card">
        <CardHeader>
          <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-primary" />AdMob App ID
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-xs text-muted-foreground">
            عند الضغط على «حفظ وإصدار تحديث» يتم تعديل قيمة <span className="font-mono">APPLICATION_ID</span> داخل
            <span className="font-mono"> AndroidManifest.xml</span> و <span className="font-mono">build.gradle</span> مباشرة في مستودع GitHub
            باستخدام التوكن المخزّن. البناء التالي سيستخدم هذا المعرّف تلقائياً — لا أسرار، لا متغيّرات بيئة، لا فرصة للخطأ.
          </p>
          <div className="space-y-2">
            <Label>App ID</Label>
            <Input value={admobAppId} onChange={(e) => setAdmobAppId(e.target.value)} placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
          <Button onClick={handleSaveAppId} disabled={savingAppId} className="w-full bg-primary text-primary-foreground">
            {savingAppId ? 'جارٍ تعديل الكود في GitHub...' : 'حفظ وإصدار تحديث'}
          </Button>
        </CardContent>
      </Card>

      {/* CARD 3: Local sequential custom ads — fully independent */}
      <Card className="border-border bg-card">
        <CardHeader>
          <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
            <Video className="w-5 h-5 text-primary" />الإعلانات المحلية المتسلسلة
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-xs text-muted-foreground">
            هذه الإعلانات منفصلة كلياً عن إعدادات القنوات. تُعرض في بداية التطبيق بالعدد المحدد ولا تتداخل مع إعدادات أي قناة فردية.
          </p>

          {/* Local ads behavior */}
          <div className="rounded-lg border border-border p-4 space-y-3 bg-secondary/30">
            <Label>متى تعرض الإعلانات المحلية؟</Label>
            <Select
              value={localAds.trigger ?? 'app_open'}
              onValueChange={(v: 'off' | 'app_open' | 'on_channel' | 'both') => setLocalAds({ ...localAds, trigger: v })}
            >
              <SelectTrigger className="bg-card border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-card border-border">
                <SelectItem value="off">معطّل</SelectItem>
                <SelectItem value="app_open">عند فتح التطبيق فقط</SelectItem>
                <SelectItem value="on_channel">عند فتح قناة فقط</SelectItem>
                <SelectItem value="both">في الحالتين</SelectItem>
              </SelectContent>
            </Select>
            {(localAds.trigger === 'on_channel' || localAds.trigger === 'both') && (
              <div className="space-y-2">
                <Label className="text-xs">قنوات معيّنة (اتركها فارغة لتُطبَّق على الكل)</Label>
                <div className="max-h-48 overflow-y-auto rounded-md border border-border p-2 bg-card grid grid-cols-1 md:grid-cols-2 gap-1">
                  {allChannels.map((c) => {
                    const checked = (localAds.channelIds ?? []).includes(c.id);
                    return (
                      <label key={c.id} className="flex items-center gap-2 text-sm cursor-pointer hover:bg-secondary/40 rounded px-2 py-1">
                        <input type="checkbox" checked={checked} onChange={() => toggleChannelScope(c.id)} className="accent-primary" />
                        <span className="truncate">{c.name}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            )}
            <div className="flex items-center justify-between rounded-md border border-border bg-card p-3">
              <div>
                <Label className="cursor-pointer">إجبارية على الروابط الخارجية</Label>
                <p className="text-xs text-muted-foreground mt-1">عند تشغيل رابط خارجي، يُعرض إعلان محلّي أوّلاً.</p>
              </div>
              <Switch checked={localAds.forceExternal || false} onCheckedChange={(v) => setLocalAds({ ...localAds, forceExternal: v })} />
            </div>
            <Button onClick={saveLocalAdsConfig} disabled={savingLocal} variant="outline">
              {savingLocal ? 'جارٍ الحفظ...' : 'حفظ سلوك الإعلانات المحلية'}
            </Button>
          </div>

          <div className="grid gap-3 md:grid-cols-[1fr_1.5fr_auto]">
            <Input value={newAd.name} onChange={(e) => setNewAd(prev => ({ ...prev, name: e.target.value }))} placeholder="اسم الإعلان" className="bg-secondary border-border" />
            <Input value={newAd.videoUrl} onChange={(e) => setNewAd(prev => ({ ...prev, videoUrl: e.target.value }))} placeholder="رابط الفيديو المباشر" className="bg-secondary border-border" dir="ltr" />
            <Button onClick={saveCustomAd} disabled={savingCustom} className="bg-primary text-primary-foreground"><Plus className="w-4 h-4 mr-2" />إضافة</Button>
          </div>
          <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
            <Label>عدد الإعلانات الإجبارية عند فتح التطبيق</Label>
            <div className="flex gap-3">
              <Input type="number" min={1} max={10} value={forcedAdsCount} onChange={(e) => setForcedAdsCount(e.target.value)} className="bg-secondary border-border max-w-[140px]" />
              <Button onClick={saveForcedCount} disabled={savingForced}>{savingForced ? 'جارٍ الحفظ...' : 'حفظ العدد'}</Button>
            </div>
          </div>
          <div className="space-y-2">
            {loadingAds ? (
              <p className="text-sm text-muted-foreground">جارٍ تحميل الإعلانات...</p>
            ) : customAds.length === 0 ? (
              <p className="text-sm text-muted-foreground">لا توجد إعلانات محلية بعد.</p>
            ) : (
              customAds.map((ad) => (
                <div key={ad.id} className="flex flex-col gap-3 rounded-lg border border-border bg-secondary/30 p-4 md:flex-row md:items-center md:justify-between">
                  <div className="min-w-0">
                    <p className="font-medium text-foreground">{ad.name}</p>
                    <p className="truncate text-sm text-muted-foreground" dir="ltr">{ad.video_url}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" onClick={() => toggleCustomAd(ad)}>{ad.hidden ? 'إظهار' : 'إخفاء'}</Button>
                    <Button variant="outline" onClick={() => deleteCustomAd(ad.id)}><Trash2 className="w-4 h-4" /></Button>
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
export default AdConfigManager;
