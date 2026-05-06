import React, { useState, useEffect, useRef } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Download, Rocket, CheckCircle, Clock, Upload, Loader2, Package, Trash2, ShieldOff } from 'lucide-react';
import { toast } from 'sonner';

interface UpdateConfig {
  downloadUrl: string;
  message: string;
  versionName: string;
  isActive: boolean;
  releasedAt?: number;
  installMode?: 'internal' | 'external'; // internal = download+install in-app, external = open browser
  storagePath?: string; // path inside app-builds bucket if uploaded here
  forceUpdate?: boolean; // when true, user cannot dismiss the dialog
  requiredVersionCode?: number; // builds below this code are forced to update
}

const AppUpdateManager: React.FC = () => {
  const [downloadUrl, setDownloadUrl] = useState('');
  const [message, setMessage] = useState('');
  const [versionName, setVersionName] = useState('');
  const [installMode, setInstallMode] = useState<'internal' | 'external'>('internal');
  const [forceUpdate, setForceUpdate] = useState(false);
  const [requiredVersionCode, setRequiredVersionCode] = useState<string>('');
  const [currentUpdate, setCurrentUpdate] = useState<UpdateConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [storagePath, setStoragePath] = useState<string | undefined>(undefined);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', 'appUpdate')
        .maybeSingle();
      if (data?.value) {
        const v = data.value as unknown as UpdateConfig;
        setCurrentUpdate(v);
        setDownloadUrl(v.downloadUrl ?? '');
        setMessage(v.message ?? '');
        setVersionName(v.versionName ?? '');
        setInstallMode(v.installMode ?? 'internal');
        setStoragePath(v.storagePath);
        setForceUpdate(!!v.forceUpdate);
        setRequiredVersionCode(v.requiredVersionCode ? String(v.requiredVersionCode) : '');
      }
    })();
  }, []);

  const handleUpload = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.apk')) {
      toast.error('الملف يجب أن يكون بصيغة APK');
      return;
    }
    setUploading(true);
    try {
      if (storagePath) {
        await supabase.storage.from('app-builds').remove([storagePath]).catch(() => null);
      }
      const safeVersion = (versionName.trim() || `build-${Date.now()}`).replace(/[^\w.-]+/g, '_');
      const path = `releases/${safeVersion}-${Date.now()}.apk`;
      const { error } = await supabase.storage
        .from('app-builds')
        .upload(path, file, { contentType: 'application/vnd.android.package-archive', upsert: true });
      if (error) throw error;
      const { data: pub } = supabase.storage.from('app-builds').getPublicUrl(path);
      setDownloadUrl(pub.publicUrl);
      setStoragePath(path);
      toast.success('تم رفع ملف APK بنجاح');
    } catch (e: any) {
      toast.error(`فشل الرفع: ${e?.message ?? 'خطأ'}`);
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const handleRelease = async () => {
    if (!downloadUrl.trim() || !message.trim()) return;
    setSaving(true);
    const update: UpdateConfig = {
      downloadUrl: downloadUrl.trim(),
      message: message.trim(),
      versionName: versionName.trim() || 'جديد',
      isActive: true,
      releasedAt: Date.now(),
      installMode,
      storagePath,
      forceUpdate,
      requiredVersionCode: requiredVersionCode.trim() ? Math.max(0, parseInt(requiredVersionCode, 10) || 0) : 0,
    };
    try {
      if (currentUpdate?.storagePath && storagePath && currentUpdate.storagePath !== storagePath) {
        await supabase.storage.from('app-builds').remove([currentUpdate.storagePath]).catch(() => null);
      }
      await adminDb.upsert('system_settings', { key: 'appUpdate', value: update, description: 'App Update Config' });
      // Send a push notification to all old installs so they pick up the update immediately
      try {
        await adminDb.pushNotification(
          `تحديث جديد ${update.versionName}`,
          update.message,
          { type: 'app_update', url: update.downloadUrl, mode: installMode, minVersionName: update.versionName, requiredVersionCode: update.requiredVersionCode || 0 },
          'broadcast'
        );
      } catch { /* push is best-effort */ }
      setCurrentUpdate(update);
      toast.success('تم إطلاق التحديث وإرسال الإشعار');
    } catch {
      toast.error('فشل إطلاق التحديث');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (checked: boolean) => {
    if (!currentUpdate) return;
    const updated = { ...currentUpdate, isActive: checked, forceUpdate: checked ? currentUpdate.forceUpdate : false };
    try {
      await adminDb.upsert('system_settings', { key: 'appUpdate', value: updated, description: 'App Update Config' });
      setCurrentUpdate(updated);
      setForceUpdate(!!updated.forceUpdate);
    } catch { /* ignore */ }
  };

  const handleDisableUpdate = async () => {
    const updated: UpdateConfig = {
      ...(currentUpdate ?? { downloadUrl, message, versionName, installMode }),
      isActive: false,
      forceUpdate: false,
      requiredVersionCode: 0,
    };
    try {
      await adminDb.upsert('system_settings', { key: 'appUpdate', value: updated, description: 'App Update Config' });
      setCurrentUpdate(updated);
      setForceUpdate(false);
      setRequiredVersionCode('');
      toast.success('تم إلغاء التحديث الإجباري وإخفاء رسالة التحديث');
    } catch {
      toast.error('فشل إلغاء التحديث');
    }
  };

  const handleDeleteUploadedApk = async () => {
    if (!storagePath && !currentUpdate?.storagePath) {
      toast.error('لا يوجد ملف APK مرفوع على الكلاود');
      return;
    }
    if (!confirm('سيتم حذف ملف APK المرفوع على الكلاود نهائياً لتوفير المساحة. هل أنت متأكد؟')) return;
    const path = storagePath || currentUpdate?.storagePath!;
    try {
      const { error } = await supabase.storage.from('app-builds').remove([path]);
      if (error) throw error;
      // Clear storagePath from settings so we don't reference a deleted file
      const updated: UpdateConfig = {
        ...(currentUpdate ?? { downloadUrl, message, versionName, installMode, isActive: false }),
        storagePath: undefined,
      };
      await adminDb.upsert('system_settings', { key: 'appUpdate', value: updated, description: 'App Update Config' });
      setCurrentUpdate(updated);
      setStoragePath(undefined);
      toast.success('تم حذف ملف APK من الكلاود');
    } catch (e: any) {
      toast.error(`فشل الحذف: ${e?.message ?? 'خطأ'}`);
    }
  };

  return (
    <div className="space-y-6">
      <Card className="border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Download className="w-5 h-5 text-primary" />إدارة تحديث التطبيق</CardTitle>
          <CardDescription>ارفع ملف APK أو ضع رابطاً خارجياً، ثم أطلق التحديث ليصل لجميع المستخدمين فوراً.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Upload APK */}
          <div className="space-y-2 p-4 rounded-lg bg-secondary/50 border border-dashed border-border">
            <Label className="flex items-center gap-2"><Package className="w-4 h-4" /> رفع ملف APK مباشرة (مستضاف على Lovable Cloud)</Label>
            <input
              ref={fileRef}
              type="file"
              accept=".apk,application/vnd.android.package-archive"
              className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) handleUpload(f); }}
            />
            <Button
              type="button"
              variant="outline"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
              className="w-full"
            >
              {uploading
                ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> جارٍ الرفع...</>
                : <><Upload className="w-4 h-4 mr-2" /> اختر ملف APK ورفعه</>}
            </Button>
            <p className="text-xs text-muted-foreground">سيظهر الرابط تلقائياً في الحقل أدناه بعد الرفع.</p>
          </div>

          <div className="space-y-2">
            <Label>رابط التحميل <span className="text-destructive">*</span></Label>
            <Input
              value={downloadUrl}
              onChange={(e) => { setDownloadUrl(e.target.value); setStoragePath(undefined); }}
              placeholder="https://example.com/app.apk"
              className="bg-secondary border-border font-mono"
              dir="ltr"
            />
          </div>
          <div className="space-y-2">
            <Label>اسم الإصدار</Label>
            <Input value={versionName} onChange={(e) => setVersionName(e.target.value)} placeholder="مثال: 2.1.0" className="bg-secondary border-border" />
          </div>
          <div className="space-y-2">
            <Label>رسالة التحديث <span className="text-destructive">*</span></Label>
            <Textarea value={message} onChange={(e) => setMessage(e.target.value)} placeholder="مثال: هناك تحديث جديد" className="bg-secondary border-border min-h-[80px]" />
          </div>

          {/* Install mode */}
          <div className="flex items-center justify-between gap-4 p-3 rounded-lg bg-secondary/50 border border-border">
            <div>
              <p className="text-sm font-medium text-foreground">وضع التثبيت الداخلي (داخل التطبيق)</p>
              <p className="text-xs text-muted-foreground mt-0.5">عند التفعيل يتم تنزيل APK وتثبيته داخل التطبيق. عند التعطيل يفتح الرابط في المتصفح.</p>
            </div>
            <Switch
              checked={installMode === 'internal'}
              onCheckedChange={(v) => setInstallMode(v ? 'internal' : 'external')}
            />
          </div>

          {/* Force update */}
          <div className="flex items-center justify-between gap-4 p-3 rounded-lg bg-destructive/10 border border-destructive/40">
            <div>
              <p className="text-sm font-medium text-foreground">إجبار التحديث (لا يمكن التخطي)</p>
              <p className="text-xs text-muted-foreground mt-0.5">عند التفعيل لن يستطيع المستخدم استخدام التطبيق دون تثبيت التحديث.</p>
            </div>
            <Switch checked={forceUpdate} onCheckedChange={setForceUpdate} />
          </div>

          <div className="space-y-2">
            <Label>الحد الأدنى لـ versionCode (اختياري)</Label>
            <Input
              type="number"
              value={requiredVersionCode}
              onChange={(e) => setRequiredVersionCode(e.target.value)}
              placeholder="مثال: 5"
              className="bg-secondary border-border font-mono"
              dir="ltr"
            />
            <p className="text-xs text-muted-foreground">أي تطبيق برقم بناء أقل من هذا سيُجبر على التحديث تلقائياً.</p>
          </div>

          <Button onClick={handleRelease} disabled={saving || !downloadUrl.trim() || !message.trim()} className="w-full bg-primary text-primary-foreground">
            {saving ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <Rocket className="w-4 h-4 ml-2" />}
            إطلاق التحديث
          </Button>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 border-t border-border">
            <Button type="button" variant="destructive" onClick={handleDisableUpdate} className="w-full">
              <ShieldOff className="w-4 h-4 ml-2" /> إيقاف التحديث الإجباري
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={handleDeleteUploadedApk}
              disabled={!storagePath && !currentUpdate?.storagePath}
              className="w-full border-destructive/50 text-destructive hover:bg-destructive/10"
            >
              <Trash2 className="w-4 h-4 ml-2" /> حذف ملف APK من الكلاود
            </Button>
          </div>
        </CardContent>
      </Card>

      {currentUpdate && (
        <Card className="border-border bg-card">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><CheckCircle className="w-4 h-4 text-primary" />التحديث الحالي</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between py-2 border-b border-border">
              <span className="text-muted-foreground text-sm">الإصدار</span>
              <span className="text-foreground font-medium">{currentUpdate.versionName || '-'}</span>
            </div>
            <div className="flex items-center justify-between py-2 border-b border-border">
              <span className="text-muted-foreground text-sm">وضع التثبيت</span>
              <span className="text-foreground text-sm">{(currentUpdate.installMode ?? 'internal') === 'internal' ? 'داخلي (APK)' : 'خارجي (متصفح)'}</span>
            </div>
            <div className="flex items-center justify-between py-2 border-b border-border">
              <span className="text-muted-foreground text-sm">الرسالة</span>
              <span className="text-foreground text-sm max-w-[200px] text-left truncate">{currentUpdate.message}</span>
            </div>
            {currentUpdate.releasedAt && (
              <div className="flex items-center justify-between py-2 border-b border-border">
                <span className="text-muted-foreground text-sm">تاريخ الإطلاق</span>
                <span className="text-foreground text-sm flex items-center gap-1"><Clock className="w-3 h-3" />{new Date(currentUpdate.releasedAt).toLocaleString('ar')}</span>
              </div>
            )}
            <div className="flex items-center justify-between py-2">
              <span className="text-muted-foreground text-sm">مفعّل</span>
              <Switch checked={currentUpdate.isActive} onCheckedChange={handleToggle} />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default AppUpdateManager;
