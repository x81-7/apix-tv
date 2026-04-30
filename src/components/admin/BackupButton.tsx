import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Download, Loader2 } from 'lucide-react';
import { supabase } from '@/integrations/supabase/client';
import { toast } from 'sonner';

/**
 * زر تنزيل نسخة نقل القنوات فقط.
 * لا يصدّر إعدادات النظام أو مفاتيح التشفير حتى لا يحمل الريماكس إعدادات مشروع قديم.
 */
const BackupButton: React.FC = () => {
  const [loading, setLoading] = useState(false);

  const handleBackup = async () => {
    setLoading(true);
    try {
      // جلب بيانات القنوات فقط بشكل متوازي
      const [catsRes, chansRes, menusRes, subsRes] = await Promise.all([
        supabase.from('categories').select('*').order('sort_order'),
        supabase.from('channels').select('*').order('sort_order'),
        supabase.from('side_menus').select('*').order('sort_order'),
        supabase.from('sub_channels').select('*').order('sort_order'),
      ]);

      const errors: string[] = [];
      if (catsRes.error) errors.push(`categories: ${catsRes.error.message}`);
      if (chansRes.error) errors.push(`channels: ${chansRes.error.message}`);
      if (menusRes.error) errors.push(`side_menus: ${menusRes.error.message}`);
      if (subsRes.error) errors.push(`sub_channels: ${subsRes.error.message}`);
      if (errors.length) throw new Error(errors.join(' • '));

      const categories = catsRes.data ?? [];
      const channels = chansRes.data ?? [];
      const sideMenus = menusRes.data ?? [];
      const subChannels = subsRes.data ?? [];

      // بناء هيكل مرتبط: كل قسم يحتوي على قنواته
      const categoriesWithChannels = categories.map((cat) => ({
        ...cat,
        channels: channels
          .filter((ch) => ch.category_id === cat.id)
          .map((ch) => ({
            ...ch,
            // تضمين كافة تفاصيل البث (روابط، مفاتيح DRM، هيدرز، مشغّل...)
            web_stream: ch.web_stream,
            android_stream: ch.android_stream,
          })),
      }));

      // القوائم الجانبية مع قنواتها الفرعية وكل التفاصيل
      const sideMenusWithSubs = sideMenus.map((menu) => ({
        ...menu,
        sub_channels: subChannels
          .filter((sc) => sc.side_menu_id === menu.id)
          .map((sc) => ({
            ...sc,
            web_stream: sc.web_stream,
            android_stream: sc.android_stream,
          })),
      }));

      // القنوات التي ليست مرتبطة بأي قسم (يتيمة)
      const orphanChannels = channels.filter(
        (ch) => !ch.category_id || !categories.some((c) => c.id === ch.category_id)
      );

      const payload = {
        meta: {
          source: 'supabase-cloud',
          exportedAt: new Date().toISOString(),
          version: 2,
          counts: {
            categories: categories.length,
            channels: channels.length,
            sideMenus: sideMenus.length,
            subChannels: subChannels.length,
          },
          warnings: errors,
        },
        data: {
          categories: categoriesWithChannels,
          sideMenus: sideMenusWithSubs,
          orphanChannels,
          // بيانات خام للاستيراد لاحقاً — قنوات فقط بدون إعدادات/مفاتيح المشروع
          raw: {
            categories,
            channels,
            side_menus: sideMenus,
            sub_channels: subChannels,
          },
        },
      };

      if (categories.length === 0 && channels.length === 0) {
        toast.warning('لا توجد بيانات لتنزيلها حالياً');
        return;
      }

      const blob = new Blob([JSON.stringify(payload, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      a.href = url;
      a.download = `apix-backup-${stamp}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);

      toast.success(
        `تم تنزيل النسخة الاحتياطية: ${categories.length} أقسام، ${channels.length} قنوات، ${sideMenus.length} قوائم جانبية، ${subChannels.length} قنوات فرعية`
      );
    } catch (err: any) {
      console.error('Backup failed:', err);
      toast.error(`فشل النسخ الاحتياطي: ${err?.message ?? 'خطأ غير معروف'}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-card rounded-2xl p-6 border border-border space-y-4">
      <div>
        <h3 className="text-lg font-bold text-foreground">النسخ الاحتياطي</h3>
        <p className="text-sm text-muted-foreground mt-1">
          نزّل ملف JSON للقنوات فقط: الأقسام، القنوات، القوائم الجانبية، القنوات الفرعية، وروابط البث.
        </p>
      </div>

      <Button
        onClick={handleBackup}
        disabled={loading}
        className="bg-primary text-primary-foreground hover:bg-primary/90"
      >
        {loading ? (
          <>
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            جارٍ التنزيل...
          </>
        ) : (
          <>
            <Download className="w-4 h-4 mr-2" />
            تنزيل ملف القنوات JSON
          </>
        )}
      </Button>
    </div>
  );
};

export default BackupButton;
