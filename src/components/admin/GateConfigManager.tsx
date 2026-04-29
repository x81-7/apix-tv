import React, { useEffect, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { toast } from 'sonner';
import { Loader2, KeyRound, Send, DoorOpen } from 'lucide-react';

type GateConfig = {
  enabled: boolean;
  bypassCode: string;
  telegramUrl: string;
  title: string;
  subtitle: string;
};

const DEFAULTS: GateConfig = {
  enabled: false,
  bypassCode: '2026',
  telegramUrl: 'https://t.me/apix_tv',
  title: 'تشغيل يدوي',
  subtitle: 'أدخل بيانات البث أو كود الدخول',
};

const GateConfigManager: React.FC = () => {
  const [cfg, setCfg] = useState<GateConfig>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', 'gateConfig')
        .maybeSingle();
      if (data?.value) setCfg({ ...DEFAULTS, ...(data.value as Partial<GateConfig>) });
      setLoading(false);
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      await adminDb.upsert('system_settings', {
        key: 'gateConfig',
        value: cfg,
        description: 'Gate / login screen configuration',
      }, true);
      toast.success('تم الحفظ');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message || 'خطأ'}`);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-card rounded-2xl p-6 border border-border space-y-5">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <DoorOpen className="w-5 h-5 text-primary" />
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-bold text-foreground">شاشة الدخول الأمامية</h3>
            <p className="text-sm text-muted-foreground">
              تظهر قبل الصفحة الرئيسية وتسمح بإدخال روابط/مفاتيح يدوية أو كود تجاوز للدخول المباشر.
            </p>
          </div>
        </div>

        <div className="flex items-center justify-between gap-4 py-3 border-t border-border">
          <div>
            <p className="text-foreground font-medium">تفعيل شاشة الدخول</p>
            <p className="text-xs text-muted-foreground mt-1">عند الإيقاف يفتح التطبيق مباشرة على الصفحة الرئيسية.</p>
          </div>
          <Switch checked={cfg.enabled} onCheckedChange={(v) => setCfg({ ...cfg, enabled: v })} />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><KeyRound className="w-4 h-4" /> كود التجاوز</Label>
            <Input
              dir="ltr"
              value={cfg.bypassCode}
              onChange={(e) => setCfg({ ...cfg, bypassCode: e.target.value })}
              placeholder="2026"
              className="font-mono"
            />
            <p className="text-xs text-muted-foreground">
              من يدخل هذا الكود سيُحفظ على هاتفه ويفتح التطبيق مباشرة في المرات القادمة.
            </p>
          </div>
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><Send className="w-4 h-4" /> رابط قناة تلجرام</Label>
            <Input
              dir="ltr"
              value={cfg.telegramUrl}
              onChange={(e) => setCfg({ ...cfg, telegramUrl: e.target.value })}
              placeholder="https://t.me/your_channel"
            />
          </div>
          <div className="space-y-2">
            <Label>عنوان الشاشة</Label>
            <Input value={cfg.title} onChange={(e) => setCfg({ ...cfg, title: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>وصف فرعي</Label>
            <Input value={cfg.subtitle} onChange={(e) => setCfg({ ...cfg, subtitle: e.target.value })} />
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <Button onClick={save} disabled={saving} className="bg-primary text-primary-foreground">
            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
            حفظ الإعدادات
          </Button>
        </div>
      </div>
    </div>
  );
};

export default GateConfigManager;
