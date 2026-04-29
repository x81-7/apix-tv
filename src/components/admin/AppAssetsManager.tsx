import React, { useRef, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Image as ImageIcon, Upload, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

type Kind = 'icon' | 'splash';

async function fileToBase64(file: File): Promise<string> {
  return await new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => {
      const result = r.result as string;
      const idx = result.indexOf('base64,');
      resolve(idx >= 0 ? result.slice(idx + 'base64,'.length) : result);
    };
    r.onerror = () => reject(r.error);
    r.readAsDataURL(file);
  });
}

const AppAssetsManager: React.FC = () => {
  const [iconBusy, setIconBusy] = useState(false);
  const [splashBusy, setSplashBusy] = useState(false);
  const iconRef = useRef<HTMLInputElement>(null);
  const splashRef = useRef<HTMLInputElement>(null);

  const upload = async (kind: Kind, file: File) => {
    if (!file.type.includes('png')) {
      toast.error('الملف يجب أن يكون PNG');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error('الحجم الأقصى 10MB');
      return;
    }
    const setBusy = kind === 'icon' ? setIconBusy : setSplashBusy;
    setBusy(true);
    try {
      const pngBase64 = await fileToBase64(file);
      const { data, error } = await supabase.functions.invoke('upload-app-assets', {
        body: { kind, pngBase64 },
      });
      if (error) throw new Error(error.message);
      if (data?.error) throw new Error(data.error);
      toast.success(`تم رفع ${kind === 'icon' ? 'الأيقونة' : 'شاشة البداية'} وتحديث ${data?.written?.length ?? 0} ملف على GitHub.`);
    } catch (e: any) {
      toast.error(`فشل: ${e?.message ?? 'خطأ'}`);
    } finally {
      setBusy(false);
      if (kind === 'icon' && iconRef.current) iconRef.current.value = '';
      if (kind === 'splash' && splashRef.current) splashRef.current.value = '';
    }
  };

  return (
    <Card className="border-border bg-card">
      <CardHeader>
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <ImageIcon className="w-5 h-5 text-primary" /> أيقونات التطبيق وشاشة البداية
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-xs text-muted-foreground">
          ارفع PNG شفاف 1024×1024 لأحسن نتيجة. سيتم توليد كل الأحجام تلقائياً للأندرويد + iOS وتحديث المستودع مباشرةً.
        </p>

        <div className="space-y-2 p-4 rounded-lg bg-secondary/50 border border-dashed border-border">
          <Label>أيقونة التطبيق (Launcher Icon)</Label>
          <input
            ref={iconRef}
            type="file"
            accept="image/png"
            className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) upload('icon', f); }}
          />
          <Button type="button" variant="outline" disabled={iconBusy} className="w-full"
            onClick={() => iconRef.current?.click()}>
            {iconBusy
              ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ الرفع وتوليد الأحجام...</>
              : <><Upload className="w-4 h-4 mr-2" /> رفع أيقونة PNG 1024×1024</>}
          </Button>
        </div>

        <div className="space-y-2 p-4 rounded-lg bg-secondary/50 border border-dashed border-border">
          <Label>صورة شاشة البداية (Splash)</Label>
          <input
            ref={splashRef}
            type="file"
            accept="image/png"
            className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) upload('splash', f); }}
          />
          <Button type="button" variant="outline" disabled={splashBusy} className="w-full"
            onClick={() => splashRef.current?.click()}>
            {splashBusy
              ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ الرفع...</>
              : <><Upload className="w-4 h-4 mr-2" /> رفع صورة Splash PNG</>}
          </Button>
        </div>

        <p className="text-[11px] text-muted-foreground">
          بعد الرفع، شغّل بناءً جديداً من قسم "معلومات التطبيق" → "إصدار تحديث".
        </p>
      </CardContent>
    </Card>
  );
};

export default AppAssetsManager;
