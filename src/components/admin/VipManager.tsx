import { useEffect, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { useToast } from '@/hooks/use-toast';
import { Crown, Plus, Trash2, X } from 'lucide-react';

interface VipRow {
  id: string;
  username: string;
  notes: string | null;
  starts_at: string;
  expires_at: string;
  device_ids: string[];
  active: boolean;
}

const empty = { username: '', notes: '', expires_at: '', device_ids: ['', '', '', '', ''], active: true };

export default function VipManager() {
  const { toast } = useToast();
  const [rows, setRows] = useState<VipRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState(empty);
  const [editingId, setEditingId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    // Read through admin-write helper-style: fall back to direct rpc via service-role function isn't available.
    // We expose a tiny read here using the admin-write 'select' isn't supported, so call a dedicated edge function.
    const { data, error } = await supabase.functions.invoke('list-vip', { body: {} });
    if (error || !data?.success) {
      toast({ title: 'فشل التحميل', description: data?.error || error?.message || '', variant: 'destructive' });
      setRows([]);
    } else {
      setRows((data.rows || []) as VipRow[]);
    }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  // Realtime
  useEffect(() => {
    const ch = supabase.channel('vip_subs').on(
      'postgres_changes',
      { event: '*', schema: 'public', table: 'vip_subscriptions' },
      () => load(),
    ).subscribe();
    return () => { supabase.removeChannel(ch); };
  }, []);

  const save = async () => {
    const devices = draft.device_ids.map((d) => d.trim()).filter(Boolean);
    if (devices.length > 5) { toast({ title: 'الحد الأقصى 5 أجهزة', variant: 'destructive' }); return; }
    if (!draft.username.trim() || !draft.expires_at) {
      toast({ title: 'الاسم وتاريخ الانتهاء مطلوبان', variant: 'destructive' });
      return;
    }
    const payload = {
      username: draft.username.trim(),
      notes: draft.notes.trim() || null,
      expires_at: new Date(draft.expires_at).toISOString(),
      device_ids: devices,
      active: draft.active,
    };
    try {
      if (editingId) {
        await adminDb.update('vip_subscriptions', { id: editingId }, payload, true);
        toast({ title: 'تم التحديث' });
      } else {
        await adminDb.insert('vip_subscriptions', payload, false, true);
        toast({ title: 'تم الإضافة' });
      }
      setDraft(empty);
      setEditingId(null);
      load();
    } catch (e: any) {
      toast({ title: 'فشل الحفظ', description: e?.message || '', variant: 'destructive' });
    }
  };

  const edit = (r: VipRow) => {
    setEditingId(r.id);
    const devs = [...r.device_ids];
    while (devs.length < 5) devs.push('');
    setDraft({
      username: r.username,
      notes: r.notes || '',
      expires_at: r.expires_at.slice(0, 16),
      device_ids: devs.slice(0, 5),
      active: r.active,
    });
  };

  const del = async (id: string) => {
    if (!confirm('حذف الاشتراك؟')) return;
    try {
      await adminDb.delete('vip_subscriptions', { id }, true);
      toast({ title: 'تم الحذف' });
      load();
    } catch (e: any) {
      toast({ title: 'فشل الحذف', description: e?.message || '', variant: 'destructive' });
    }
  };

  return (
    <div className="bg-card rounded-2xl p-6 border border-border space-y-6">
      <div className="flex items-center gap-2">
        <Crown className="w-5 h-5 text-primary" />
        <h3 className="text-lg font-bold text-foreground">اشتراكات VIP</h3>
      </div>

      {/* Form */}
      <div className="space-y-3 p-4 border border-border rounded-xl">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <Label>اسم المستخدم</Label>
            <Input value={draft.username} onChange={(e) => setDraft({ ...draft, username: e.target.value })} />
          </div>
          <div>
            <Label>تاريخ الانتهاء</Label>
            <Input type="datetime-local" value={draft.expires_at} onChange={(e) => setDraft({ ...draft, expires_at: e.target.value })} />
          </div>
        </div>
        <div>
          <Label>ملاحظات</Label>
          <Textarea value={draft.notes} onChange={(e) => setDraft({ ...draft, notes: e.target.value })} rows={2} />
        </div>
        <div>
          <Label>الأجهزة المرتبطة (حتى 5)</Label>
          <div className="space-y-2 mt-2">
            {draft.device_ids.map((d, i) => (
              <Input
                key={i}
                placeholder={`Device ID ${i + 1}`}
                value={d}
                onChange={(e) => {
                  const next = [...draft.device_ids];
                  next[i] = e.target.value;
                  setDraft({ ...draft, device_ids: next });
                }}
              />
            ))}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Switch checked={draft.active} onCheckedChange={(v) => setDraft({ ...draft, active: v })} />
          <span className="text-sm">مفعّل</span>
        </div>
        <div className="flex gap-2">
          <Button onClick={save}><Plus className="w-4 h-4 mr-1" />{editingId ? 'تحديث' : 'إضافة'}</Button>
          {editingId && (
            <Button variant="ghost" onClick={() => { setDraft(empty); setEditingId(null); }}>
              <X className="w-4 h-4 mr-1" />إلغاء
            </Button>
          )}
        </div>
      </div>

      {/* List */}
      <div className="space-y-2">
        {loading ? <p className="text-muted-foreground text-sm">جارٍ التحميل…</p>
          : rows.length === 0 ? <p className="text-muted-foreground text-sm">لا توجد اشتراكات.</p>
          : rows.map((r) => {
            const exp = new Date(r.expires_at).getTime();
            const expired = exp < Date.now();
            return (
              <div key={r.id} className="flex items-start justify-between gap-3 p-3 border border-border rounded-xl">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-medium">{r.username}</span>
                    {!r.active && <span className="text-xs bg-muted px-2 py-0.5 rounded">معطّل</span>}
                    {expired && <span className="text-xs bg-destructive/20 text-destructive px-2 py-0.5 rounded">منتهٍ</span>}
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">ينتهي: {new Date(r.expires_at).toLocaleString('ar')}</p>
                  <p className="text-xs text-muted-foreground">أجهزة: {r.device_ids.length}/5</p>
                  {r.device_ids.length > 0 && (
                    <p className="text-[10px] font-mono text-muted-foreground/70 break-all mt-1">{r.device_ids.join(' • ')}</p>
                  )}
                </div>
                <div className="flex flex-col gap-1">
                  <Button size="sm" variant="ghost" onClick={() => edit(r)}>تعديل</Button>
                  <Button size="sm" variant="ghost" onClick={() => del(r.id)}>
                    <Trash2 className="w-4 h-4 text-destructive" />
                  </Button>
                </div>
              </div>
            );
          })}
      </div>
    </div>
  );
}
