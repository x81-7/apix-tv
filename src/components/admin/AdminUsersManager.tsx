import React, { useEffect, useMemo, useState } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Shield, ShieldOff, Search, RefreshCw, Globe, Smartphone, AlertTriangle, UserCheck, Ban, Pencil, Check, X } from 'lucide-react';
import { toast } from 'sonner';

interface AppUser {
  id: string;
  device_id: string;
  ip_address: string | null;
  country: string | null;
  city: string | null;
  install_count: number;
  strike_count: number;
  status: string;
  ban_until: string | null;
  ban_reason: string | null;
  last_seen_at: string;
  app_version: string | null;
  custom_name: string | null;
}

const BANNED_STATUSES = new Set(['PERMA_BAN', 'TEMP_BAN', 'TAMPERED_MOD', 'ENVIRONMENT_DANGER']);

const statusColor = (s: string) => {
  switch (s) {
    case 'PERMA_BAN':
    case 'TAMPERED_MOD':
      return 'bg-destructive text-destructive-foreground';
    case 'TEMP_BAN':
      return 'bg-amber-600 text-white';
    case 'ENVIRONMENT_DANGER':
      return 'bg-red-700 text-white';
    default:
      return 'bg-emerald-600 text-white';
  }
};

type RowAction =
  | { kind: 'ban' | 'unban'; body: Record<string, unknown> }
  | { kind: 'rename'; body: { device_id: string; custom_name: string } };

const UserRow: React.FC<{ u: AppUser; onAction: (a: RowAction) => void }> = ({ u, onAction }) => {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(u.custom_name ?? '');
  return (
    <div className="p-3 rounded-lg bg-secondary border border-border space-y-2">
      <div className="flex items-start justify-between gap-2 flex-wrap">
        <div className="flex-1 min-w-0">
          {/* Custom name (admin label) */}
          {editing ? (
            <div className="flex items-center gap-2 mb-1">
              <Input
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="اسم وصفي مثل: جهاز محمد"
                className="h-7 text-sm bg-background border-border"
                maxLength={60}
              />
              <Button size="icon" variant="ghost" className="h-7 w-7"
                onClick={() => { onAction({ kind: 'rename', body: { device_id: u.device_id, custom_name: name } }); setEditing(false); }}>
                <Check className="w-3.5 h-3.5 text-emerald-500" />
              </Button>
              <Button size="icon" variant="ghost" className="h-7 w-7"
                onClick={() => { setName(u.custom_name ?? ''); setEditing(false); }}>
                <X className="w-3.5 h-3.5 text-muted-foreground" />
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2 mb-1">
              <p className="text-sm font-semibold text-foreground truncate">
                {u.custom_name?.trim() ? u.custom_name : <span className="text-muted-foreground italic font-normal">جهاز بدون اسم</span>}
              </p>
              <Button size="icon" variant="ghost" className="h-6 w-6" onClick={() => setEditing(true)}>
                <Pencil className="w-3 h-3" />
              </Button>
            </div>
          )}
          <p className="font-mono text-xs text-muted-foreground truncate">{u.device_id}</p>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground mt-1">
            <span className="flex items-center gap-1"><Globe className="w-3 h-3" />{u.country ?? '—'} {u.city ? `· ${u.city}` : ''}</span>
            <span>· IP: {u.ip_address ?? '—'}</span>
            <span className="flex items-center gap-1"><Smartphone className="w-3 h-3" />v{u.app_version ?? '?'}</span>
          </div>
          <div className="flex flex-wrap items-center gap-3 text-xs mt-2">
            <span>تثبيتات: <b className="text-foreground">{u.install_count}</b></span>
            <span>إنذارات: <b className="text-amber-500">{u.strike_count}</b></span>
            <span>آخر ظهور: {new Date(u.last_seen_at).toLocaleString('ar')}</span>
          </div>
          {u.ban_reason && (
            <p className="text-xs text-destructive mt-1 flex items-center gap-1">
              <AlertTriangle className="w-3 h-3" /> {u.ban_reason}
            </p>
          )}
        </div>
        <Badge className={statusColor(u.status)}>{u.status}</Badge>
      </div>
      <div className="flex gap-2 flex-wrap">
        {u.status === 'ACTIVE' ? (
          <>
            <Button size="sm" variant="outline" onClick={() => onAction({ kind: 'ban', body: { device_id: u.device_id, status: 'TEMP_BAN', reason: 'MANUAL_TEMP', minutes: 15 } })}>
              حظر مؤقت 15د
            </Button>
            <Button size="sm" variant="destructive" onClick={() => onAction({ kind: 'ban', body: { device_id: u.device_id, status: 'PERMA_BAN', reason: 'MANUAL_PERMA' } })}>
              <ShieldOff className="w-3 h-3 mr-1" />حظر دائم
            </Button>
          </>
        ) : (
          <Button size="sm" variant="outline" onClick={() => onAction({ kind: 'unban', body: { device_id: u.device_id } })}>
            رفع الحظر
          </Button>
        )}
      </div>
    </div>
  );
};

