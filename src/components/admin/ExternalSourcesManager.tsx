import React, { useEffect, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Link2, Download, Loader2, Save } from 'lucide-react';
import { toast } from 'sonner';

/**
 * External Sources — lets a white-label client point the app at their OWN
 * raw JSON feed (e.g. GitHub Raw) to populate the cinema Home, with NO source
 * code changes. Also offers a downloadable template.json so the client knows
 * the exact shape to publish.
 */
const TEMPLATE = {
  hero: [
    {
      id: 'movie-1',
      title: 'اسم الفيلم',
      poster: 'https://.../poster.jpg',
      backdrop: 'https://.../backdrop.jpg',
      description: 'وصف قصير للفيلم',
      rating: '8.5',
      year: '2025',
      section: 'vod',
      url: 'https://.../stream.m3u8',
      useLocalProxy: false,
      ext: 'm3u8',
    },
  ],
  rows: [
    {
      id: 'trending',
      title: 'الأكثر مشاهدة',
      items: [
        {
          id: 'movie-2',
          title: 'فيلم آخر',
          poster: 'https://.../poster2.jpg',
          section: 'vod',
          url: 'https://.../stream2.mp4',
          useLocalProxy: true,
          ext: 'mp4',
        },
      ],
    },
  ],
};

const ExternalSourcesManager: React.FC = () => {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await supabase
          .from('system_settings').select('value').eq('key', 'externalSources').maybeSingle();
        const v = (data?.value as any) || {};
        setUrl(v.url || '');
      } catch {
        toast.error('فشل تحميل إعدادات المصادر الخارجية');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      await adminDb.upsert('system_settings',
        { key: 'externalSources', value: { url: url.trim() }, description: 'Client external JSON feed' }, true);
      toast.success('تم حفظ رابط المصدر الخارجي');
    } catch (e: any) {
      toast.error(`فشل الحفظ: ${e?.message}`);
    } finally {
      setSaving(false);
    }
  };

  const downloadTemplate = () => {
    const blob = new Blob([JSON.stringify(TEMPLATE, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'template.json';
    a.click();
    URL.revokeObjectURL(a.href);
  };

  if (loading) {
    return <div className="flex items-center justify-center p-8"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>;
  }

  return (
    <Card className="bg-card border-border">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-foreground"><Link2 className="w-5 h-5 text-primary" />المصادر الخارجية</CardTitle>
        <CardDescription>
          ضع رابط ملف JSON خاص بك (مثل GitHub Raw) ليقوم التطبيق بقراءته وعرض محتواه في الشاشة الرئيسية تلقائياً —
          دون تعديل الكود. حمّل قالب الهيكل لمعرفة ترتيب البيانات.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label>رابط JSON (Raw)</Label>
          <Input value={url} onChange={e => setUrl(e.target.value)} placeholder="https://raw.githubusercontent.com/user/repo/main/data.json" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={save} disabled={saving} className="bg-primary text-primary-foreground">
            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}حفظ الرابط
          </Button>
          <Button variant="outline" onClick={downloadTemplate}>
            <Download className="w-4 h-4 mr-2" />تحميل قالب الهيكل (template.json)
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};

export default ExternalSourcesManager;
