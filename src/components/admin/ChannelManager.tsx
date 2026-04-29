import React, { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { ActionType, AndroidStreamConfig } from '@/types/admin';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogClose } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import ImageUploader from './ImageUploader';
import WebConfigForm from './WebConfigForm';
import AndroidConfigForm from './AndroidConfigForm';
import IOSConfigForm, { IOSStreamConfig } from './IOSConfigForm';
import { Plus, Edit2, Trash2, Play, Menu, Tv, ChevronUp, ChevronDown, ExternalLink, Globe, Smartphone, Eye, EyeOff, Monitor } from 'lucide-react';

interface ChannelManagerProps {
  categoryId: string;
  categoryName: string;
}

interface ChannelRow {
  id: string;
  name: string;
  image_url: string | null;
  sort_order: number;
  hidden: boolean;
  action_type: string;
  side_menu_id: string | null;
  external_url: string | null;
  preferred_player: string | null;
  ios_player_type?: string | null;
  web_stream: any;
  android_stream: any;
  ios_stream: any;
  android_action_type: string | null;
  windows_stream?: any;
  windows_action_type?: string | null;
  offline_cache_enabled?: boolean;
}

interface MenuRow {
  id: string;
  name: string;
}

const ChannelManager: React.FC<ChannelManagerProps> = ({ categoryId, categoryName }) => {
  const [channels, setChannels] = useState<ChannelRow[]>([]);
  const [sideMenus, setSideMenus] = useState<MenuRow[]>([]);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingChannel, setEditingChannel] = useState<ChannelRow | null>(null);

  const [formData, setFormData] = useState<any>({
    name: '',
    imageUrl: '',
    sortOrder: 0,
    actionType: 'direct_play',
    stream: { url: '' },
    sideMenuId: '',
    externalUrl: '',
    preferredPlayer: 'default',
    androidStream: { url: '' },
    androidActionType: 'native',
    windowsStream: { url: '' },
    windowsActionType: 'native',
    iosStream: { playerType: 'native', externalApp: 'none' } as IOSStreamConfig,
    offlineCacheEnabled: false,
  });

  const loadChannels = async () => {
    const { data } = await supabase
      .from('channels')
      .select('*')
      .eq('category_id', categoryId)
      .order('sort_order');
    setChannels(data ?? []);
  };

  const loadMenus = async () => {
    const { data } = await supabase.from('side_menus').select('id, name').order('sort_order');
    setSideMenus(data ?? []);
  };

  useEffect(() => {
    if (!categoryId) return;
    loadChannels();
    loadMenus();

    const sub = supabase
      .channel(`ch-${categoryId}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'channels', filter: `category_id=eq.${categoryId}` }, () => loadChannels())
      .subscribe();
    return () => { supabase.removeChannel(sub); };
  }, [categoryId]);

  const resetForm = () => {
    setFormData({
      name: '', imageUrl: '', sortOrder: channels.length,
      actionType: 'direct_play', stream: { url: '' }, sideMenuId: '', externalUrl: '',
      preferredPlayer: 'default', androidStream: { url: '' }, androidActionType: 'native',
      windowsStream: { url: '' }, windowsActionType: 'native',
      iosPlayerType: 'native',
      iosStream: { playerType: 'native', externalApp: 'none' } as IOSStreamConfig,
      offlineCacheEnabled: false,
    });
    setEditingChannel(null);
  };

  const openAddDialog = () => { resetForm(); setFormData((p: any) => ({ ...p, sortOrder: channels.length })); setIsDialogOpen(true); };

  const openEditDialog = (ch: ChannelRow) => {
    setEditingChannel(ch);
    setFormData({
      name: ch.name,
      imageUrl: ch.image_url || '',
      sortOrder: ch.sort_order,
      actionType: ch.action_type,
      stream: ch.web_stream || { url: '' },
      sideMenuId: ch.side_menu_id || '',
      externalUrl: ch.external_url || '',
      preferredPlayer: ch.preferred_player || 'default',
      iosPlayerType: ch.ios_player_type || 'native',
      iosStream: (ch.ios_stream as IOSStreamConfig) || { playerType: (ch.ios_player_type as any) || 'native', externalApp: 'none' },
      androidStream: ch.android_stream || { url: '' },
      androidActionType: ch.android_action_type || 'native',
      windowsStream: ch.windows_stream || { url: '' },
      windowsActionType: ch.windows_action_type || 'native',
      offlineCacheEnabled: !!ch.offline_cache_enabled,
    });
    setIsDialogOpen(true);
  };

  const handleSave = async () => {
    if (!formData.name?.trim()) { alert('الرجاء إدخال اسم القناة.'); return; }

    const row: any = {
      category_id: categoryId,
      name: formData.name.trim(),
      image_url: formData.imageUrl?.trim() || null,
      sort_order: formData.sortOrder ?? 0,
      action_type: formData.actionType || 'direct_play',
      hidden: false,
      offline_cache_enabled: !!formData.offlineCacheEnabled,
    };

    if (formData.actionType === 'direct_play') {
      row.web_stream = formData.stream || null;
      row.preferred_player = formData.preferredPlayer || 'default';
      row.ios_player_type = (formData.iosStream?.playerType) || formData.iosPlayerType || 'native';
      row.ios_stream = formData.iosStream || null;
      row.android_stream = formData.androidStream || null;
      row.android_action_type = formData.androidActionType || 'native';
      row.windows_stream = formData.windowsStream || null;
      row.windows_action_type = formData.windowsActionType || 'native';
      row.side_menu_id = null;
      row.external_url = null;
    } else if (formData.actionType === 'open_submenu') {
      row.side_menu_id = formData.sideMenuId;
      row.web_stream = null; row.android_stream = null; row.ios_stream = null; row.windows_stream = null;
      row.preferred_player = null; row.ios_player_type = null; row.android_action_type = null;
      row.windows_action_type = null;
      row.external_url = null;
    } else if (formData.actionType === 'external_link') {
      row.external_url = formData.externalUrl?.trim();
      row.web_stream = null; row.android_stream = null; row.ios_stream = null; row.windows_stream = null;
      row.preferred_player = null; row.ios_player_type = null; row.android_action_type = null;
      row.windows_action_type = null;
      row.side_menu_id = null;
    }

    try {
      if (editingChannel) {
        await adminDb.update('channels', { id: editingChannel.id }, row);
      } else {
        await adminDb.insert('channels', row);
      }
      setIsDialogOpen(false);
      resetForm();
    } catch (err: any) {
      alert(`فشل حفظ القناة: ${err?.message || 'خطأ غير معروف'}`);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('هل تريد حذف هذه القناة؟')) return;
    try { await adminDb.delete('channels', { id }); } catch { alert('فشل حذف القناة.'); }
  };

  const handleToggleHidden = async (ch: ChannelRow) => {
    try { await adminDb.update('channels', { id: ch.id }, { hidden: !ch.hidden }); } catch (e) { console.error(e); }
  };

  const handleMove = async (id: string, direction: 'up' | 'down') => {
    const sorted = [...channels].sort((a, b) => a.sort_order - b.sort_order);
    const idx = sorted.findIndex(c => c.id === id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    [sorted[idx], sorted[swapIdx]] = [sorted[swapIdx], sorted[idx]];
    // Re-number sequentially so equal sort_orders never appear.
    try {
      for (let i = 0; i < sorted.length; i++) {
        if (sorted[i].sort_order !== i) {
          await adminDb.update('channels', { id: sorted[i].id }, { sort_order: i }, true);
        }
      }
    } catch (e) { console.error('move failed', e); }
  };

  const sideMenuMap = Object.fromEntries(sideMenus.map(m => [m.id, m]));

  return (
    <Card className="border-border bg-card">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
          <Tv className="w-5 h-5 text-primary" />
          القنوات داخل "{categoryName}"
        </CardTitle>
        <Button size="sm" onClick={openAddDialog} className="bg-primary text-primary-foreground">
          <Plus className="w-4 h-4 mr-2" />إضافة قناة
        </Button>
      </CardHeader>
      <CardContent>
        {channels.length === 0 ? (
          <p className="text-muted-foreground text-sm text-center py-8">لا توجد قنوات داخل هذا القسم</p>
        ) : (
          <div className="space-y-2">
            {channels.map((channel, index) => (
              <div key={channel.id} className="flex items-center gap-3 p-3 rounded-lg bg-secondary hover:bg-secondary/80 transition-colors">
                <div className="flex flex-col gap-0.5">
                  <Button variant="ghost" size="icon" className="h-5 w-5 p-0" onClick={() => handleMove(channel.id, 'up')} disabled={index === 0}>
                    <ChevronUp className="w-4 h-4" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-5 w-5 p-0" onClick={() => handleMove(channel.id, 'down')} disabled={index === channels.length - 1}>
                    <ChevronDown className="w-4 h-4" />
                  </Button>
                </div>
                <div className="w-12 h-12 rounded-lg bg-background flex items-center justify-center overflow-hidden">
                  {channel.image_url ? (
                    <img src={channel.image_url} alt={channel.name} className="w-10 h-10 object-contain"
                      onError={(e) => { (e.target as HTMLImageElement).src = 'https://via.placeholder.com/40?text=TV'; }} />
                  ) : (<Tv className="w-6 h-6 text-muted-foreground" />)}
                </div>
                <div className="flex-1">
                  <p className={`font-medium ${channel.hidden ? 'text-muted-foreground line-through' : 'text-foreground'}`}>
                    {channel.name}
                    {channel.hidden && <span className="text-xs text-amber-500 mr-2"> (مخفي)</span>}
                  </p>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      {channel.action_type === 'direct_play' ? (
                      <><Play className="w-3 h-3" /><span>تشغيل مباشر</span>
                        <span className="text-primary flex items-center gap-1"><Globe className="w-3 h-3" /> {channel.preferred_player || 'default'}</span>
                        <span className="text-primary flex items-center gap-1"><Smartphone className="w-3 h-3" /> {channel.android_action_type || 'native'}</span>
                        <span className="text-primary flex items-center gap-1"><Tv className="w-3 h-3" /> {channel.ios_player_type || 'native'}</span></>
                        
                    ) : channel.action_type === 'external_link' ? (
                      <><ExternalLink className="w-3 h-3" /><span className="truncate max-w-[200px]">رابط: {channel.external_url}</span></>
                    ) : (
                      <><Menu className="w-3 h-3" /><span>قائمة فرعية: {sideMenuMap[channel.side_menu_id || '']?.name || 'غير محدد'}</span></>
                    )}
                  </div>
                </div>
                <Button variant="ghost" size="icon" onClick={() => handleToggleHidden(channel)}>
                  {channel.hidden ? <EyeOff className="w-4 h-4 text-amber-500" /> : <Eye className="w-4 h-4" />}
                </Button>
                <Button variant="ghost" size="icon" onClick={() => openEditDialog(channel)}><Edit2 className="w-4 h-4" /></Button>
                <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive" onClick={() => handleDelete(channel.id)}><Trash2 className="w-4 h-4" /></Button>
              </div>
            ))}
          </div>
        )}
      </CardContent>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="bg-card border-border max-w-3xl max-h-[90vh]">
          <DialogHeader><DialogTitle>{editingChannel ? 'تعديل القناة' : 'إضافة قناة جديدة'}</DialogTitle></DialogHeader>
          <ScrollArea className="max-h-[70vh] pr-4">
            <div className="space-y-6 py-4">
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>اسم القناة <span className="text-destructive">*</span></Label>
                  <Input value={formData.name || ''} onChange={(e) => setFormData((p: any) => ({ ...p, name: e.target.value }))}
                    placeholder="مثال: beIN Sports 1" className="bg-secondary border-border" />
                </div>
                <ImageUploader value={formData.imageUrl || ''} onChange={(base64) => setFormData((p: any) => ({ ...p, imageUrl: base64 }))} label="صورة القناة" />

                <div className="flex items-center justify-between gap-3 p-3 rounded-lg bg-secondary border border-border">
                  <div>
                    <Label className="text-foreground font-medium">تفعيل التخزين المحلي المشفر (Offline Cache)</Label>
                    <p className="text-xs text-muted-foreground mt-1">يحفظ القناة محلياً بتشفير عسكري ولا يطلب تحديثاً.</p>
                  </div>
                  <input
                    type="checkbox"
                    className="w-5 h-5 accent-primary"
                    checked={!!formData.offlineCacheEnabled}
                    onChange={(e) => setFormData((p: any) => ({ ...p, offlineCacheEnabled: e.target.checked }))}
                  />
                </div>
              </div>

              <div className="space-y-3">
                <Label>نوع الإجراء</Label>
                <RadioGroup value={formData.actionType} onValueChange={(value: ActionType) => setFormData((p: any) => ({ ...p, actionType: value }))} className="flex flex-wrap gap-4">
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="direct_play" id="direct_play" />
                    <Label htmlFor="direct_play" className="flex items-center gap-2 cursor-pointer"><Play className="w-4 h-4 text-primary" />تشغيل مباشر</Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="open_submenu" id="open_submenu" />
                    <Label htmlFor="open_submenu" className="flex items-center gap-2 cursor-pointer"><Menu className="w-4 h-4 text-primary" />فتح قائمة فرعية</Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="external_link" id="external_link" />
                    <Label htmlFor="external_link" className="flex items-center gap-2 cursor-pointer"><ExternalLink className="w-4 h-4 text-primary" />رابط خارجي</Label>
                  </div>
                </RadioGroup>
              </div>

              {formData.actionType === 'direct_play' && (
                <Tabs defaultValue="web" className="w-full">
                  <TabsList className="grid w-full grid-cols-4">
                    <TabsTrigger value="web" className="flex items-center gap-2"><Globe className="w-4 h-4" />الويب</TabsTrigger>
                    <TabsTrigger value="android" className="flex items-center gap-2"><Smartphone className="w-4 h-4" />أندرويد</TabsTrigger>
                    <TabsTrigger value="ios" className="flex items-center gap-2"><Tv className="w-4 h-4" />الآيفون</TabsTrigger>
                    <TabsTrigger value="windows" className="flex items-center gap-2"><Monitor className="w-4 h-4" />ويندوز</TabsTrigger>
                  </TabsList>
                  <TabsContent value="web" className="mt-4">
                    <WebConfigForm
                      streamConfig={formData.stream || { url: '' }}
                      playerType={formData.preferredPlayer || 'default'}
                      onStreamChange={(stream) => setFormData((p: any) => ({ ...p, stream }))}
                      onPlayerTypeChange={(playerType) => setFormData((p: any) => ({ ...p, preferredPlayer: playerType }))}
                    />
                  </TabsContent>
                  <TabsContent value="android" className="mt-4">
                    <AndroidConfigForm
                      config={formData.androidStream || { url: '' }}
                      actionType={formData.androidActionType || 'native'}
                      onChange={(config) => setFormData((p: any) => ({ ...p, androidStream: config as AndroidStreamConfig }))}
                      onActionTypeChange={(actionType) => setFormData((p: any) => ({ ...p, androidActionType: actionType }))}
                    />
                  </TabsContent>
                  <TabsContent value="ios" className="mt-4">
                    <IOSConfigForm
                      config={formData.iosStream || { playerType: 'native', externalApp: 'none' }}
                      onChange={(cfg) => setFormData((p: any) => ({ ...p, iosStream: cfg, iosPlayerType: cfg.playerType || 'native' }))}
                    />
                  </TabsContent>
                  <TabsContent value="windows" className="mt-4">
                    <AndroidConfigForm
                      config={formData.windowsStream || { url: '' }}
                      actionType={formData.windowsActionType || 'native'}
                      onChange={(config) => setFormData((p: any) => ({ ...p, windowsStream: config as AndroidStreamConfig }))}
                      onActionTypeChange={(actionType) => setFormData((p: any) => ({ ...p, windowsActionType: actionType }))}
                    />
                  </TabsContent>
                </Tabs>
              )}

              {formData.actionType === 'open_submenu' && (
                <div className="space-y-2">
                  <Label>اختر قائمة جانبية <span className="text-destructive">*</span></Label>
                  <Select value={formData.sideMenuId || undefined} onValueChange={(value) => setFormData((p: any) => ({ ...p, sideMenuId: value }))}>
                    <SelectTrigger className="bg-secondary border-border"><SelectValue placeholder="اختر قائمة جانبية" /></SelectTrigger>
                    <SelectContent className="bg-popover border-border z-50">
                      {sideMenus.map((m) => (<SelectItem key={m.id} value={m.id}>{m.name}</SelectItem>))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {formData.actionType === 'external_link' && (
                <div className="space-y-2">
                  <Label>رابط الموقع <span className="text-destructive">*</span></Label>
                  <Input value={formData.externalUrl || ''} onChange={(e) => setFormData((p: any) => ({ ...p, externalUrl: e.target.value }))}
                    placeholder="https://example.com" className="bg-secondary border-border font-mono text-sm" />
                </div>
              )}
            </div>
          </ScrollArea>
          <DialogFooter>
            <DialogClose asChild><Button variant="outline">إلغاء</Button></DialogClose>
            <Button onClick={handleSave} className="bg-primary text-primary-foreground">{editingChannel ? 'حفظ التغييرات' : 'إضافة القناة'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
};

export default ChannelManager;
