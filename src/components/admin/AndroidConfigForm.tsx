import React, { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Separator } from '@/components/ui/separator';
import { Smartphone, Globe, Play, ExternalLink, FileCode, Key, Link, Plus, Trash2, Image, Monitor, Volume2, Subtitles, Server, Shield } from 'lucide-react';
import type { AndroidStreamConfig, AndroidActionType, DrmScheme, ClearKeyMode, CustomHeader, AudioSource, LogoOverlay, DynamicApiConfig, AspectRatioMode, WebViewOrientationMode } from '@/types/admin';

interface AndroidConfigFormProps {
  config: Partial<AndroidStreamConfig>;
  actionType: AndroidActionType;
  onChange: (config: Partial<AndroidStreamConfig>) => void;
  onActionTypeChange: (actionType: AndroidActionType) => void;
}

const AndroidConfigForm: React.FC<AndroidConfigFormProps> = ({
  config,
  actionType,
  onChange,
  onActionTypeChange
}) => {
  const [clearKeyMode, setClearKeyMode] = useState<ClearKeyMode>(
    config.drmClearKeyMode || 'combined'
  );

  const updateConfig = (updates: Partial<AndroidStreamConfig>) => {
    onChange({ ...config, ...updates });
  };

  const updateHeaders = (field: string, value: string) => {
    onChange({
      ...config,
      headers: {
        ...config.headers,
        [field]: value
      }
    });
  };

  const isWebViewAction = actionType === 'webview';
  const showAdvancedSections = actionType !== 'intent' && actionType !== 'webview';

  // Custom headers helpers
  const addCustomHeader = () => {
    const current = config.customHeaders || [];
    updateConfig({ customHeaders: [...current, { key: '', value: '' }] });
  };
  const removeCustomHeader = (index: number) => {
    const current = [...(config.customHeaders || [])];
    current.splice(index, 1);
    updateConfig({ customHeaders: current });
  };
  const updateCustomHeader = (index: number, field: 'key' | 'value', val: string) => {
    const current = [...(config.customHeaders || [])];
    current[index] = { ...current[index], [field]: val };
    updateConfig({ customHeaders: current });
  };

  // DRM License headers helpers
  const addDrmLicenseHeader = () => {
    const current = config.drmLicenseHeaders || [];
    updateConfig({ drmLicenseHeaders: [...current, { key: '', value: '' }] });
  };
  const removeDrmLicenseHeader = (index: number) => {
    const current = [...(config.drmLicenseHeaders || [])];
    current.splice(index, 1);
    updateConfig({ drmLicenseHeaders: current });
  };
  const updateDrmLicenseHeader = (index: number, field: 'key' | 'value', val: string) => {
    const current = [...(config.drmLicenseHeaders || [])];
    current[index] = { ...current[index], [field]: val };
    updateConfig({ drmLicenseHeaders: current });
  };

  // Audio sources helpers
  const addAudioSource = () => {
    const current = config.audioSources || [];
    updateConfig({ audioSources: [...current, { name: '', url: '' }] });
  };
  const removeAudioSource = (index: number) => {
    const current = [...(config.audioSources || [])];
    current.splice(index, 1);
    updateConfig({ audioSources: current });
  };
  const updateAudioSource = (index: number, field: 'name' | 'url', val: string) => {
    const current = [...(config.audioSources || [])];
    current[index] = { ...current[index], [field]: val };
    updateConfig({ audioSources: current });
  };

  return (
    <div className="space-y-4 p-4 rounded-lg border-2 border-green-600/30 bg-green-950/20">
      <div className="flex items-center gap-2 text-green-400">
        <Smartphone className="w-5 h-5" />
        <span className="font-bold text-base">📱 إعدادات أندرويد</span>
      </div>
      
      {/* Action Type */}
      <div className="space-y-2">
        <Label>نوع الإجراء في التطبيق</Label>
        <Select
          value={actionType}
          onValueChange={(value: AndroidActionType) => onActionTypeChange(value)}
        >
          <SelectTrigger className="bg-secondary border-border">
            <SelectValue placeholder="اختر نوع الإجراء" />
          </SelectTrigger>
          <SelectContent className="bg-popover border-border z-50">
            <SelectItem value="native">
              <div className="flex items-center gap-2">
                <Play className="w-4 h-4 text-green-500" />
                <span>مشغل أصلي (Native Player)</span>
              </div>
            </SelectItem>
            <SelectItem value="youtube">
              <div className="flex items-center gap-2">
                <Play className="w-4 h-4 text-red-500" />
                <span>مشغل يوتيوب (YouTube Sniffer)</span>
              </div>
            </SelectItem>
            <SelectItem value="webview">
              <div className="flex items-center gap-2">
                <Globe className="w-4 h-4 text-blue-500" />
                <span>WebView (تضمين ويب)</span>
              </div>
            </SelectItem>
            <SelectItem value="shaka_web">
              <div className="flex items-center gap-2">
                <Globe className="w-4 h-4 text-cyan-500" />
                <span>مشغل شاكا ويب (Shaka Web Player)</span>
              </div>
            </SelectItem>
            <SelectItem value="jw_web">
              <div className="flex items-center gap-2">
                <Globe className="w-4 h-4 text-amber-500" />
                <span>مشغل JW ويب (Dash.js Hybrid)</span>
              </div>
            </SelectItem>
            <SelectItem value="intent">
              <div className="flex items-center gap-2">
                <ExternalLink className="w-4 h-4 text-purple-500" />
                <span>Intent (تطبيق خارجي)</span>
              </div>
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Stream URL */}
      <div className="space-y-2">
        <Label>رابط البث للتطبيق</Label>
        <Input
          value={config.url || ''}
          onChange={(e) => updateConfig({ url: e.target.value })}
          placeholder="https://stream.example.com/live.m3u8"
          className="bg-secondary border-border font-mono text-sm"
          dir="ltr"
        />
      </div>

      {isWebViewAction && (
        <div className="space-y-2 p-3 rounded-lg border border-blue-600/30 bg-blue-950/20">
          <Label>وضعية WebView</Label>
          <Select
            value={config.webViewOrientation || 'auto'}
            onValueChange={(value: WebViewOrientationMode) => updateConfig({ webViewOrientation: value })}
          >
            <SelectTrigger className="bg-secondary border-border">
              <SelectValue placeholder="اختر وضعية الشاشة" />
            </SelectTrigger>
            <SelectContent className="bg-popover border-border z-50">
              <SelectItem value="auto">تلقائي حسب دوران الجهاز</SelectItem>
              <SelectItem value="landscape">إجباري أفقي</SelectItem>
              <SelectItem value="portrait">إجباري عمودي</SelectItem>
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">WebView هنا مجرد عارض موقع، لذلك نعرض الرابط ووضعية الشاشة فقط.</p>
        </div>
      )}

      {/* Intent URI */}
      {actionType === 'intent' && (
        <div className="space-y-2">
          <Label>Intent URI</Label>
          <Input
            value={config.intentUri || ''}
            onChange={(e) => updateConfig({ intentUri: e.target.value })}
            placeholder="intent://..."
            className="bg-secondary border-border font-mono text-xs"
            dir="ltr"
          />
        </div>
      )}

      {/* ============ 1️⃣ Custom Headers & DRM License Headers ============ */}
      {showAdvancedSections && (
        <div className="space-y-3 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <Shield className="w-4 h-4" />
            1️⃣ الترويسات المخصصة وحماية DRM
          </Label>
          
          {/* Standard Headers */}
          <div className="grid grid-cols-1 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">User-Agent</Label>
              <Input value={config.headers?.userAgent || ''} onChange={(e) => updateHeaders('userAgent', e.target.value)} placeholder="Mozilla/5.0..." className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Referer</Label>
              <Input value={config.headers?.referrer || ''} onChange={(e) => updateHeaders('referrer', e.target.value)} placeholder="https://example.com" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Cookie</Label>
              <Input value={config.headers?.cookie || ''} onChange={(e) => updateHeaders('cookie', e.target.value)} placeholder="session=abc123" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Origin</Label>
              <Input value={config.headers?.origin || ''} onChange={(e) => updateHeaders('origin', e.target.value)} placeholder="https://example.com" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
          </div>

          {/* Custom Headers (Key:Value pairs) */}
          <div className="space-y-2 p-3 rounded-lg bg-secondary/50 border border-border">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-bold">ترويسات إضافية (Custom Headers)</Label>
              <Button type="button" variant="outline" size="sm" onClick={addCustomHeader}>
                <Plus className="w-3 h-3 mr-1" /> إضافة
              </Button>
            </div>
            {(config.customHeaders || []).map((h, i) => (
              <div key={i} className="flex gap-2 items-center">
                <Input value={h.key} onChange={(e) => updateCustomHeader(i, 'key', e.target.value)} placeholder="Header-Name" className="bg-secondary border-border font-mono text-xs flex-1" dir="ltr" />
                <Input value={h.value} onChange={(e) => updateCustomHeader(i, 'value', e.target.value)} placeholder="Header-Value" className="bg-secondary border-border font-mono text-xs flex-1" dir="ltr" />
                <Button type="button" variant="ghost" size="sm" onClick={() => removeCustomHeader(i)}><Trash2 className="w-3 h-3 text-destructive" /></Button>
              </div>
            ))}
          </div>

          {/* DRM License Headers */}
          <div className="space-y-2 p-3 rounded-lg bg-yellow-950/20 border border-yellow-600/30">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-bold text-yellow-400">ترويسات خادم الترخيص (DRM License Headers)</Label>
              <Button type="button" variant="outline" size="sm" onClick={addDrmLicenseHeader}>
                <Plus className="w-3 h-3 mr-1" /> إضافة
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">تُرسل فقط مع طلب License Server لتمرير توكنات الحماية</p>
            {(config.drmLicenseHeaders || []).map((h, i) => (
              <div key={i} className="flex gap-2 items-center">
                <Input value={h.key} onChange={(e) => updateDrmLicenseHeader(i, 'key', e.target.value)} placeholder="Authorization" className="bg-secondary border-border font-mono text-xs flex-1" dir="ltr" />
                <Input value={h.value} onChange={(e) => updateDrmLicenseHeader(i, 'value', e.target.value)} placeholder="Bearer token..." className="bg-secondary border-border font-mono text-xs flex-1" dir="ltr" />
                <Button type="button" variant="ghost" size="sm" onClick={() => removeDrmLicenseHeader(i)}><Trash2 className="w-3 h-3 text-destructive" /></Button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ============ 2️⃣ Backup Server, Audio Sources, Subtitles ============ */}
      {showAdvancedSections && (
        <div className="space-y-3 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <Server className="w-4 h-4" />
            2️⃣ الخوادم البديلة، الصوت، والترجمة
          </Label>

          {/* Backup Server URL (legacy single fallback) */}
          <div className="space-y-1">
            <Label className="text-xs">رابط السيرفر البديل (Backup — بسيط)</Label>
            <Input value={config.backupUrl || ''} onChange={(e) => updateConfig({ backupUrl: e.target.value })} placeholder="https://backup.example.com/live.m3u8" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            <p className="text-xs text-muted-foreground">رابط واحد بسيط — للحالات البسيطة. للسيرفرات المتعددة بقوة كاملة استخدم القائمة أدناه.</p>
          </div>

          {/* Advanced fallback servers (array with full power) */}
          <FallbackServersEditor
            servers={(config.fallbackServers as any) || []}
            onChange={(srv) => updateConfig({ fallbackServers: srv as any })}
          />

          {/* Multi-Source Audio */}
          <div className="space-y-2 p-3 rounded-lg bg-secondary/50 border border-border">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-bold flex items-center gap-1"><Volume2 className="w-3 h-3" /> مصادر الصوت الإضافية</Label>
              <Button type="button" variant="outline" size="sm" onClick={addAudioSource}>
                <Plus className="w-3 h-3 mr-1" /> إضافة
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">روابط صوتية مستقلة تظهر كخيار تبديل صوت داخل المشغل</p>
            {(config.audioSources || []).map((a, i) => (
              <div key={i} className="flex gap-2 items-center">
                <Input value={a.name} onChange={(e) => updateAudioSource(i, 'name', e.target.value)} placeholder="تعليق عربي" className="bg-secondary border-border text-xs w-1/3" />
                <Input value={a.url} onChange={(e) => updateAudioSource(i, 'url', e.target.value)} placeholder="https://audio.m3u8" className="bg-secondary border-border font-mono text-xs flex-1" dir="ltr" />
                <Button type="button" variant="ghost" size="sm" onClick={() => removeAudioSource(i)}><Trash2 className="w-3 h-3 text-destructive" /></Button>
              </div>
            ))}
          </div>

          {/* External Subtitle URL */}
          <div className="space-y-1">
            <Label className="text-xs flex items-center gap-1"><Subtitles className="w-3 h-3" /> رابط الترجمة الخارجية (SRT/VTT)</Label>
            <Input value={config.subtitleUrl || ''} onChange={(e) => updateConfig({ subtitleUrl: e.target.value })} placeholder="https://example.com/subs.vtt" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
          </div>
        </div>
      )}

      {/* ============ 3️⃣ Dynamic API Fetching ============ */}
      {showAdvancedSections && (
        <div className="space-y-3 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <Globe className="w-4 h-4" />
            3️⃣ الجلب الديناميكي (Dynamic API)
          </Label>
          <div className="flex items-center justify-between">
            <Label className="text-xs">تفعيل Dynamic API Request</Label>
            <Switch
              checked={config.dynamicApi?.enabled || false}
              onCheckedChange={(checked) => updateConfig({ dynamicApi: { ...config.dynamicApi, enabled: checked, endpoint: config.dynamicApi?.endpoint || '', method: config.dynamicApi?.method || 'GET' } })}
            />
          </div>
          {config.dynamicApi?.enabled && (
            <div className="space-y-2 p-3 rounded-lg bg-blue-950/20 border border-blue-600/30">
              <div className="space-y-1">
                <Label className="text-xs">API Endpoint</Label>
                <Input value={config.dynamicApi?.endpoint || ''} onChange={(e) => updateConfig({ dynamicApi: { ...config.dynamicApi, enabled: true, endpoint: e.target.value } })} placeholder="https://api.example.com/stream" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Method</Label>
                <Select value={config.dynamicApi?.method || 'GET'} onValueChange={(v) => updateConfig({ dynamicApi: { ...config.dynamicApi, enabled: true, method: v as 'GET' | 'POST' } })}>
                  <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
                  <SelectContent className="bg-popover border-border z-50">
                    <SelectItem value="GET">GET</SelectItem>
                    <SelectItem value="POST">POST</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Channel ID Param</Label>
                <Input value={config.dynamicApi?.channelIdParam || ''} onChange={(e) => updateConfig({ dynamicApi: { ...config.dynamicApi, enabled: true, channelIdParam: e.target.value } })} placeholder="channel_id" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              </div>
              <p className="text-xs text-muted-foreground">يرسل طلباً للحصول على رابط بث متجدد + ترويسات + مفاتيح DRM</p>
            </div>
          )}
        </div>
      )}

      {/* ============ 4️⃣ Screen Mode Lock ============ */}
      {showAdvancedSections && (
        <div className="space-y-3 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <Monitor className="w-4 h-4" />
            4️⃣ قفل وضعية الشاشة (Screen Mode Lock)
          </Label>
          <div className="space-y-2">
            <Label className="text-xs">وضعية العرض الافتراضية</Label>
            <Select value={config.forcedAspectRatio || 'original'} onValueChange={(v) => updateConfig({ forcedAspectRatio: v as AspectRatioMode })}>
              <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-popover border-border z-50">
                <SelectItem value="original">Original (أصلي)</SelectItem>
                <SelectItem value="fit">Fit (ملائم)</SelectItem>
                <SelectItem value="stretch">Stretch (تمدد)</SelectItem>
                <SelectItem value="16:9">16:9</SelectItem>
                <SelectItem value="4:3">4:3</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-xs">قفل تغيير الأبعاد</Label>
              <p className="text-xs text-muted-foreground">منع المستخدم من تغيير وضعية العرض</p>
            </div>
            <Switch checked={config.lockAspectRatio || false} onCheckedChange={(checked) => updateConfig({ lockAspectRatio: checked })} />
          </div>
        </div>
      )}

      {/* ============ 5️⃣ Smart Logo Overlay ============ */}
      {showAdvancedSections && (
        <div className="space-y-3 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <Image className="w-4 h-4" />
            5️⃣ الشعار الذكي (Smart Logo Overlay)
          </Label>
          <div className="space-y-2">
            <div className="space-y-1">
              <Label className="text-xs">رابط الشعار</Label>
              <Input value={config.logoOverlay?.url || ''} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, url: e.target.value, position: config.logoOverlay?.position || 'top-right' } })} placeholder="https://example.com/logo.png" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">الموقع</Label>
              <Select value={config.logoOverlay?.position || 'top-right'} onValueChange={(v) => updateConfig({ logoOverlay: { ...config.logoOverlay, position: v as LogoOverlay['position'] } })}>
                <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
                <SelectContent className="bg-popover border-border z-50">
                  <SelectItem value="top-left">أعلى يسار</SelectItem>
                  <SelectItem value="top-right">أعلى يمين</SelectItem>
                  <SelectItem value="bottom-left">أسفل يسار</SelectItem>
                  <SelectItem value="bottom-right">أسفل يمين</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label className="text-xs">إزاحة X</Label>
                <Input type="number" value={config.logoOverlay?.offsetX ?? 0} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, offsetX: parseInt(e.target.value) || 0 } })} className="bg-secondary border-border text-xs" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">إزاحة Y</Label>
                <Input type="number" value={config.logoOverlay?.offsetY ?? 0} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, offsetY: parseInt(e.target.value) || 0 } })} className="bg-secondary border-border text-xs" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">العرض (px)</Label>
                <Input type="number" value={config.logoOverlay?.width ?? 80} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, width: parseInt(e.target.value) || 80 } })} className="bg-secondary border-border text-xs" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">الارتفاع (px)</Label>
                <Input type="number" value={config.logoOverlay?.height ?? 40} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, height: parseInt(e.target.value) || 40 } })} className="bg-secondary border-border text-xs" />
              </div>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">الشفافية (0-1)</Label>
              <Input type="number" step="0.1" min="0" max="1" value={config.logoOverlay?.opacity ?? 1} onChange={(e) => updateConfig({ logoOverlay: { ...config.logoOverlay, opacity: parseFloat(e.target.value) || 1 } })} className="bg-secondary border-border text-xs" />
            </div>
          </div>
        </div>
      )}

      {/* ============ DRM Section ============ */}
      {showAdvancedSections && (
        <div className="space-y-4 pt-2 border-t border-border">
          <Label className="text-sm text-muted-foreground flex items-center gap-2">
            <FileCode className="w-4 h-4" />
            إعدادات DRM / ClearKey (Native / Hybrid / WebView)
          </Label>
          
          <div className="space-y-2">
            <Label className="text-xs">نوع DRM</Label>
            <Select value={config.drmScheme || 'clearkey'} onValueChange={(value: DrmScheme) => updateConfig({ drmScheme: value })}>
              <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-popover border-border z-50">
                <SelectItem value="clearkey">ClearKey</SelectItem>
                <SelectItem value="widevine">Widevine</SelectItem>
                <SelectItem value="playready">PlayReady</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {config.drmScheme === 'clearkey' && (
            <div className="space-y-3 p-3 rounded-lg bg-yellow-950/20 border border-yellow-600/30">
              <Label className="text-xs text-yellow-400 flex items-center gap-2">
                <Key className="w-3 h-3" /> طريقة إدخال ClearKey
              </Label>
              <RadioGroup value={clearKeyMode} onValueChange={(value: ClearKeyMode) => { setClearKeyMode(value); updateConfig({ drmClearKeyMode: value }); }} className="flex flex-col gap-2">
                <div className="flex items-center gap-2"><RadioGroupItem value="separate" id="ck-separate" /><Label htmlFor="ck-separate" className="text-xs cursor-pointer">منفصل (Key ID + Key)</Label></div>
                <div className="flex items-center gap-2"><RadioGroupItem value="combined" id="ck-combined" /><Label htmlFor="ck-combined" className="text-xs cursor-pointer">مدمج (KeyID:Key)</Label></div>
                <div className="flex items-center gap-2"><RadioGroupItem value="url" id="ck-url" /><Label htmlFor="ck-url" className="text-xs cursor-pointer">رابط ديناميكي (URL)</Label></div>
              </RadioGroup>

              {clearKeyMode === 'separate' && (
                <div className="space-y-2">
                  <Input value={config.drmKeyId || ''} onChange={(e) => updateConfig({ drmKeyId: e.target.value })} placeholder="Key ID" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                  <Input value={config.drmKey || ''} onChange={(e) => updateConfig({ drmKey: e.target.value })} placeholder="Key" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
              )}
              {clearKeyMode === 'combined' && (
                <Input value={config.drmClearKeyCombined || ''} onChange={(e) => updateConfig({ drmClearKeyCombined: e.target.value })} placeholder="KeyID:Key" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              )}
              {clearKeyMode === 'url' && (
                <Input value={config.drmLicenseUrl || ''} onChange={(e) => updateConfig({ drmLicenseUrl: e.target.value })} placeholder="https://license.example.com/clearkey" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              )}
            </div>
          )}
          
          {(config.drmScheme === 'widevine' || config.drmScheme === 'playready') && (
            <div className="space-y-1">
              <Label className="text-xs">رابط الرخصة (License URL)</Label>
              <Input value={config.drmLicenseUrl || ''} onChange={(e) => updateConfig({ drmLicenseUrl: e.target.value })} placeholder="https://license.example.com/drm" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default AndroidConfigForm;
