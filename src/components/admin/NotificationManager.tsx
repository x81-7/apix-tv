import React, { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Bell, Send, AlertCircle, CheckCircle } from 'lucide-react';

type NotificationActionType = 'main_channel' | 'side_menu' | 'sub_channel' | 'external_link';

interface ChannelOption { id: string; name: string; categoryName: string; }
interface MenuOption { id: string; name: string; }
interface SubOption { id: string; name: string; side_menu_id: string; }

const NotificationManager: React.FC = () => {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [actionType, setActionType] = useState<NotificationActionType>('main_channel');
  const [selectedMainChannel, setSelectedMainChannel] = useState('');
  const [selectedSideMenu, setSelectedSideMenu] = useState('');
  const [selectedSubChannel, setSelectedSubChannel] = useState('');
  const [externalUrl, setExternalUrl] = useState('');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);
  const [loading, setLoading] = useState(true);

  const [mainChannels, setMainChannels] = useState<ChannelOption[]>([]);
  const [sideMenus, setSideMenus] = useState<MenuOption[]>([]);
  const [subChannels, setSubChannels] = useState<SubOption[]>([]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      const [cats, chans, menus, subs] = await Promise.all([
        supabase.from('categories').select('id, name').order('sort_order'),
        supabase.from('channels').select('id, name, category_id').order('sort_order'),
        supabase.from('side_menus').select('id, name').order('sort_order'),
        supabase.from('sub_channels').select('id, name, side_menu_id').order('sort_order'),
      ]);
      const catMap = Object.fromEntries((cats.data ?? []).map(c => [c.id, c.name]));
      setMainChannels((chans.data ?? []).map(ch => ({ id: ch.id, name: ch.name, categoryName: catMap[ch.category_id] || '' })));
      setSideMenus(menus.data ?? []);
      setSubChannels(subs.data ?? []);
      setLoading(false);
    })();
  }, []);

  const buildPayload = () => {
    const payload: any = { actionType };
    switch (actionType) {
      case 'main_channel': payload.targetId = selectedMainChannel; break;
      case 'side_menu': payload.targetId = selectedSideMenu; break;
      case 'sub_channel': payload.targetId = selectedSubChannel; payload.parentMenuId = selectedSideMenu; break;
      case 'external_link': payload.externalUrl = externalUrl; break;
    }
    return payload;
  };

  const resolveRealtimeChannel = () => {
    switch (actionType) {
      case 'main_channel':
        return `channel:${selectedMainChannel}`;
      case 'side_menu':
        return `side_menu:${selectedSideMenu}`;
      case 'sub_channel':
        return `sub_channel:${selectedSubChannel}`;
      default:
        return 'broadcast';
    }
  };

  const validateForm = (): boolean => {
    if (!title.trim() || !body.trim()) return false;
    switch (actionType) {
      case 'main_channel': return !!selectedMainChannel;
      case 'side_menu': return !!selectedSideMenu;
      case 'sub_channel': return !!selectedSideMenu && !!selectedSubChannel;
      case 'external_link': return !!externalUrl.trim();
      default: return false;
    }
  };

  const sendNotification = async () => {
    if (!validateForm()) { setResult({ success: false, message: 'الرجاء ملء جميع الحقول المطلوبة' }); return; }
    setSending(true); setResult(null);
    try {
      await adminDb.pushNotification(title.trim(), body.trim(), buildPayload(), resolveRealtimeChannel());
      setResult({ success: true, message: 'تم إرسال الإشعار بنجاح وسيصل مباشرة للمستخدمين.' });
      setTitle(''); setBody(''); setExternalUrl('');
      setSelectedMainChannel(''); setSelectedSideMenu(''); setSelectedSubChannel('');
    } catch (error: any) {
      setResult({ success: false, message: `فشل إرسال الإشعار: ${error?.message || 'خطأ'}` });
    } finally { setSending(false); }
  };

  const filteredSubs = subChannels.filter(s => s.side_menu_id === selectedSideMenu);

  if (loading) return <div className="flex items-center justify-center p-8"><div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" /></div>;

  return (
    <div className="space-y-6">
      <Card className="border-border bg-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Bell className="w-5 h-5 text-primary" />إرسال إشعار جديد</CardTitle>
          <CardDescription>يتم إرسال الإشعار مباشرة للتطبيق مع تمرير القناة أو القسم أو الرابط المحدد عند الضغط عليه.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>عنوان الإشعار <span className="text-destructive">*</span></Label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="مثال: مباراة جديدة الآن!" className="bg-secondary border-border" />
          </div>
          <div className="space-y-2">
            <Label>محتوى الإشعار <span className="text-destructive">*</span></Label>
            <Textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="مثال: شاهد مباراة ريال مدريد الآن" className="bg-secondary border-border min-h-[80px]" />
          </div>
          <div className="space-y-2">
            <Label>نوع الإجراء عند النقر <span className="text-destructive">*</span></Label>
            <Select value={actionType} onValueChange={(v: NotificationActionType) => { setActionType(v); setSelectedMainChannel(''); setSelectedSideMenu(''); setSelectedSubChannel(''); setExternalUrl(''); }}>
              <SelectTrigger className="bg-secondary border-border"><SelectValue /></SelectTrigger>
              <SelectContent className="bg-card border-border">
                <SelectItem value="main_channel">فتح قناة رئيسية</SelectItem>
                <SelectItem value="side_menu">فتح قائمة جانبية</SelectItem>
                <SelectItem value="sub_channel">فتح قناة فرعية</SelectItem>
                <SelectItem value="external_link">رابط خارجي</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {actionType === 'main_channel' && (
            <div className="space-y-2">
              <Label>اختر القناة <span className="text-destructive">*</span></Label>
              <Select value={selectedMainChannel} onValueChange={setSelectedMainChannel}>
                <SelectTrigger className="bg-secondary border-border"><SelectValue placeholder="اختر قناة" /></SelectTrigger>
                <SelectContent className="bg-card border-border max-h-[200px]">
                  {mainChannels.map(ch => (<SelectItem key={ch.id} value={ch.id}>{ch.name} ({ch.categoryName})</SelectItem>))}
                </SelectContent>
              </Select>
            </div>
          )}
          {(actionType === 'side_menu' || actionType === 'sub_channel') && (
            <div className="space-y-2">
              <Label>اختر القائمة الجانبية <span className="text-destructive">*</span></Label>
              <Select value={selectedSideMenu} onValueChange={(v) => { setSelectedSideMenu(v); setSelectedSubChannel(''); }}>
                <SelectTrigger className="bg-secondary border-border"><SelectValue placeholder="اختر قائمة" /></SelectTrigger>
                <SelectContent className="bg-card border-border">{sideMenus.map(m => (<SelectItem key={m.id} value={m.id}>{m.name}</SelectItem>))}</SelectContent>
              </Select>
            </div>
          )}
          {actionType === 'sub_channel' && selectedSideMenu && (
            <div className="space-y-2">
              <Label>اختر القناة الفرعية <span className="text-destructive">*</span></Label>
              <Select value={selectedSubChannel} onValueChange={setSelectedSubChannel}>
                <SelectTrigger className="bg-secondary border-border"><SelectValue placeholder="اختر قناة فرعية" /></SelectTrigger>
                <SelectContent className="bg-card border-border">{filteredSubs.map(s => (<SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>))}</SelectContent>
              </Select>
            </div>
          )}
          {actionType === 'external_link' && (
            <div className="space-y-2">
              <Label>الرابط الخارجي <span className="text-destructive">*</span></Label>
              <Input value={externalUrl} onChange={(e) => setExternalUrl(e.target.value)} placeholder="https://example.com" className="bg-secondary border-border font-mono" dir="ltr" />
            </div>
          )}

          {result && (
            <div className={`flex items-center gap-2 p-3 rounded-lg ${result.success ? 'bg-green-500/10 text-green-500' : 'bg-destructive/10 text-destructive'}`}>
              {result.success ? <CheckCircle className="w-5 h-5" /> : <AlertCircle className="w-5 h-5" />}
              <span className="text-sm">{result.message}</span>
            </div>
          )}

          <Button onClick={sendNotification} disabled={sending || !validateForm()} className="w-full bg-primary text-primary-foreground">
            {sending ? (<><div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin ml-2" />جاري الإرسال...</>) : (<><Send className="w-4 h-4 ml-2" />إرسال الإشعار</>)}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
};

export default NotificationManager;
