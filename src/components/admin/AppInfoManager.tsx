import React, { useEffect, useState } from 'react';
import { z } from 'zod';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Smartphone, Save, Rocket } from 'lucide-react';
import { toast } from 'sonner';

interface AppInfo {
  appName?: string;
  packageName?: string;
  versionName?: string;
  versionCode?: number;
}

const schema = z.object({
  appName: z.string().trim().min(1).max(60).optional(),
  packageName: z.string().trim().regex(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/, 'صيغة غير صحيحة').optional(),
  versionName: z.string().trim().regex(/^\d+(\.\d+){0,3}$/, 'مثال: 1.2 أو 2.5.0').optional(),
  versionCode: z.coerce.number().int().min(1).optional(),
});

const AppInfoManager: React.FC = () => {
  const [info, setInfo] = useState<AppInfo>({});
  const [saving, setSaving] = useState(false);
  const [building, setBuilding] = useState(false);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', 'app_info')
        .maybeSingle();
      if (data?.value) setInfo(data.value as AppInfo);
    })();
  }, []);

  const save = async () => {
    const parsed = schema.safeParse(info);
    if (!parsed.success) {
      toast.error(parsed.error.issues[0]?.message ?? 'بيانات غير صالحة');
      return;
    }
    setSaving(true);
    try {
      const { data, error } = await supabase.functions.invoke('update-android-config', {
        body: parsed.data,
      });
      if (error) throw new Error(error.message ?? 'فشل الاتصال');
      if (data?.error) throw new Error(data.error);
      if (!data?.ok) throw new Error('استجابة غير متوقعة');
      await adminDb.upsert('system_settings', {
        key: 'app_info',
        value: parsed.data,
        description: 'App identity (name/package/version) baked into Android source',
      }, true);
      toast.success('تم تعديل الكود في GitHub. شغّل البناء التالي لإصدار APK/AAB جديد.');
    } catch (e: any) {
      toast.error(`فشل: ${e?.message ?? 'خطأ'}`);
    } finally {
      setSaving(false);
    }
  };

  const triggerBuild = async () => {
    setBuilding(true);
    try {
      const { data, error } = await supabase.functions.invoke('trigger-android-build', { body: {} });
      if (error) throw new Error(error.message);
      if (data?.error) throw new Error(data.error);
      toast.success('تم تشغيل عملية البناء على GitHub Actions.');
    } catch (e: any) {
      toast.error(`فشل: ${e?.message ?? 'خطأ'}`);
    } finally {
      setBuilding(false);
    }
  };

  return (
    <Card className="border-border bg-card">
      <CardHeader>
        <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
          <Smartphone className="w-5 h-5 text-primary" />معلومات التطبيق
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-xs text-muted-foreground">
          هذه الحقول تُكتب مباشرةً في كود الأندرويد (<span className="font-mono">build.gradle</span>،{' '}
          <span className="font-mono">strings.xml</span>) في مستودع GitHub باستخدام التوكن المخزّن.
          لا يوجد وسيط — التغيير يدخل البناء التالي.
        </p>

        <div className="space-y-2">
          <Label>اسم التطبيق</Label>
          <Input value={info.appName ?? ''} onChange={(e) => setInfo({ ...info, appName: e.target.value })}
            placeholder="APiX" className="bg-secondary border-border" />
        </div>

        <div className="space-y-2">
          <Label>اسم الحزمة (Package name)</Label>
          <Input value={info.packageName ?? ''} onChange={(e) => setInfo({ ...info, packageName: e.target.value })}
            placeholder="com.apix.app" className="bg-secondary border-border font-mono" dir="ltr" />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-2">
            <Label>إصدار التطبيق (versionName)</Label>
            <Input value={info.versionName ?? ''} onChange={(e) => setInfo({ ...info, versionName: e.target.value })}
              placeholder="2.5.0" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>رقم البناء (versionCode)</Label>
            <Input type="number" value={info.versionCode ?? ''} onChange={(e) => setInfo({ ...info, versionCode: Number(e.target.value) })}
              placeholder="3" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
        </div>

        <div className="flex gap-2">
          <Button onClick={save} disabled={saving} className="flex-1 bg-primary text-primary-foreground">
            <Save className="w-4 h-4 mr-2" />{saving ? 'جارٍ الحفظ...' : 'حفظ وتعديل الكود'}
          </Button>
          <Button onClick={triggerBuild} disabled={building} variant="outline">
            <Rocket className="w-4 h-4 mr-2" />{building ? 'جارٍ التشغيل...' : 'إصدار تحديث'}
          </Button>
        </div>

        <p className="text-xs text-muted-foreground">
          الأيقونات (تطبيق / شاشات / إشعارات) — استخدم قسم "أيقونات التطبيق" المنفصل لرفعها (PNG شفاف).
        </p>
      </CardContent>
    </Card>
  );
};

export default AppInfoManager;
