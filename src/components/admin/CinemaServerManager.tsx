import React, { useEffect, useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Clapperboard, Rocket, KeyRound, Loader2, Copy, Check, Film } from 'lucide-react';
import { toast } from 'sonner';

/**
 * Dedicated "Cinema Server" management — fully separate from the live-TV
 * gateway worker. Deploys its OWN Cloudflare Worker (apix-cinema) that proxies
 * only the movies/series traffic, and injects cinema-only secrets (TMDB key +
 * source links) away from the live gateway's secrets.
 */
interface CinemaServerConfig {
  scriptName: string;
  workerUrl: string;
}

const EMPTY: CinemaServerConfig = { scriptName: 'apix-cinema', workerUrl: '' };
const TOKEN_KEY = 'apix_cf_api_token'; // shared with CloudflareManager (same CF account)

const SUPA_URL = import.meta.env.VITE_SUPABASE_URL as string;
const SUPA_ANON = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string;

const CinemaServerManager: React.FC = () => {
  const [cfg, setCfg] = useState<CinemaServerConfig>(EMPTY);
  const [accountId, setAccountId] = useState('');
  const [apiToken, setApiToken] = useState('');
  const [tmdbKey, setTmdbKey] = useState('');
  const [sources, setSources] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const loadAll = useCallback(async () => {
    try {
      const [cfRes, cinemaRes] = await Promise.all([
        supabase.from('system_settings').select('value').eq('key', 'cloudflare_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'cinema_config').maybeSingle(),
      ]);
      const cf = (cfRes.data?.value as any) || {};
      setAccountId(cf.accountId || '');
      const cn = (cinemaRes.data?.value as any) || {};
      setCfg({ scriptName: cn.scriptName || 'apix-cinema', workerUrl: cn.workerUrl || '' });
      setApiToken(localStorage.getItem(TOKEN_KEY) || '');
    } catch {
      toast.error('فشل تحميل إعدادات سيرفر السينما');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const persistCinema = async (next: CinemaServerConfig) => {
    await adminDb.upsert('system_settings',
      { key: 'cinema_config', value: next, description: 'Dedicated cinema worker config (non-secret)' }, true);
  };

  const deploy = async () => {
    if (!accountId.trim() || !apiToken.trim()) {
      toast.error('أدخل Account ID و API Token (نفس بيانات Cloudflare)');
      return;
    }
    setBusy(true);
    try {
      const { data, error } = await supabase.functions.invoke('cloudflare-manager', {
        body: {
          action: 'deploy-cinema',
          accountId: accountId.trim(),
          apiToken: apiToken.trim(),
          scriptName: cfg.scriptName.trim() || 'apix-cinema',
          secrets: { SUPA_URL, SUPA_ANON },
          cinemaSecrets: {
            TMDB_KEY: tmdbKey.trim() || undefined,
            CINEMA_SOURCES: sources.trim() || undefined,
          },
        },
      });
      if (error) throw error;
      if (!data?.success) throw new Error(data?.error || 'فشل النشر');
      const next = { ...cfg, workerUrl: data.workerUrl || cfg.workerUrl };
      setCfg(next);
      await persistCinema(next);
      if (data.workerUrl) {
        try { await navigator.clipboard.writeText(data.workerUrl); } catch { /* ignore */ }
      }
      toast.success('تم نشر وركر السينما المنفصل وحقن أسراره الخاصة');
    } catch (e: any) {
      toast.error(`فشل نشر وركر السينما: ${e?.message}`);
    } finally {
      setBusy(false);
    }
  };

  const copyUrl = () => {
    if (!cfg.workerUrl) return;
    navigator.clipboard.writeText(cfg.workerUrl);
    setCopied(true); setTimeout(() => setCopied(false), 1500);
  };

  if (loading) {
    return <div className="flex items-center justify-center p-8"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>;
  }

  return (
    <div className="space-y-6">
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground">
            <Clapperboard className="w-5 h-5 text-primary" />إدارة سيرفر السينما (منفصل)
          </CardTitle>
          <CardDescription>
            وركر مستقل تماماً للأفلام والمسلسلات، لا يختلط ببث القنوات المباشر. يحمل أسراره الخاصة فقط
            (مفتاح TMDB وروابط مصادر الأفلام).
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Account ID</Label>
              <Input value={accountId} onChange={e => setAccountId(e.target.value)} placeholder="Cloudflare Account ID" className="bg-secondary border-border font-mono" dir="ltr" />
            </div>
            <div className="space-y-2">
              <Label className="flex items-center gap-2"><KeyRound className="w-4 h-4" />API Token</Label>
              <Input type="password" value={apiToken} onChange={e => { setApiToken(e.target.value); if (e.target.value) localStorage.setItem(TOKEN_KEY, e.target.value); }} placeholder="Cloudflare API Token" className="bg-secondary border-border font-mono" dir="ltr" />
            </div>
          </div>
          <div className="space-y-2">
            <Label>اسم وركر السينما (Script)</Label>
            <Input value={cfg.scriptName} onChange={e => setCfg(c => ({ ...c, scriptName: e.target.value }))} placeholder="apix-cinema" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
        </CardContent>
      </Card>

      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Film className="w-5 h-5 text-primary" />أسرار السينما</CardTitle>
          <CardDescription>تُحقن داخل وركر السينما فقط (مخفية)، ولا تصل للتطبيق أو لوركر البث المباشر.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>مفتاح TMDB (TMDB API Key)</Label>
            <Input type="password" value={tmdbKey} onChange={e => setTmdbKey(e.target.value)} placeholder="لإثراء صور ومعلومات الأفلام" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>روابط مصادر الأفلام (سطر لكل رابط)</Label>
            <Textarea value={sources} onChange={e => setSources(e.target.value)} placeholder={'https://source1.example/api\nhttps://source2.example/api'} className="bg-secondary border-border font-mono text-xs min-h-[110px]" dir="ltr" />
          </div>
          <Button onClick={deploy} disabled={busy} className="bg-primary text-primary-foreground">
            {busy ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Rocket className="w-4 h-4 mr-2" />}نشر / تحديث وركر السينما
          </Button>
          {cfg.workerUrl && (
            <div className="space-y-2">
              <Label>رابط وركر السينما</Label>
              <div className="flex gap-2">
                <Input value={cfg.workerUrl} readOnly className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                <Button variant="outline" size="icon" onClick={copyUrl}>{copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}</Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default CinemaServerManager;