const AdminUsersManager: React.FC = () => {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const { data, error } = await supabase.functions.invoke('admin-ban', { method: 'GET' as any });
      if (error) throw error;
      setUsers((data?.users ?? []) as AppUser[]);
    } catch (e: any) {
      toast.error(`فشل التحميل: ${e?.message ?? 'خطأ'}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const callAction = async (a: RowAction) => {
    try {
      const { data, error } = await supabase.functions.invoke(`admin-ban?action=${a.kind}`, { body: a.body });
      if (error) throw error;
      if ((data as any)?.error) throw new Error((data as any).error);
      const successMsg =
        a.kind === 'ban' ? 'تم الحظر'
        : a.kind === 'unban' ? 'تم رفع الحظر'
        : 'تم تحديث الاسم';
      toast.success(successMsg);
      load();
    } catch (e: any) {
      toast.error(e?.message ?? 'خطأ');
    }
  };

  const unbanAll = async () => {
    if (!confirm('هل تريد فعلاً رفع الحظر عن جميع المستخدمين المحظورين؟ لا يمكن التراجع.')) return;
    try {
      const { data, error } = await supabase.functions.invoke('admin-ban?action=unban_all', { body: {} });
      if (error) throw error;
      if ((data as any)?.error) throw new Error((data as any).error);
      toast.success('تم رفع الحظر عن جميع المحظورين');
      load();
    } catch (e: any) {
      toast.error(`فشل: ${e?.message ?? 'خطأ'}`);
    }
  };

  const filtered = useMemo(() => {
    if (!search) return users;
    const q = search.toLowerCase();
    return users.filter(u =>
      u.device_id.toLowerCase().includes(q) ||
      (u.ip_address ?? '').includes(q) ||
      (u.country ?? '').toLowerCase().includes(q) ||
      (u.custom_name ?? '').toLowerCase().includes(q)
    );
  }, [users, search]);

  const activeUsers = filtered.filter(u => !BANNED_STATUSES.has(u.status));
  const bannedUsers = filtered.filter(u => BANNED_STATUSES.has(u.status));

  return (
    <Card className="border-border bg-card">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Shield className="w-5 h-5 text-primary" />
          إدارة المستخدمين والحظر
        </CardTitle>
        <Button size="sm" variant="outline" onClick={load} disabled={loading}>
          <RefreshCw className={`w-4 h-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          تحديث
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="relative">
          <Search className="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="بحث بالـ Device ID / IP / دولة..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pr-10 bg-secondary border-border"
          />
        </div>

        <Tabs defaultValue="active" className="w-full">
          <TabsList className="bg-secondary border border-border w-full">
            <TabsTrigger value="active" className="flex-1 data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <UserCheck className="w-4 h-4 mr-2" />
              المستخدمون العاديون ({activeUsers.length})
            </TabsTrigger>
            <TabsTrigger value="banned" className="flex-1 data-[state=active]:bg-destructive data-[state=active]:text-destructive-foreground">
              <Ban className="w-4 h-4 mr-2" />
              المحظورون ({bannedUsers.length})
            </TabsTrigger>
          </TabsList>

          <TabsContent value="active" className="mt-4">
            <ScrollArea className="h-[600px] pr-2">
              <div className="space-y-2">
                {activeUsers.map(u => <UserRow key={u.id} u={u} onAction={callAction} />)}
                {activeUsers.length === 0 && !loading && (
                  <p className="text-center text-muted-foreground py-8">لا يوجد مستخدمون نشطون</p>
                )}
              </div>
            </ScrollArea>
          </TabsContent>

          <TabsContent value="banned" className="mt-4 space-y-3">
            {bannedUsers.length > 0 && (
              <Button
                onClick={unbanAll}
                variant="outline"
                className="w-full border-emerald-600/40 text-emerald-500 hover:bg-emerald-600/10"
              >
                <Shield className="w-4 h-4 mr-2" />
                رفع الحظر عن جميع المحظورين ({bannedUsers.length})
              </Button>
            )}
            <ScrollArea className="h-[560px] pr-2">
              <div className="space-y-2">
                {bannedUsers.map(u => <UserRow key={u.id} u={u} onAction={callAction} />)}
                {bannedUsers.length === 0 && !loading && (
                  <p className="text-center text-muted-foreground py-8">لا يوجد مستخدمون محظورون</p>
                )}
              </div>
            </ScrollArea>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
};

export default AdminUsersManager;
