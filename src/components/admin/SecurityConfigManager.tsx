import React, { useState, useEffect, useCallback, useRef } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Shield, Plus, Trash2, Copy, Check, Key, Clock, Eye, EyeOff, Github, Upload, Loader2, Bug } from 'lucide-react';
import { toast } from 'sonner';

interface SignatureEntry { id: string; hash: string; label: string; enabled: boolean; addedAt: number; }

interface SecuritySettings {
  cloudDecryptionKey: string;
  appApiHmacSecret: string;
  internalKeySalt: string;
  keyValidityDays: number;
  githubRepo: string;
  keystoreBase64: string;
  keystorePassword: string;
  keyAlias: string;
  keyPassword: string;
  githubToken: string;
  externalPanelDecryptionKey: string;
}

const EMPTY_SETTINGS: SecuritySettings = {
  cloudDecryptionKey: '',
  appApiHmacSecret: '',
  internalKeySalt: '',
  keyValidityDays: 30,
  githubRepo: '',
  keystoreBase64: '',
  keystorePassword: '',
  keyAlias: '',
  keyPassword: '',
  githubToken: '',
  externalPanelDecryptionKey: '',
};

const SecurityConfigManager: React.FC = () => {
  const [signatures, setSignatures] = useState<SignatureEntry[]>([]);
  const [blockedSignatures, setBlockedSignatures] = useState<SignatureEntry[]>([]);
  const [newHash, setNewHash] = useState('');
  const [newLabel, setNewLabel] = useState('');
  const [newBlockedHash, setNewBlockedHash] = useState('');
  const [newBlockedLabel, setNewBlockedLabel] = useState('');
  const [loading, setLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [settings, setSettings] = useState<SecuritySettings>(EMPTY_SETTINGS);
  const [showSecrets, setShowSecrets] = useState<Record<string, boolean>>({});
  const [syncing, setSyncing] = useState<Record<string, boolean>>({});
  const [debugKillToasts, setDebugKillToasts] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const loadAll = useCallback(async () => {
    try {
      const [sigRes, blkRes, secRes, dbgRes] = await Promise.all([
        supabase.from('system_settings').select('value').eq('key', 'security_signatures').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'security_blocked_signatures').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'security_config').maybeSingle(),
        supabase.from('system_settings').select('value').eq('key', 'debug_kill_toasts').maybeSingle(),
      ]);
      const list: SignatureEntry[] = (sigRes.data?.value as any[]) || [];
      const blk: SignatureEntry[] = (blkRes.data?.value as any[]) || [];
      setSignatures(list.sort((a, b) => b.addedAt - a.addedAt));
      setBlockedSignatures(blk.sort((a, b) => b.addedAt - a.addedAt));
      if (secRes.data?.value) setSettings(s => ({ ...s, ...(secRes.data!.value as any) }));
      setDebugKillToasts(Boolean((dbgRes.data?.value as any)?.enabled));
    } catch { toast.error('فشل تحميل الإعدادات'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const saveSignatures = async (list: SignatureEntry[]) => {
    await adminDb.upsert('system_settings', { key: 'security_signatures', value: list, description: 'Allowed APK Signatures' }, true);
  };

  const saveBlockedSignatures = async (list: SignatureEntry[]) => {
    await adminDb.upsert('system_settings', { key: 'security_blocked_signatures', value: list, description: 'Blocked APK Signatures' }, true);
  };

  const saveSettings = async (s: SecuritySettings) => {
    await adminDb.upsert('system_settings', { key: 'security_config', value: s, description: 'Security & Encryption Config' }, true);
  };

  const addSignature = async () => {
    const hash = newHash.trim().toLowerCase();
    if (!hash || hash.length !== 64 || !/^[a-f0-9]+$/.test(hash)) { toast.error('بصمة SHA-256 غير صالحة (64 حرف hex)'); return; }
    if (signatures.some(s => s.hash === hash)) { toast.error('هذه البصمة موجودة بالفعل'); return; }
    const updated = [...signatures, { id: crypto.randomUUID(), hash, label: newLabel.trim() || 'APK Build', enabled: true, addedAt: Date.now() }];
    try { await saveSignatures(updated); setNewHash(''); setNewLabel(''); toast.success('تم إضافة البصمة'); loadAll(); } catch { toast.error('فشل إضافة البصمة'); }
  };

  const addBlockedSignature = async () => {
    const hash = newBlockedHash.trim().toLowerCase();
    if (!hash || hash.length !== 64 || !/^[a-f0-9]+$/.test(hash)) { toast.error('بصمة SHA-256 غير صالحة (64 حرف hex)'); return; }
    if (blockedSignatures.some(s => s.hash === hash)) { toast.error('هذه البصمة محظورة بالفعل'); return; }
    const updated = [...blockedSignatures, { id: crypto.randomUUID(), hash, label: newBlockedLabel.trim() || 'Blocked Build', enabled: true, addedAt: Date.now() }];
    try { await saveBlockedSignatures(updated); setNewBlockedHash(''); setNewBlockedLabel(''); toast.success('تم حظر البصمة'); loadAll(); } catch { toast.error('فشل حظر البصمة'); }
  };

  const toggleSignature = async (id: string) => {
    const updated = signatures.map(s => s.id === id ? { ...s, enabled: !s.enabled } : s);
    try { await saveSignatures(updated); setSignatures(updated); } catch { toast.error('فشل تحديث البصمة'); }
  };

  const toggleBlockedSignature = async (id: string) => {
    const updated = blockedSignatures.map(s => s.id === id ? { ...s, enabled: !s.enabled } : s);
    try { await saveBlockedSignatures(updated); setBlockedSignatures(updated); } catch { toast.error('فشل تحديث البصمة'); }
  };

  const removeSignature = async (id: string) => {
    const updated = signatures.filter(s => s.id !== id);
    try { await saveSignatures(updated); toast.success('تم حذف البصمة'); loadAll(); } catch { toast.error('فشل حذف البصمة'); }
  };

  const removeBlockedSignature = async (id: string) => {
    const updated = blockedSignatures.filter(s => s.id !== id);
    try { await saveBlockedSignatures(updated); toast.success('تم حذف البصمة من قائمة الحظر'); loadAll(); } catch { toast.error('فشل الحذف'); }
  };

  const handleFieldChange = (field: keyof SecuritySettings, value: string | number) => {
    setSettings(s => ({ ...s, [field]: value }));
  };

  const handleSaveField = async (field: keyof SecuritySettings) => {
    try {
      await saveSettings(settings);
      toast.success('تم الحفظ');
    } catch { toast.error('فشل الحفظ'); }
  };

  /** Sync a secret to GitHub repo secrets */
  const syncToGitHub = async (secretName: string, secretValue: string) => {
    if (!settings.githubRepo || !settings.githubToken) {
      toast.error('أدخل اسم المستودع و GitHub Token أولاً');
      return;
    }
    if (!secretValue || !String(secretValue).trim()) {
      toast.error(`قيمة ${secretName} فارغة — املأ الحقل قبل المزامنة`);
      return;
    }
    setSyncing(s => ({ ...s, [secretName]: true }));
    try {
      const { data, error } = await supabase.functions.invoke('admin-write', {
        body: {
          table: '__github_sync',
          op: 'github_secret',
          githubRepo: settings.githubRepo,
          githubToken: settings.githubToken,
          secretName,
          secretValue,
        },
      });
      if (error) throw error;
      if (!data?.success) throw new Error(data?.error || 'Sync failed');
      toast.success(`تم رفع ${secretName} إلى GitHub Secrets`);
    } catch (e: any) {
      toast.error(`فشل المزامنة: ${e?.message}`);
    } finally {
      setSyncing(s => ({ ...s, [secretName]: false }));
    }
  };

  const handleKeystoreFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = (reader.result as string).split(',')[1];
      setSettings(s => ({ ...s, keystoreBase64: base64 }));
      toast.success('تم تحويل ملف .jks إلى Base64');
    };
    reader.readAsDataURL(file);
  };

  const uploadKeystoreToGitHub = async () => {
    if (!settings.keystoreBase64 || !settings.keystorePassword || !settings.keyAlias || !settings.keyPassword) {
      toast.error('املأ جميع حقول التوقيع أولاً');
      return;
    }
    setSyncing(s => ({ ...s, keystore: true }));
    try {
      // Save settings first
      await saveSettings(settings);
      // Sync all keystore secrets
      const secrets: Record<string, string> = {
        KEYSTORE_BASE64: settings.keystoreBase64,
        KEYSTORE_PASSWORD: settings.keystorePassword,
        KEY_ALIAS: settings.keyAlias,
        KEY_PASSWORD: settings.keyPassword,
      };
      for (const [name, value] of Object.entries(secrets)) {
        const { data, error } = await supabase.functions.invoke('admin-write', {
          body: { table: '__github_sync', op: 'github_secret', githubRepo: settings.githubRepo, githubToken: settings.githubToken, secretName: name, secretValue: value },
        });
        if (error) throw error;
        if (!data?.success) throw new Error(data?.error || `Failed to sync ${name}`);
      }
      toast.success('تم رفع أسرار التوقيع إلى GitHub Actions');
    } catch (e: any) {
      toast.error(`فشل الرفع: ${e?.message}`);
    } finally {
      setSyncing(s => ({ ...s, keystore: false }));
    }
  };

  const toggleShow = (key: string) => setShowSecrets(s => ({ ...s, [key]: !s[key] }));

  if (loading) return <div className="flex items-center justify-center p-8"><div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" /></div>;

  return (
    <div className="space-y-6">
      {/* === 1. Fingerprints === */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Shield className="w-5 h-5 text-primary" />بصمات التطبيق المسموح بها (SHA-256)</CardTitle>
          <CardDescription>أضف بصمات SHA-256 للنسخ المصرح بها. يتحقق التطبيق عند أول تشغيل.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-2">
            <Input placeholder="SHA-256 Hash (64 hex chars)" value={newHash} onChange={e => setNewHash(e.target.value)} className="font-mono text-xs bg-secondary border-border" dir="ltr" />
            <div className="flex gap-2">
              <Input placeholder="التسمية (اختياري)" value={newLabel} onChange={e => setNewLabel(e.target.value)} className="flex-1 bg-secondary border-border" />
              <Button onClick={addSignature} className="bg-primary text-primary-foreground"><Plus className="w-4 h-4 mr-1" />إضافة بصمة</Button>
            </div>
          </div>
          {signatures.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-sm font-medium text-foreground flex items-center gap-2"><Shield className="w-4 h-4 text-primary" />البصمات الحالية ({signatures.length})</h4>
              {signatures.map(sig => (
                <div key={sig.id} className="flex items-center justify-between gap-2 p-3 rounded-lg bg-secondary/50 border border-border">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground">{sig.label}</p>
                    <p className="text-xs font-mono text-muted-foreground truncate" dir="ltr">{sig.hash}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Switch checked={sig.enabled} onCheckedChange={() => toggleSignature(sig.id)} />
                    <Button variant="ghost" size="sm" onClick={() => removeSignature(sig.id)} className="hover:text-destructive"><Trash2 className="w-4 h-4" /></Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* === 1b. Blocked Fingerprints (Manual Ban List) === */}
      <Card className="bg-card border-destructive/40">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground">
            <Shield className="w-5 h-5 text-destructive" />
            بصمات محظورة يدوياً (Block List)
          </CardTitle>
          <CardDescription>
            ضع هنا بصمة SHA-256 لأي نسخة معدّلة. عند تشغيل التطبيق، إن طابقت بصمة الـ APK أيّ من هذه القيم سيُغلق التطبيق فوراً.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-2">
            <Input
              placeholder="SHA-256 Hash (64 hex chars)"
              value={newBlockedHash}
              onChange={e => setNewBlockedHash(e.target.value)}
              className="font-mono text-xs bg-secondary border-border"
              dir="ltr"
            />
            <div className="flex gap-2">
              <Input
                placeholder="التسمية (مثال: نسخة مكركة من تيليجرام)"
                value={newBlockedLabel}
                onChange={e => setNewBlockedLabel(e.target.value)}
                className="flex-1 bg-secondary border-border"
              />
              <Button onClick={addBlockedSignature} variant="destructive">
                <Trash2 className="w-4 h-4 mr-1" />حظر هذه البصمة
              </Button>
            </div>
          </div>
          {blockedSignatures.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-sm font-medium text-destructive flex items-center gap-2">
                <Shield className="w-4 h-4" />
                البصمات المحظورة ({blockedSignatures.length})
              </h4>
              {blockedSignatures.map(sig => (
                <div key={sig.id} className="flex items-center justify-between gap-2 p-3 rounded-lg bg-destructive/10 border border-destructive/40">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground">{sig.label}</p>
                    <p className="text-xs font-mono text-muted-foreground truncate" dir="ltr">{sig.hash}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Switch checked={sig.enabled} onCheckedChange={() => toggleBlockedSignature(sig.id)} />
                    <Button variant="ghost" size="sm" onClick={() => removeBlockedSignature(sig.id)} className="hover:text-destructive">
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* === 2. Keystore === */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Key className="w-5 h-5 text-primary" />توقيع التطبيق (Keystore)</CardTitle>
          <CardDescription>ارفع keystore لتوقيع APK الإنتاج ببصمة ثابتة جاهزة لمتجر Play.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><Github className="w-4 h-4" />GitHub Repository</Label>
            <Input value={settings.githubRepo} onChange={e => handleFieldChange('githubRepo', e.target.value)} placeholder="owner/repository" className="bg-secondary border-border font-mono" dir="ltr" />
            <p className="text-xs text-muted-foreground">مطلوب لمزامنة Repository Secrets (مثال: username/apix-tv)</p>
          </div>
          <div className="space-y-2">
            <Label>Keystore (Base64)</Label>
            <Textarea value={settings.keystoreBase64} onChange={e => handleFieldChange('keystoreBase64', e.target.value)} placeholder="الصق محتوى ملف .jks بعد ترميزه Base64" className="bg-secondary border-border font-mono text-xs min-h-[80px]" dir="ltr" />
            <input ref={fileRef} type="file" accept=".jks,.keystore" className="hidden" onChange={handleKeystoreFile} />
            <Button variant="outline" onClick={() => fileRef.current?.click()} size="sm"><Upload className="w-4 h-4 mr-2" />أو اختر ملف .jks لتحويله تلقائياً</Button>
          </div>
          <div className="space-y-2">
            <Label>Keystore Password</Label>
            <Input type={showSecrets.ksPass ? 'text' : 'password'} value={settings.keystorePassword} onChange={e => handleFieldChange('keystorePassword', e.target.value)} placeholder="كلمة مرور الـ keystore" className="bg-secondary border-border" />
          </div>
          <div className="space-y-2">
            <Label>Key Alias</Label>
            <Input value={settings.keyAlias} onChange={e => handleFieldChange('keyAlias', e.target.value)} placeholder="مثال: apix-release" className="bg-secondary border-border font-mono" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>Key Password</Label>
            <Input type={showSecrets.keyPass ? 'text' : 'password'} value={settings.keyPassword} onChange={e => handleFieldChange('keyPassword', e.target.value)} placeholder="كلمة مرور المفتاح" className="bg-secondary border-border" />
          </div>
          <Button onClick={uploadKeystoreToGitHub} disabled={syncing.keystore} className="w-full bg-primary text-primary-foreground">
            {syncing.keystore ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" />جاري الرفع...</> : <><Shield className="w-4 h-4 mr-2" />رفع أسرار التوقيع إلى GitHub Actions</>}
          </Button>
          <p className="text-xs text-muted-foreground">بعد الرفع، أي بناء جديد على GitHub سيُنتج app-release.apk موقّعاً بهذه البصمة. لا تُحفظ هذه القيم في قاعدة البيانات — تُرفع مباشرة إلى GitHub Secrets كقيم مشفّرة.</p>
        </CardContent>
      </Card>

      {/* === 3. Encryption Keys === */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Key className="w-5 h-5 text-primary" />Cloud Decryption Key (ENCRYPTION_SECRET_KEY)</CardTitle>
          <CardDescription>
            مفتاح AES-256-GCM (64 hex / 32 byte) المستخدم لتشفير ردود الـ Edge Functions.
            عند الحفظ تستخدمه الدوال مباشرة من قاعدة البيانات — لا حاجة لإدخاله كسيكرت يدوي.
            زر «مزامنة جيت هب» يرفعه أيضاً إلى GitHub Actions ليُحقن في build التطبيقات (Android / iOS / Windows).
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Input type={showSecrets.cdk ? 'text' : 'password'} value={settings.cloudDecryptionKey} onChange={e => handleFieldChange('cloudDecryptionKey', e.target.value.trim())} placeholder="64 حرف hex" className="flex-1 bg-secondary border-border font-mono text-xs" dir="ltr" />
            <Button variant="outline" size="sm" onClick={() => toggleShow('cdk')}>{showSecrets.cdk ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</Button>
            <Button variant="outline" size="sm" onClick={async () => {
              const v = settings.cloudDecryptionKey.trim();
              if (!/^[0-9a-fA-F]{64}$/.test(v)) { toast.error('يجب أن يكون 64 حرف hex (32 بايت)'); return; }
              await saveSettings(settings);
              toast.success('تم الحفظ — الدوال ستستخدمه فوراً');
              await syncToGitHub('ENCRYPTION_SECRET_KEY', v);
            }} disabled={syncing.ENCRYPTION_SECRET_KEY}>
              {syncing.ENCRYPTION_SECRET_KEY ? <Loader2 className="w-4 h-4 animate-spin" /> : <><Github className="w-4 h-4 mr-1" />حفظ + مزامنة</>}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="text-foreground">APP_API_HMAC_SECRET (Internal App Secret)</CardTitle>
          <CardDescription>السر المشترك بين التطبيق والسيرفر. عند الضغط على «مزامنة جيت هب» سيُحدَّث تلقائياً ليُستخدم في Repository Secret في build_apk.yml.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Input type={showSecrets.hmac ? 'text' : 'password'} value={settings.appApiHmacSecret} onChange={e => handleFieldChange('appApiHmacSecret', e.target.value)} className="flex-1 bg-secondary border-border font-mono" dir="ltr" />
            <Button variant="outline" size="sm" onClick={() => toggleShow('hmac')}>{showSecrets.hmac ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</Button>
            <Button variant="outline" size="sm" onClick={() => { saveSettings(settings); syncToGitHub('APP_API_HMAC_SECRET', settings.appApiHmacSecret); }} disabled={syncing.APP_API_HMAC_SECRET}>
              {syncing.APP_API_HMAC_SECRET ? <Loader2 className="w-4 h-4 animate-spin" /> : <><Github className="w-4 h-4 mr-1" />مزامنة جيت هب</>}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="text-foreground">INTERNAL_KEY_SALT</CardTitle>
          <CardDescription>ملح اشتقاق المفتاح. يُمزج مع Cloud Decryption Key لإنتاج مفتاح AES-256-GCM.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Input type={showSecrets.salt ? 'text' : 'password'} value={settings.internalKeySalt} onChange={e => handleFieldChange('internalKeySalt', e.target.value)} className="flex-1 bg-secondary border-border font-mono" dir="ltr" />
            <Button variant="outline" size="sm" onClick={() => toggleShow('salt')}>{showSecrets.salt ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</Button>
            <Button variant="outline" size="sm" onClick={() => { saveSettings(settings); syncToGitHub('INTERNAL_KEY_SALT', settings.internalKeySalt); }} disabled={syncing.INTERNAL_KEY_SALT}>
              {syncing.INTERNAL_KEY_SALT ? <Loader2 className="w-4 h-4 animate-spin" /> : <><Github className="w-4 h-4 mr-1" />مزامنة جيت هب</>}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* === 4. Key Validity + GitHub Token === */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground"><Clock className="w-5 h-5 text-primary" />صلاحية المفتاح + إعدادات GitHub</CardTitle>
          <CardDescription>مدة بقاء المفتاح في ذاكرة الهاتف قبل طلبه مجدداً، ومستودع GitHub للمزامنة.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>Key Validity Period (يوم)</Label>
            <Input type="number" min={1} max={30} value={settings.keyValidityDays} onChange={e => handleFieldChange('keyValidityDays', Number(e.target.value))} className="bg-secondary border-border" />
            <p className="text-xs text-muted-foreground">من 1 إلى 30 يوماً</p>
          </div>
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><Github className="w-4 h-4" />GitHub Repository</Label>
            <Input value={settings.githubRepo} onChange={e => handleFieldChange('githubRepo', e.target.value)} placeholder="owner/repository" className="bg-secondary border-border font-mono" dir="ltr" />
            <p className="text-xs text-muted-foreground">مطلوب لمزامنة Repository Secrets</p>
          </div>
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><Key className="w-4 h-4" />GitHub Personal Access Token</Label>
            <Input type={showSecrets.ghToken ? 'text' : 'password'} value={settings.githubToken} onChange={e => handleFieldChange('githubToken', e.target.value)} placeholder="ghp_xxxxxxxxxxxx" className="bg-secondary border-border font-mono" dir="ltr" />
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => toggleShow('ghToken')}>{showSecrets.ghToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</Button>
              <Button variant="outline" size="sm" onClick={() => { saveSettings(settings); toast.success('تم حفظ الإعدادات'); }}>حفظ</Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* === 5. External Panel Decryption Key === */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-foreground">
            <Key className="w-5 h-5 text-primary" />مفتاح فك تشفير الروابط الخارجية
          </CardTitle>
          <CardDescription>
            يُستخدم في لوحة <span className="font-mono">generate_panel</span> لتشفير الروابط الخارجية ويفكّه التطبيق محلياً.
            عند الضغط على «مزامنة جيت هب» يُرفع كـ GitHub Secret باسم{' '}
            <span className="font-mono">EXTERNAL_PANEL_DECRYPTION_KEY</span> ويُحقن في البناء التالي.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-2">
            <Label>المفتاح (64 hex / 32 byte)</Label>
            <div className="flex items-center gap-2">
              <Input
                type={showSecrets.extKey ? 'text' : 'password'}
                value={settings.externalPanelDecryptionKey}
                onChange={e => handleFieldChange('externalPanelDecryptionKey', e.target.value.trim())}
                placeholder="64 حرف hex"
                className="flex-1 bg-secondary border-border font-mono text-xs"
                dir="ltr"
              />
              <Button variant="outline" size="sm" onClick={() => toggleShow('extKey')}>
                {showSecrets.extKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={syncing.EXTERNAL_PANEL_DECRYPTION_KEY}
                onClick={async () => {
                  const v = settings.externalPanelDecryptionKey.trim();
                  if (!/^[0-9a-fA-F]{64}$/.test(v)) {
                    toast.error('يجب أن يكون 64 حرف hex (32 بايت)');
                    return;
                  }
                  if (!settings.githubRepo || !settings.githubToken) {
                    toast.error('املأ GitHub Repo + Token أولاً في الكارت أعلاه');
                    return;
                  }
                  setSyncing(s => ({ ...s, EXTERNAL_PANEL_DECRYPTION_KEY: true }));
                  try {
                    await saveSettings(settings);
                    const { data, error } = await supabase.functions.invoke('update-github-secret', {
                      body: {
                        name: 'EXTERNAL_PANEL_DECRYPTION_KEY',
                        value: v,
                        githubToken: settings.githubToken,
                        githubRepo: settings.githubRepo,
                      },
                    });
                    if (error) throw new Error(error.message);
                    if (data?.error) throw new Error(data.error);
                    if (!data?.ok) throw new Error('استجابة غير متوقعة');
                    toast.success('تم رفع المفتاح إلى GitHub Secrets');
                  } catch (e: any) {
                    toast.error(`فشل: ${e?.message ?? 'خطأ'}`);
                  } finally {
                    setSyncing(s => ({ ...s, EXTERNAL_PANEL_DECRYPTION_KEY: false }));
                  }
                }}
              >
                {syncing.EXTERNAL_PANEL_DECRYPTION_KEY
                  ? <Loader2 className="w-4 h-4 animate-spin" />
                  : <><Github className="w-4 h-4 mr-1" />مزامنة جيت هب</>}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default SecurityConfigManager;
