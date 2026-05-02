import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Plus, Edit2, Trash2, Server, Shield, ChevronUp, ChevronDown } from 'lucide-react';
import type { CustomHeader, ClearKeyMode, DrmScheme } from '@/types/admin';

/**
 * Full-power fallback server entry. Each entry mirrors a primary stream:
 * URL + headers + custom headers + DRM. The Android player chains them
 * sequentially when the previous one fails or stops mid-playback.
 */
export interface FallbackServer {
  id: string;
  name: string;
  url: string;
  userAgent?: string;
  referer?: string;
  origin?: string;
  cookie?: string;
  customHeaders?: CustomHeader[];
  drmScheme?: DrmScheme | '';
  drmLicenseUrl?: string;
  drmKeyId?: string;
  drmKey?: string;
  drmClearKeyCombined?: string;
  drmClearKeyMode?: ClearKeyMode;
  drmLicenseHeaders?: CustomHeader[];
}

interface Props {
  servers: FallbackServer[];
  onChange: (servers: FallbackServer[]) => void;
}

const empty = (): FallbackServer => ({
  id: crypto.randomUUID(),
  name: 'سيرفر بديل',
  url: '',
  drmClearKeyMode: 'combined',
});

const FallbackServersEditor: React.FC<Props> = ({ servers, onChange }) => {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<FallbackServer | null>(null);

  const startAdd = () => { setDraft(empty()); setOpen(true); };
  const startEdit = (s: FallbackServer) => { setDraft({ ...s }); setOpen(true); };

  const save = () => {
    if (!draft) return;
    const exists = servers.some(s => s.id === draft.id);
    onChange(exists ? servers.map(s => s.id === draft.id ? draft : s) : [...servers, draft]);
    setOpen(false);
    setDraft(null);
  };

  const remove = (id: string) => onChange(servers.filter(s => s.id !== id));

  const move = (idx: number, dir: -1 | 1) => {
    const next = [...servers];
    const t = idx + dir;
    if (t < 0 || t >= next.length) return;
    [next[idx], next[t]] = [next[t], next[idx]];
    onChange(next);
  };

  const updateHeader = (key: 'customHeaders' | 'drmLicenseHeaders', updater: (h: CustomHeader[]) => CustomHeader[]) => {
    if (!draft) return;
    setDraft({ ...draft, [key]: updater([...(draft[key] || [])]) });
  };

  return (
    <div className="space-y-3 p-3 rounded-lg border border-border bg-secondary/40">
      <div className="flex items-center justify-between">
        <Label className="flex items-center gap-2 text-sm font-bold">
          <Server className="w-4 h-4 text-primary" />
          السيرفرات البديلة (قائمة متسلسلة)
        </Label>
        <Button type="button" size="sm" variant="outline" onClick={startAdd}>
          <Plus className="w-3 h-3 mr-1" />إضافة سيرفر بديل
        </Button>
      </div>

      <p className="text-xs text-muted-foreground">
        ينتقل المشغل تلقائياً للسيرفر التالي عند فشل أو توقف الحالي. كل سيرفر يدعم نفس إمكانيات السيرفر الأصلي (هيدرز / DRM).
      </p>

      {servers.length === 0 ? (
        <p className="text-xs text-muted-foreground text-center py-3">لا توجد سيرفرات بديلة بعد</p>
      ) : (
        <div className="space-y-2">
          {servers.map((s, i) => (
            <div key={s.id} className="flex items-center gap-2 p-2 rounded-md bg-background border border-border">
              <div className="flex flex-col gap-0.5">
                <Button type="button" size="icon" variant="ghost" className="h-5 w-5 p-0" disabled={i === 0} onClick={() => move(i, -1)}>
                  <ChevronUp className="w-3 h-3" />
                </Button>
                <Button type="button" size="icon" variant="ghost" className="h-5 w-5 p-0" disabled={i === servers.length - 1} onClick={() => move(i, 1)}>
                  <ChevronDown className="w-3 h-3" />
                </Button>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">
                  {i + 1}. {s.name || 'بدون اسم'}
                  {s.drmScheme ? <span className="text-xs text-amber-500 mr-2"><Shield className="inline w-3 h-3" /> {s.drmScheme}</span> : null}
                </p>
                <p className="text-xs text-muted-foreground font-mono truncate" dir="ltr">{s.url || '—'}</p>
              </div>
              <Button type="button" size="icon" variant="ghost" onClick={() => startEdit(s)}>
                <Edit2 className="w-4 h-4" />
              </Button>
              <Button type="button" size="icon" variant="ghost" className="text-destructive" onClick={() => remove(s.id)}>
                <Trash2 className="w-4 h-4" />
              </Button>
            </div>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="bg-card border-border max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader><DialogTitle>إعدادات السيرفر البديل</DialogTitle></DialogHeader>
          {draft && (
            <div className="space-y-4 py-2">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">الاسم</Label>
                  <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} className="bg-secondary border-border" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">رابط البث (URL)</Label>
                  <Input value={draft.url} onChange={(e) => setDraft({ ...draft, url: e.target.value })} placeholder="https://..." className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">User-Agent</Label>
                  <Input value={draft.userAgent || ''} onChange={(e) => setDraft({ ...draft, userAgent: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Referer</Label>
                  <Input value={draft.referer || ''} onChange={(e) => setDraft({ ...draft, referer: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Origin</Label>
                  <Input value={draft.origin || ''} onChange={(e) => setDraft({ ...draft, origin: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Cookie</Label>
                  <Input value={draft.cookie || ''} onChange={(e) => setDraft({ ...draft, cookie: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                </div>
              </div>

              <div className="space-y-2 p-3 rounded-md bg-background border border-border">
                <div className="flex items-center justify-between">
                  <Label className="text-xs font-bold">Custom Headers</Label>
                  <Button type="button" size="sm" variant="outline" onClick={() => updateHeader('customHeaders', (items) => [...items, { key: '', value: '' }])}>
                    <Plus className="w-3 h-3 mr-1" />إضافة
                  </Button>
                </div>
                {(draft.customHeaders || []).map((h, idx) => (
                  <div key={idx} className="flex gap-2">
                    <Input value={h.key} onChange={(e) => updateHeader('customHeaders', (items) => items.map((x, i) => i === idx ? { ...x, key: e.target.value } : x))} placeholder="Header" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                    <Input value={h.value} onChange={(e) => updateHeader('customHeaders', (items) => items.map((x, i) => i === idx ? { ...x, value: e.target.value } : x))} placeholder="Value" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                    <Button type="button" size="icon" variant="ghost" onClick={() => updateHeader('customHeaders', (items) => items.filter((_, i) => i !== idx))}>
                      <Trash2 className="w-4 h-4 text-destructive" />
                    </Button>
                  </div>
                ))}
              </div>

              <div className="space-y-3 p-3 rounded-md bg-yellow-950/20 border border-yellow-600/30">
                <Label className="text-xs font-bold text-yellow-400 flex items-center gap-1">
                  <Shield className="w-3 h-3" />إعدادات الحماية (DRM)
                </Label>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs">DRM Scheme</Label>
                    <Select value={draft.drmScheme || ''} onValueChange={(v) => setDraft({ ...draft, drmScheme: (v as DrmScheme) || '' })}>
                      <SelectTrigger className="bg-secondary border-border"><SelectValue placeholder="بدون DRM" /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="clearkey">ClearKey</SelectItem>
                        <SelectItem value="widevine">Widevine</SelectItem>
                        <SelectItem value="playready">PlayReady</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">License URL</Label>
                    <Input value={draft.drmLicenseUrl || ''} onChange={(e) => setDraft({ ...draft, drmLicenseUrl: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                  </div>
                </div>

                <div className="space-y-1">
                  <Label className="text-xs">طريقة ClearKey</Label>
                  <Select value={draft.drmClearKeyMode || 'combined'} onValueChange={(v: ClearKeyMode) => setDraft({ ...draft, drmClearKeyMode: v })}>
                    <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="combined">مدمج (KID:KEY)</SelectItem>
                      <SelectItem value="separate">منفصل</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {draft.drmClearKeyMode === 'combined' ? (
                  <div className="space-y-1">
                    <Label className="text-xs">KID:KEY</Label>
                    <Input value={draft.drmClearKeyCombined || ''} onChange={(e) => setDraft({ ...draft, drmClearKeyCombined: e.target.value })} className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <Input value={draft.drmKeyId || ''} onChange={(e) => setDraft({ ...draft, drmKeyId: e.target.value })} placeholder="Key ID" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                    <Input value={draft.drmKey || ''} onChange={(e) => setDraft({ ...draft, drmKey: e.target.value })} placeholder="Key" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                  </div>
                )}

                <div className="space-y-2 mt-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs">DRM License Headers</Label>
                    <Button type="button" size="sm" variant="outline" onClick={() => updateHeader('drmLicenseHeaders', (items) => [...items, { key: '', value: '' }])}>
                      <Plus className="w-3 h-3 mr-1" />إضافة
                    </Button>
                  </div>
                  {(draft.drmLicenseHeaders || []).map((h, idx) => (
                    <div key={idx} className="flex gap-2">
                      <Input value={h.key} onChange={(e) => updateHeader('drmLicenseHeaders', (items) => items.map((x, i) => i === idx ? { ...x, key: e.target.value } : x))} placeholder="Authorization" className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                      <Input value={h.value} onChange={(e) => updateHeader('drmLicenseHeaders', (items) => items.map((x, i) => i === idx ? { ...x, value: e.target.value } : x))} placeholder="Bearer ..." className="bg-secondary border-border font-mono text-xs" dir="ltr" />
                      <Button type="button" size="icon" variant="ghost" onClick={() => updateHeader('drmLicenseHeaders', (items) => items.filter((_, i) => i !== idx))}>
                        <Trash2 className="w-4 h-4 text-destructive" />
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>إلغاء</Button>
            <Button onClick={save} disabled={!draft?.url?.trim()}>حفظ</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default FallbackServersEditor;
