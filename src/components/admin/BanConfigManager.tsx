import React, { useEffect, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { ShieldAlert, Send, Save } from 'lucide-react';
import { toast } from 'sonner';

interface BanConfig {
  official_signature_sha256: string;
  telegram_url: string;
  temp_ban_minutes: number;
  temp_ban_threshold: number;
  perma_ban_threshold: number;
  max_ip_changes_per_device: number;
  strike_window_hours: number;
  reset_after_days: number;
  integrity_check_enabled: boolean;
  anti_debug_enabled: boolean;
  anti_hook_enabled: boolean;
  auto_temp_ban_enabled: boolean;
  auto_perma_ban_enabled: boolean;
}

const DEFAULTS: BanConfig = {
  official_signature_sha256: '',
  telegram_url: '',
  temp_ban_minutes: 15,
  temp_ban_threshold: 4,
  perma_ban_threshold: 6,
  max_ip_changes_per_device: 2,
  strike_window_hours: 24,
  reset_after_days: 2,
  integrity_check_enabled: true,
  anti_debug_enabled: true,
  anti_hook_enabled: true,
  auto_temp_ban_enabled: true,
  auto_perma_ban_enabled: true,
};

const BanConfigManager: React.FC = () => {
  const [cfg, setCfg] = useState<BanConfig>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', 'ban_config')
        .maybeSingle();
      if (data?.value) setCfg({ ...DEFAULTS, ...(data.value as Partial<BanConfig>) });
      setLoading(false);
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      await adminDb.upsert('system_settings', {
        key: 'ban_config',
        value: cfg,
        description: 'Anti-tamper and ban system',
      }, true);
      toast.success('تم حفظ إعدادات الحماية');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message ?? 'خطأ'}`);
    } finally {
      setSaving(false);
    }
  };

  const set = <K extends keyof BanConfig>(k: K, v: BanConfig[K]) => setCfg(s => ({ ...s, [k]: v }));

  if (loading) return <div className="p-4 text-muted-foreground">جاري التحميل...</div>;

  return (
    <Card className="bg-card border-border">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-foreground">
          <ShieldAlert className="w-5 h-5 text-primary" />
          إعدادات نظام الحماية والحظر التلقائي
        </CardTitle>
        <CardDescription>تحكم في عتبات إعادة التثبيت، فحص النزاهة، ورابط الدعم.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-2">
          <Label className="flex items-center gap-2"><ShieldAlert className="w-4 h-4" /> بصمة التوقيع الرسمية (SHA-256)</Label>
          <Input
            value={cfg.official_signature_sha256}
            onChange={e => set('official_signature_sha256', e.target.value.trim().toLowerCase())}
            placeholder="64 hex chars — اتركه فارغاً لتعطيل فحص التوقيع السحابي"
            className="bg-secondary border-border font-mono text-xs" dir="ltr"
          />
        </div>

        <div className="space-y-2">
          <Label className="flex items-center gap-2"><Send className="w-4 h-4" /> رابط قناة تيليجرام (يظهر في شاشة الحظر)</Label>
          <Input
            value={cfg.telegram_url}
            onChange={e => set('telegram_url', e.target.value)}
            placeholder="https://t.me/your_channel"
            className="bg-secondary border-border" dir="ltr"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <Label>عتبة الحظر المؤقت (مرات)</Label>
            <Input type="number" min={1} value={cfg.temp_ban_threshold}
              onChange={e => set('temp_ban_threshold', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
          <div className="space-y-1">
            <Label>عتبة الحظر الدائم (مرات)</Label>
            <Input type="number" min={1} value={cfg.perma_ban_threshold}
              onChange={e => set('perma_ban_threshold', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
          <div className="space-y-1">
            <Label>مدة الحظر المؤقت (دقيقة)</Label>
            <Input type="number" min={1} value={cfg.temp_ban_minutes}
              onChange={e => set('temp_ban_minutes', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
          <div className="space-y-1">
            <Label>نافذة عدّ الإنذارات (ساعة)</Label>
            <Input type="number" min={1} value={cfg.strike_window_hours}
              onChange={e => set('strike_window_hours', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
          <div className="space-y-1">
            <Label>حد تغيّر الـ IP قبل الإجراء</Label>
            <Input type="number" min={1} value={cfg.max_ip_changes_per_device}
              onChange={e => set('max_ip_changes_per_device', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
          <div className="space-y-1 col-span-2">
            <Label>إعادة تعيين الإنذارات بعد (يوم)</Label>
            <Input type="number" min={1} value={cfg.reset_after_days}
              onChange={e => set('reset_after_days', Number(e.target.value))}
              className="bg-secondary border-border" />
          </div>
        </div>

        <div className="space-y-3 pt-2 border-t border-border">
          <div className="flex items-center justify-between">
            <Label>تفعيل فحص نزاهة التطبيق (Signature/Dex)</Label>
            <Switch checked={cfg.integrity_check_enabled}
              onCheckedChange={v => set('integrity_check_enabled', v)} />
          </div>
          <div className="flex items-center justify-between">
            <Label>تفعيل كشف Debugger (Anti-Debug)</Label>
            <Switch checked={cfg.anti_debug_enabled}
              onCheckedChange={v => set('anti_debug_enabled', v)} />
          </div>
          <div className="flex items-center justify-between">
            <Label>تفعيل كشف Frida/Xposed (Anti-Hook)</Label>
            <Switch checked={cfg.anti_hook_enabled}
              onCheckedChange={v => set('anti_hook_enabled', v)} />
          </div>
          <div className="flex items-center justify-between">
            <Label>الحظر التلقائي المؤقت</Label>
            <Switch checked={cfg.auto_temp_ban_enabled}
              onCheckedChange={v => set('auto_temp_ban_enabled', v)} />
          </div>
          <div className="flex items-center justify-between">
            <Label>الحظر التلقائي الدائم</Label>
            <Switch checked={cfg.auto_perma_ban_enabled}
              onCheckedChange={v => set('auto_perma_ban_enabled', v)} />
          </div>
        </div>

        <Button onClick={save} disabled={saving} className="w-full bg-primary text-primary-foreground">
          <Save className="w-4 h-4 mr-2" />
          {saving ? 'جاري الحفظ...' : 'حفظ الإعدادات'}
        </Button>
      </CardContent>
    </Card>
  );
};

export default BanConfigManager;
