import React from 'react';
import type { AudioSource, ClearKeyMode, CustomHeader, DRMConfig, LogoOverlay, StreamConfig, AspectRatioMode } from '@/types/admin';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Cookie, Globe, ImageIcon, Key, Link, LinkIcon, Monitor, Plus, Server, Shield, Subtitles, Trash2, Volume2 } from 'lucide-react';

interface PlayerConfigFormProps {
  streamConfig: StreamConfig;
  onChange: (config: StreamConfig) => void;
}

const PlayerConfigForm: React.FC<PlayerConfigFormProps> = ({ streamConfig, onChange }) => {
  const updateField = <K extends keyof StreamConfig>(field: K, value: StreamConfig[K]) => {
    onChange({ ...streamConfig, [field]: value });
  };

  const updateDRM = <K extends keyof DRMConfig>(field: K, value: DRMConfig[K]) => {
    onChange({
      ...streamConfig,
      drm: { ...streamConfig.drm, [field]: value },
    });
  };

  const clearKeyMode = streamConfig.drm?.clearKeyMode || 'separate';

  const updateCustomHeaders = (updater: (items: CustomHeader[]) => CustomHeader[]) => {
    onChange({ ...streamConfig, customHeaders: updater([...(streamConfig.customHeaders || [])]) });
  };

  const updateDrmLicenseHeaders = (updater: (items: CustomHeader[]) => CustomHeader[]) => {
    onChange({ ...streamConfig, drmLicenseHeaders: updater([...(streamConfig.drmLicenseHeaders || [])]) });
  };

  const updateAudioSources = (updater: (items: AudioSource[]) => AudioSource[]) => {
    onChange({ ...streamConfig, audioSources: updater([...(streamConfig.audioSources || [])]) });
  };

  const updateLogoOverlay = (updates: Partial<LogoOverlay>) => {
    onChange({
      ...streamConfig,
      logoOverlay: {
        url: streamConfig.logoOverlay?.url || '',
        position: streamConfig.logoOverlay?.position || 'top-right',
        offsetX: streamConfig.logoOverlay?.offsetX || 0,
        offsetY: streamConfig.logoOverlay?.offsetY || 0,
        width: streamConfig.logoOverlay?.width || 80,
        height: streamConfig.logoOverlay?.height || 40,
        opacity: streamConfig.logoOverlay?.opacity ?? 1,
        ...updates,
      },
    });
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <h4 className="flex items-center gap-2 font-semibold text-foreground">
          <Link className="h-4 w-4 text-primary" />
          إعدادات البث
        </h4>

        <div className="space-y-2">
          <Label>رابط البث</Label>
          <Input value={streamConfig.url || ''} onChange={(e) => updateField('url', e.target.value)} placeholder="https://example.com/stream.m3u8" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
        </div>
      </div>

      <Separator className="bg-border" />

      <div className="space-y-4">
        <h4 className="flex items-center gap-2 font-semibold text-foreground">
          <Globe className="h-4 w-4 text-primary" />
          الترويسات والحماية
        </h4>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label>User-Agent</Label>
            <Input value={streamConfig.userAgent || ''} onChange={(e) => updateField('userAgent', e.target.value)} className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>Referrer</Label>
            <Input value={streamConfig.referrer || ''} onChange={(e) => updateField('referrer', e.target.value)} className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>Origin</Label>
            <Input value={streamConfig.origin || ''} onChange={(e) => updateField('origin', e.target.value)} className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
          <div className="space-y-2">
            <Label>Cookies</Label>
            <Textarea value={streamConfig.cookies || ''} onChange={(e) => updateField('cookies', e.target.value)} className="bg-secondary border-border font-mono text-sm min-h-[44px]" dir="ltr" />
          </div>
        </div>

        <div className="space-y-3 rounded-xl border border-border bg-muted/40 p-4">
          <div className="flex items-center justify-between">
            <Label>Custom Headers</Label>
            <Button type="button" variant="outline" size="sm" onClick={() => updateCustomHeaders((items) => [...items, { key: '', value: '' }])}><Plus className="mr-1 h-3 w-3" />إضافة</Button>
          </div>
          {(streamConfig.customHeaders || []).map((header, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input value={header.key} onChange={(e) => updateCustomHeaders((items) => items.map((item, i) => i === index ? { ...item, key: e.target.value } : item))} placeholder="Header-Name" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              <Input value={header.value} onChange={(e) => updateCustomHeaders((items) => items.map((item, i) => i === index ? { ...item, value: e.target.value } : item))} placeholder="Header-Value" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              <Button type="button" variant="ghost" size="icon" onClick={() => updateCustomHeaders((items) => items.filter((_, i) => i !== index))}><Trash2 className="h-4 w-4 text-destructive" /></Button>
            </div>
          ))}
        </div>
      </div>

      <Separator className="bg-border" />

      <div className="space-y-4">
        <h4 className="flex items-center gap-2 font-semibold text-foreground">
          <Shield className="h-4 w-4 text-primary" />
          إعدادات DRM / ClearKey
        </h4>

        <div className="space-y-3">
          <Label>طريقة إدخال ClearKey</Label>
          <RadioGroup value={clearKeyMode} onValueChange={(value: ClearKeyMode) => updateDRM('clearKeyMode', value)} className="flex flex-col gap-2">
            <div className="flex items-center gap-2"><RadioGroupItem value="separate" id="ck-separate" /><Label htmlFor="ck-separate">منفصل</Label></div>
            <div className="flex items-center gap-2"><RadioGroupItem value="combined" id="ck-combined" /><Label htmlFor="ck-combined">مدمج</Label></div>
            <div className="flex items-center gap-2"><RadioGroupItem value="url" id="ck-url" /><Label htmlFor="ck-url">رابط URL</Label></div>
          </RadioGroup>
        </div>

        {clearKeyMode === 'separate' && (
          <div className="grid gap-4 md:grid-cols-2">
            <Input value={streamConfig.drm?.clearKeyId || ''} onChange={(e) => updateDRM('clearKeyId', e.target.value)} placeholder="ClearKey ID" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
            <Input value={streamConfig.drm?.clearKeyKey || ''} onChange={(e) => updateDRM('clearKeyKey', e.target.value)} placeholder="ClearKey Key" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
        )}

        {clearKeyMode === 'combined' && (
          <Input value={streamConfig.drm?.clearKeyCombined || ''} onChange={(e) => updateDRM('clearKeyCombined', e.target.value)} placeholder="KeyID:Key" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
        )}

        {clearKeyMode === 'url' && (
          <div className="space-y-2">
            <Label className="flex items-center gap-2"><LinkIcon className="h-4 w-4" />رابط ClearKey</Label>
            <Input value={streamConfig.drm?.clearKeyUrl || ''} onChange={(e) => updateDRM('clearKeyUrl', e.target.value)} placeholder="https://api.example.com/keys" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          </div>
        )}

        <div className="space-y-3 rounded-xl border border-yellow-600/30 bg-yellow-950/20 p-4">
          <div className="flex items-center justify-between">
            <Label>DRM License Headers</Label>
            <Button type="button" variant="outline" size="sm" onClick={() => updateDrmLicenseHeaders((items) => [...items, { key: '', value: '' }])}><Plus className="mr-1 h-3 w-3" />إضافة</Button>
          </div>
          {(streamConfig.drmLicenseHeaders || []).map((header, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input value={header.key} onChange={(e) => updateDrmLicenseHeaders((items) => items.map((item, i) => i === index ? { ...item, key: e.target.value } : item))} placeholder="Authorization" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              <Input value={header.value} onChange={(e) => updateDrmLicenseHeaders((items) => items.map((item, i) => i === index ? { ...item, value: e.target.value } : item))} placeholder="Bearer token" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              <Button type="button" variant="ghost" size="icon" onClick={() => updateDrmLicenseHeaders((items) => items.filter((_, i) => i !== index))}><Trash2 className="h-4 w-4 text-destructive" /></Button>
            </div>
          ))}
        </div>
      </div>

      <Separator className="bg-border" />

      <div className="space-y-4">
        <h4 className="flex items-center gap-2 font-semibold text-foreground"><Server className="h-4 w-4 text-primary" />الخادم البديل، الصوت والترجمة</h4>
        <div className="space-y-2">
          <Label>Backup Server URL</Label>
          <Input value={streamConfig.backupUrl || ''} onChange={(e) => updateField('backupUrl', e.target.value)} className="bg-secondary border-border font-mono text-sm" dir="ltr" />
        </div>
        <div className="space-y-3 rounded-xl border border-border bg-muted/40 p-4">
          <div className="flex items-center justify-between">
            <Label className="flex items-center gap-2"><Volume2 className="h-4 w-4" />مصادر الصوت الإضافية</Label>
            <Button type="button" variant="outline" size="sm" onClick={() => updateAudioSources((items) => [...items, { name: '', url: '' }])}><Plus className="mr-1 h-3 w-3" />إضافة</Button>
          </div>
          {(streamConfig.audioSources || []).map((source, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input value={source.name} onChange={(e) => updateAudioSources((items) => items.map((item, i) => i === index ? { ...item, name: e.target.value } : item))} placeholder="تعليق عربي" className="bg-secondary border-border text-xs" />
              <Input value={source.url} onChange={(e) => updateAudioSources((items) => items.map((item, i) => i === index ? { ...item, url: e.target.value } : item))} placeholder="https://audio.example.com/track.m3u8" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
              <Button type="button" variant="ghost" size="icon" onClick={() => updateAudioSources((items) => items.filter((_, i) => i !== index))}><Trash2 className="h-4 w-4 text-destructive" /></Button>
            </div>
          ))}
        </div>
        <div className="space-y-2">
          <Label className="flex items-center gap-2"><Subtitles className="h-4 w-4" />External Subtitle URL</Label>
          <Input value={streamConfig.subtitleUrl || ''} onChange={(e) => updateField('subtitleUrl', e.target.value)} className="bg-secondary border-border font-mono text-sm" dir="ltr" />
        </div>
      </div>

      <Separator className="bg-border" />

      <div className="space-y-4">
        <h4 className="flex items-center gap-2 font-semibold text-foreground"><Monitor className="h-4 w-4 text-primary" />القفل والشعار الذكي</h4>
        <div className="space-y-2">
          <Label>Forced Aspect Ratio</Label>
          <Select value={streamConfig.forcedAspectRatio || 'original'} onValueChange={(value: AspectRatioMode) => updateField('forcedAspectRatio', value)}>
            <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="original">Original</SelectItem>
              <SelectItem value="fit">Fit</SelectItem>
              <SelectItem value="stretch">Stretch</SelectItem>
              <SelectItem value="16:9">16:9</SelectItem>
              <SelectItem value="4:3">4:3</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center justify-between rounded-xl border border-border bg-muted/40 p-4">
          <div>
            <Label>Lock User Interaction</Label>
            <p className="text-xs text-muted-foreground mt-1">تعطيل تغيير الأبعاد داخل المشغل.</p>
          </div>
          <Switch checked={streamConfig.lockAspectRatio || false} onCheckedChange={(checked) => updateField('lockAspectRatio', checked)} />
        </div>
        <div className="grid gap-3 rounded-xl border border-border bg-muted/40 p-4">
          <Label className="flex items-center gap-2"><ImageIcon className="h-4 w-4" />Smart Logo Overlay</Label>
          <Input value={streamConfig.logoOverlay?.url || ''} onChange={(e) => updateLogoOverlay({ url: e.target.value })} placeholder="https://example.com/logo.png" className="bg-secondary border-border font-mono text-sm" dir="ltr" />
          <Select value={streamConfig.logoOverlay?.position || 'top-right'} onValueChange={(value: LogoOverlay['position']) => updateLogoOverlay({ position: value })}>
            <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="top-left">أعلى يسار</SelectItem>
              <SelectItem value="top-right">أعلى يمين</SelectItem>
              <SelectItem value="bottom-left">أسفل يسار</SelectItem>
              <SelectItem value="bottom-right">أسفل يمين</SelectItem>
            </SelectContent>
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <Input type="number" value={streamConfig.logoOverlay?.offsetX ?? 0} onChange={(e) => updateLogoOverlay({ offsetX: Number(e.target.value) || 0 })} placeholder="Offset X" className="bg-secondary border-border text-xs" />
            <Input type="number" value={streamConfig.logoOverlay?.offsetY ?? 0} onChange={(e) => updateLogoOverlay({ offsetY: Number(e.target.value) || 0 })} placeholder="Offset Y" className="bg-secondary border-border text-xs" />
            <Input type="number" value={streamConfig.logoOverlay?.width ?? 80} onChange={(e) => updateLogoOverlay({ width: Number(e.target.value) || 80 })} placeholder="Width" className="bg-secondary border-border text-xs" />
            <Input type="number" value={streamConfig.logoOverlay?.height ?? 40} onChange={(e) => updateLogoOverlay({ height: Number(e.target.value) || 40 })} placeholder="Height" className="bg-secondary border-border text-xs" />
          </div>
          <Input type="number" min="0" max="1" step="0.1" value={streamConfig.logoOverlay?.opacity ?? 1} onChange={(e) => updateLogoOverlay({ opacity: Number(e.target.value) || 1 })} placeholder="Opacity" className="bg-secondary border-border text-xs" />
        </div>
      </div>
    </div>
  );
};

export default PlayerConfigForm;