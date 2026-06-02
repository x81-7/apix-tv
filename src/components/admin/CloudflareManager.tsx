import React, { useState, useEffect, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Cloud, Rocket, KeyRound, Trash2, Github, Loader2, Eye, EyeOff, CheckCircle2, Copy, Check } from 'lucide-react';
import { toast } from 'sonner';

interface CfConfig {
  accountId: string;
  zoneId: string;
  scriptName: string;
  workerUrl: string;
  workerPins: string;
}

const EMPTY: CfConfig = { accountId: '', zoneId: '', scriptName: 'apix-gateway', workerUrl: '', workerPins: '' };
const TOKEN_KEY = 'apix_cf_api_token'; // kept locally — never written to a public table

const SUPA_URL = import.meta.env.VITE_SUPABASE_URL as string;
const SUPA_ANON = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string;

const CloudflareManager: React.FC = () => {
  const [cfg, setCfg] = useState<CfConfig>(EMPTY);
  const [apiToken, setApiToken] = useState('');
  const [encKey, setEncKey] = useState('');
  const [github, setGithub] = useState({ repo: '', token: '' });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [showToken, setShowToken] = useState(false);
  const [tokenValid, setTokenValid] = useState<boolean | null>(null);
  const [copied, setCopied] = useState(false);

  const loadAll = useCallback(async () => {
    try {
      const [cfRes, secRes] = await Promise.all([
        supabase.from('system_settings').select('value').eq('key', 'cloudflare_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'security_config').maybeSingle(),
      ]);
      if (cfRes.data?.value) setCfg(c => ({ ...c, ...(cfRes.data!.value as any) }));
      const sec = (secRes.data?.value as any) || {};
      setEncKey(sec.cloudDecryptionKey || '');
      setGithub({ repo: sec.githubRepo || '', token: sec.githubToken || '' });
      setApiToken(localStorage.getItem(TOKEN_KEY) || '');
    } catch { toast.error('فشل تحميل إعدادات Cloudflare'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const field = (k: keyof CfConfig, v: string) => setCfg(c => ({ ...c, [k]: v }));

  const persistToken = (v: string) => {
    setApiToken(v);
    if (v) localStorage.setItem(TOKEN_KEY, v); else localStorage.removeItem(TOKEN_KEY);
  };

  const saveConfig = async (next: CfConfig) => {
    await adminDb.upsert('system_settings',
      { key: 'cloudflare_config', value: next, description: 'Cloudflare gateway config (non-secret)' }, true);
  };

  const setBusyKey = (k: string, v: boolean) => setBusy(b => ({ ...b, [k]: v }));

  const callManager = async (action: string, extra: Record<string, unknown> = {}) => {
    const { data, error } = await supabase.functions.invoke('cloudflare-manager', {
      body: {
        action,
        accountId: cfg.accountId.trim(),
        apiToken: apiToken.trim(),
        scriptName: cfg.scriptName.trim() || 'apix-gateway',
        zoneId: cfg.zoneId.trim(),
        secrets: { SUPA_URL, SUPA_ANON, ENC_KEY: encKey, BAN_ENDPOINT: '' },
        ...extra,
      },
    });
    if (error) throw error;
    if (!data?.success) throw new Error(data?.error || 'فشل الطلب');
    return data;
  };

  const handleStatus = async () => {
    if (!apiToken.trim()) { toast.error('أدخل API Token أولاً'); return; }
    setBusyKey('status', true);
    try {
      const d = await callManager('status');
      setTokenValid(!!d.tokenValid);
      toast[d.tokenValid ? 'success' : 'error'](d.tokenValid ? 'التوكن صالح ✓' : 'التوكن غير صالح');
    } catch (e: any) { setTokenValid(false); toast.error(e?.message || 'فشل التحقق'); }
    finally { setBusyKey('status', false); }
  };

  const handleDeploy = async () => {
    if (!cfg.accountId.trim() || !apiToken.trim()) { toast.error('أدخل Account ID و API Token'); return; }
    if (!encKey) { toast.error('مفتاح التشفير فارغ — اضبط Cloud Decryption Key في قسم الحماية'); return; }
    setBusyKey('deploy', true);
    try {
      const d = await callManager('deploy');
      const next = { ...cfg, workerUrl: d.workerUrl || cfg.workerUrl };
      setCfg(next);
      await saveConfig(next);
      toast.success(`تم نشر الـ Worker وحقن الأسرار (${(d.secretsInjected || []).join(', ')})`);
    } catch (e: any) { toast.error(`فشل النشر: ${e?.message}`); }
    finally { setBusyKey('deploy', false); }
  };

  const handleUpdateSecrets = async () => {
    if (!cfg.accountId.trim() || !apiToken.trim()) { toast.error('أدخل Account ID و API Token'); return; }
    setBusyKey('secrets', true);
    try {
      const d = await callManager('update-secrets');
      toast.success(`تم تحديث الأسرار: ${(d.secretsInjected || []).join(', ')}`);
    } catch (e: any) { toast.error(`فشل التحديث: ${e?.message}`); }
    finally { setBusyKey('secrets', false); }
  };

  const handlePurge = async () => {
    if (!cfg.zoneId.trim()) { toast.error('أدخل Zone ID لمسح الكاش'); return; }
    setBusyKey('purge', true);
    try { await callManager('purge-cache'); toast.success('تم مسح الكاش'); }
    catch (e: any) { toast.error(`فشل المسح: ${e?.message}`); }
    finally { setBusyKey('purge', false); }
  };

  const syncGitHub = async () => {
    if (!github.repo || !github.token) { toast.error('اضبط المستودع و GitHub Token في قسم الحماية أولاً'); return; }
    if (!cfg.workerUrl) { toast.error('انشر الـ Worker أولاً للحصول على الرابط'); return; }
    setBusyKey('github', true);
    try {
      await saveConfig(cfg);
      const secrets: Record<string, string> = {
        WORKER_URL: cfg.workerUrl,
        WORKER_PINS: cfg.workerPins || '',
        CLOUD_URL: SUPA_URL,
        CLOUD_ANON_KEY: SUPA_ANON,
        ENCRYPTION_SECRET_KEY: encKey,
      };
      for (const [name, value] of Object.entries(secrets)) {
        if (!value) continue;
        const { data, error } = await supabase.functions.invoke('admin-write', {
          body: { table: '__github_sync', op: 'github_secret', githubRepo: github.repo, githubToken: github.token, secretName: name, secretValue: value },
        });
        if (error) throw error;
        if (!data?.success) throw new Error(data?.error || `فشل رفع ${name}`);
      }
      toast.success('تم رفع WORKER_URL والمفاتيح إلى GitHub Secrets');
    } catch (e: any) { toast.error(`فشل المزامنة: ${e?.message}`); }
    finally { setBusyKey('github', false); }
  };

  const copyUrl = () => {
    if (!cfg.workerUrl) return;
    navigator.clipboard.writeText(cfg.workerUrl);
    setCopied(true); setTimeout(() => setCopied(false), 1500);
  };

  if (loading) return <div className="flex items-center justify-center p-8"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>;

  return (
    <div className="space-y-6">
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Cloud className="w-5 h-5 text-primary" />بيانات حساب Cloudflare</CardTitle>
          <CardDescription>الوسيط الوحيد بين التطبيق و Supabase. يتم حقن الأسرار داخل الـ Worker ولا تُخزَّن كنص واضح.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>Account ID</Label>
            <Input value={cfg.accountId} onChange={e => field('accountId', e.target.value)} placeholder="Cloudflare Account ID" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><KeyRound className="w-4 h-4" />API Token</Label>
            <div className="flex gap-2">
              <Input type={showToken ? 'text' : 'password'} value={apiToken} onChange={e => persistToken(e.target.value)} placeholder="Cloudflare API Token (Edit Workers)" className="bg-secondary border-border font-mono" dir="ltr" />
              <Button variant="outline" size="icon" onClick={() => setShowToken(s => !s)}>{showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</Button>
            </div>
            <p className="text-xs text-muted-foreground">يُحفظ محلياً في متصفحك فقط ويُرسل مشفّراً عبر HTTPS عند كل عملية. صلاحيات مطلوبة: Workers Scripts:Edit.</p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>اسم الـ Worker (Script)</Label>
              <Input value={cfg.scriptName} onChange={e => field('scriptName', e.target.value)} placeholder="apix-gateway" className="bg-secondary border-border font-mono" dir="ltr" />
            </div>
            <div className="space-y-2">
              <Label>Zone ID (اختياري — لمسح الكاش)</Label>
              <Input value={cfg.zoneId} onChange={e => field('zoneId', e.target.value)} placeholder="Zone ID" className="bg-secondary border-border font-mono" dir="ltr" />
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={handleStatus} disabled={busy.status}>
              {busy.status ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <CheckCircle2 className="w-4 h-4 mr-2" />}التحقق من التوكن
            </Button>
            {tokenValid === true && <span className="text-xs text-green-500 self-center flex items-center gap-1"><CheckCircle2 className="w-4 h-4" />صالح</span>}
            {tokenValid === false && <span className="text-xs text-destructive self-center">غير صالح</span>}
          </div>
        </CardContent>
      </Card>

      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Rocket className="w-5 h-5 text-primary" />نشر وإدارة الـ Worker</CardTitle>
          <CardDescription>ينشئ سكريبت الـ Worker في حسابك ويحقن SUPA_URL و SUPA_ANON و ENC_KEY كأسرار مخفية.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {!encKey && <p className="text-xs text-destructive">⚠ مفتاح التشفير فارغ — اضبط Cloud Decryption Key في قسم الحماية قبل النشر.</p>}
          <div className="flex flex-wrap gap-2">
            <Button onClick={handleDeploy} disabled={busy.deploy} className="bg-primary text-primary-foreground">
              {busy.deploy ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Rocket className="w-4 h-4 mr-2" />}إنشاء / نشر الـ Worker
            </Button>
            <Button variant="outline" onClick={handleUpdateSecrets} disabled={busy.secrets}>
              {busy.secrets ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <KeyRound className="w-4 h-4 mr-2" />}تحديث الأسرار
            </Button>
            <Button variant="outline" onClick={handlePurge} disabled={busy.purge} className="hover:text-destructive">
              {busy.purge ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Trash2 className="w-4 h-4 mr-2" />}مسح الكاش
            </Button>
          </div>
          {cfg.workerUrl && (
            <div className="space-y-2">
              <Label>رابط الـ Worker</Label>
              <div className="flex gap-2">
                <Input value={cfg.workerUrl} readOnly className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                <Button variant="outline" size="icon" onClick={copyUrl}>{copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}</Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Github className="w-5 h-5 text-primary" />White-Labeling — مزامنة مع GitHub</CardTitle>
          <CardDescription>يرفع WORKER_URL والمفاتيح إلى GitHub Secrets ليتم حقنها تلقائياً عند بناء الـ APK.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>رابط الـ Worker (Worker URL)</Label>
            <Input value={cfg.workerUrl} onChange={e => field('workerUrl', e.target.value)} placeholder="https://apix-gateway.xxx.workers.dev" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
          </div>
          <Button onClick={syncGitHub} disabled={busy.github} className="w-full bg-primary text-primary-foreground">
            {busy.github ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" />جاري الرفع...</> : <><Github className="w-4 h-4 mr-2" />رفع WORKER_URL + المفاتيح إلى GitHub</>}
          </Button>
          <p className="text-xs text-muted-foreground">يرفع: WORKER_URL، CLOUD_URL، CLOUD_ANON_KEY، ENCRYPTION_SECRET_KEY. تأكد من ضبط المستودع و GitHub Token في قسم الحماية.</p>
        </CardContent>
      </Card>
    </div>
  );
};

export default CloudflareManager;
