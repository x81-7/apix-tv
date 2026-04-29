import React, { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { StreamConfig, AndroidStreamConfig, AndroidActionType } from '@/types/admin';
import type { WebPlayerType, iOSPlayerApp } from '@/types/admin';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogClose } from '@/components/ui/dialog';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import ImageUploader from './ImageUploader';
import WebConfigForm from './WebConfigForm';
import AndroidConfigForm from './AndroidConfigForm';
import IOSConfigForm, { IOSStreamConfig } from './IOSConfigForm';
import { Plus, Edit2, Trash2, Menu, Tv, ChevronUp, ChevronDown, Globe, Smartphone, Eye, EyeOff, Lock } from 'lucide-react';

interface MenuRow { id: string; name: string; sort_order: number; pin_code?: string | null; }
interface SubRow {
  id: string; name: string; image_url: string | null; sort_order: number; hidden: boolean;
  side_menu_id: string; preferred_player: string | null;
  web_stream: any; android_stream: any; ios_stream: any; android_action_type: string | null;
  offline_cache_enabled?: boolean;
}

const SideMenuManager: React.FC = () => {
  const [menus, setMenus] = useState<MenuRow[]>([]);
  const [subChannels, setSubChannels] = useState<SubRow[]>([]);
  const [isMenuDialogOpen, setIsMenuDialogOpen] = useState(false);
  const [isChannelDialogOpen, setIsChannelDialogOpen] = useState(false);
  const [editingMenu, setEditingMenu] = useState<MenuRow | null>(null);
  const [editingChannel, setEditingChannel] = useState<{ menuId: string; channel: SubRow | null }>({ menuId: '', channel: null });
  const [menuName, setMenuName] = useState('');
  const [menuPin, setMenuPin] = useState('');
  const [channelForm, setChannelForm] = useState<any>({
    name: '', imageUrl: '', sortOrder: 0,
    stream: { url: '' }, preferredPlayer: 'default',
    androidStream: {}, androidActionType: 'native',
    iosStream: { playerType: 'native', externalApp: 'none' } as IOSStreamConfig,
    offlineCacheEnabled: false,
  });

  const loadMenus = async () => {
    const { data } = await supabase.from('side_menus').select('*').order('sort_order');
    setMenus(data ?? []);
  };
  const loadSubs = async () => {
    const { data } = await supabase.from('sub_channels').select('*').order('sort_order');
    setSubChannels(data ?? []);
  };

  useEffect(() => {
    loadMenus(); loadSubs();
    const s1 = supabase.channel('side-menus-rt').on('postgres_changes', { event: '*', schema: 'public', table: 'side_menus' }, () => loadMenus()).subscribe();
    const s2 = supabase.channel('sub-channels-rt').on('postgres_changes', { event: '*', schema: 'public', table: 'sub_channels' }, () => loadSubs()).subscribe();
    return () => { supabase.removeChannel(s1); supabase.removeChannel(s2); };
  }, []);

  const handleSaveMenu = async () => {
    if (!menuName.trim()) return;
    const pin = (menuPin || '').trim();
    if (pin && !/^\d{3,8}$/.test(pin)) { alert('رمز القفل يجب أن يكون أرقاماً فقط (3 إلى 8 خانات).'); return; }
    try {
      if (editingMenu) {
        await adminDb.update('side_menus', { id: editingMenu.id }, { name: menuName.trim(), pin_code: pin || null });
      } else {
        await adminDb.insert('side_menus', { name: menuName.trim(), sort_order: menus.length, pin_code: pin || null });
      }
      setIsMenuDialogOpen(false); setMenuName(''); setMenuPin(''); setEditingMenu(null);
    } catch (err: any) { alert('فشل حفظ القائمة: ' + err?.message); }
  };

  const handleDeleteMenu = async (id: string) => {
    if (!confirm('هل تريد حذف هذه القائمة الجانبية وجميع القنوات بداخلها؟')) return;
    try { await adminDb.delete('sub_channels', { side_menu_id: id }); await adminDb.delete('side_menus', { id }); } catch { alert('فشل حذف القائمة.'); }
  };

  const handleMoveMenu = async (id: string, direction: 'up' | 'down') => {
    const sorted = [...menus].sort((a, b) => a.sort_order - b.sort_order);
    const idx = sorted.findIndex(m => m.id === id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    [sorted[idx], sorted[swapIdx]] = [sorted[swapIdx], sorted[idx]];
    try {
      for (let i = 0; i < sorted.length; i++) {
        if (sorted[i].sort_order !== i) {
          await adminDb.update('side_menus', { id: sorted[i].id }, { sort_order: i }, true);
        }
      }
    } catch (e) { console.error('move menu failed', e); }
  };

  const openAddChannelDialog = (menuId: string) => {
    const count = subChannels.filter(s => s.side_menu_id === menuId).length;
    setEditingChannel({ menuId, channel: null });
    setChannelForm({
      name: '', imageUrl: '', sortOrder: count, stream: { url: '' }, preferredPlayer: 'default',
      androidStream: {}, androidActionType: 'native',
      windowsStream: {}, windowsActionType: 'native',
      iosStream: { playerType: 'native', externalApp: 'none' } as IOSStreamConfig,
      offlineCacheEnabled: false,
    });
    setIsChannelDialogOpen(true);
  };

  const openEditChannelDialog = (menuId: string, ch: SubRow) => {
    setEditingChannel({ menuId, channel: ch });
    setChannelForm({
      name: ch.name, imageUrl: ch.image_url || '', sortOrder: ch.sort_order,
      stream: ch.web_stream || { url: '' }, preferredPlayer: ch.preferred_player || 'default',
      androidStream: ch.android_stream || {}, androidActionType: ch.android_action_type || 'native',
      windowsStream: (ch as any).windows_stream || {}, windowsActionType: (ch as any).windows_action_type || 'native',
      iosStream: (ch.ios_stream as IOSStreamConfig) || { playerType: 'native', externalApp: 'none' },
      offlineCacheEnabled: !!ch.offline_cache_enabled,
    });
    setIsChannelDialogOpen(true);
  };

  const handleSaveChannel = async () => {
    if (!channelForm.name?.trim() || !editingChannel.menuId) return;
    const row: any = {
      side_menu_id: editingChannel.menuId,
      name: channelForm.name.trim(), image_url: channelForm.imageUrl?.trim() || null,
      sort_order: channelForm.sortOrder || 0,
      web_stream: channelForm.stream, preferred_player: channelForm.preferredPlayer || 'default',
      android_stream: channelForm.androidStream, android_action_type: channelForm.androidActionType || 'native',
      windows_stream: channelForm.windowsStream, windows_action_type: channelForm.windowsActionType || 'native',
      ios_stream: channelForm.iosStream || null,
      offline_cache_enabled: !!channelForm.offlineCacheEnabled,
    };
    try {
      if (editingChannel.channel) { await adminDb.update('sub_channels', { id: editingChannel.channel.id }, row); }
      else { await adminDb.insert('sub_channels', row); }
      setIsChannelDialogOpen(false);
    } catch (err: any) { alert('فشل حفظ القناة: ' + err?.message); }
  };

  const handleDeleteChannel = async (id: string) => {
    if (!confirm('هل تريد حذف هذه القناة؟')) return;
    try { await adminDb.delete('sub_channels', { id }); } catch { alert('فشل حذف القناة.'); }
  };

  const handleToggleHidden = async (ch: SubRow) => {
    try { await adminDb.update('sub_channels', { id: ch.id }, { hidden: !ch.hidden }); } catch { }
  };

  const handleMoveChannel = async (menuId: string, channelId: string, direction: 'up' | 'down') => {
    const menuSubs = subChannels.filter(s => s.side_menu_id === menuId).sort((a, b) => a.sort_order - b.sort_order);
    const idx = menuSubs.findIndex(c => c.id === channelId);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= menuSubs.length) return;
    [menuSubs[idx], menuSubs[swapIdx]] = [menuSubs[swapIdx], menuSubs[idx]];
    try {
      for (let i = 0; i < menuSubs.length; i++) {
        if (menuSubs[i].sort_order !== i) {
          await adminDb.update('sub_channels', { id: menuSubs[i].id }, { sort_order: i }, true);
        }
      }
    } catch (e) { console.error('move sub failed', e); }
  };

  return (
    <Card className="border-border bg-card">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
          <Menu className="w-5 h-5 text-primary" />القوائم الجانبية (مجموعات فرعية)
        </CardTitle>
        <Button size="sm" onClick={() => { setEditingMenu(null); setMenuName(''); setMenuPin(''); setIsMenuDialogOpen(true); }} className="bg-primary text-primary-foreground">
          <Plus className="w-4 h-4 mr-2" />إضافة قائمة
        </Button>
      </CardHeader>
      <CardContent>
        {menus.length === 0 ? (
          <p className="text-muted-foreground text-sm text-center py-8">لا توجد قوائم جانبية بعد</p>
        ) : (
          <Accordion type="multiple" className="space-y-2">
            {menus.map((menu, menuIndex) => {
              const menuSubs = subChannels.filter(s => s.side_menu_id === menu.id).sort((a, b) => a.sort_order - b.sort_order);
              return (
                <AccordionItem key={menu.id} value={menu.id} className="border border-border rounded-lg overflow-hidden">
                  <AccordionTrigger className="px-4 py-3 bg-secondary hover:bg-secondary/80 [&[data-state=open]]:bg-primary/10">
                    <div className="flex items-center gap-3 flex-1">
                      <Menu className="w-5 h-5 text-primary" />
                      <span className="font-medium text-foreground">{menu.name}</span>
                      <span className="text-xs text-muted-foreground">{menuSubs.length} قناة</span>
                      {menu.pin_code && (
                        <span className="text-xs bg-amber-500/20 text-amber-500 px-1.5 py-0.5 rounded flex items-center gap-1">
                          <Lock className="w-3 h-3" /> مقفلة
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-1 mr-2">
                      <Button variant="ghost" size="icon" className="h-7 w-7 p-0" onClick={(e) => { e.stopPropagation(); handleMoveMenu(menu.id, 'up'); }} disabled={menuIndex === 0}><ChevronUp className="w-4 h-4" /></Button>
                      <Button variant="ghost" size="icon" className="h-7 w-7 p-0" onClick={(e) => { e.stopPropagation(); handleMoveMenu(menu.id, 'down'); }} disabled={menuIndex === menus.length - 1}><ChevronDown className="w-4 h-4" /></Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8" onClick={(e) => { e.stopPropagation(); setEditingMenu(menu); setMenuName(menu.name); setMenuPin(menu.pin_code || ''); setIsMenuDialogOpen(true); }}><Edit2 className="w-4 h-4" /></Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={(e) => { e.stopPropagation(); handleDeleteMenu(menu.id); }}><Trash2 className="w-4 h-4" /></Button>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="px-4 py-3 bg-card">
                    <div className="space-y-2">
                      <Button size="sm" variant="outline" onClick={() => openAddChannelDialog(menu.id)} className="w-full mb-3"><Plus className="w-4 h-4 mr-2" />إضافة قناة للقائمة</Button>
                      {menuSubs.length === 0 ? (
                        <p className="text-muted-foreground text-sm text-center py-4">لا توجد قنوات داخل هذه القائمة</p>
                      ) : (
                        menuSubs.map((channel, index) => (
                          <div key={channel.id} className="flex items-center gap-3 p-3 rounded-lg bg-secondary">
                            <div className="flex flex-col gap-0.5">
                              <Button variant="ghost" size="icon" className="h-5 w-5 p-0" onClick={() => handleMoveChannel(menu.id, channel.id, 'up')} disabled={index === 0}><ChevronUp className="w-4 h-4" /></Button>
                              <Button variant="ghost" size="icon" className="h-5 w-5 p-0" onClick={() => handleMoveChannel(menu.id, channel.id, 'down')} disabled={index === menuSubs.length - 1}><ChevronDown className="w-4 h-4" /></Button>
                            </div>
                            <div className="w-10 h-10 rounded-lg bg-background flex items-center justify-center overflow-hidden">
                              {channel.image_url ? (<img src={channel.image_url} alt={channel.name} className="w-8 h-8 object-contain" />) : (<Tv className="w-5 h-5 text-muted-foreground" />)}
                            </div>
                            <div className="flex-1">
                              <span className={`font-medium block ${channel.hidden ? 'text-muted-foreground line-through' : 'text-foreground'}`}>
                                {channel.name}{channel.hidden && <span className="text-xs text-amber-500 mr-2"> (مخفي)</span>}
                              </span>
                              <div className="flex items-center gap-2 mt-1">
                                {(channel.web_stream as any)?.url && <span className="text-xs bg-primary/20 text-primary px-1.5 py-0.5 rounded"><Globe className="w-3 h-3 inline mr-1" />Web</span>}
                                {(channel.android_stream as any)?.url && <span className="text-xs bg-primary/20 text-primary px-1.5 py-0.5 rounded"><Smartphone className="w-3 h-3 inline mr-1" />Android</span>}
                              </div>
                            </div>
                            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => handleToggleHidden(channel)}>{channel.hidden ? <EyeOff className="w-4 h-4 text-amber-500" /> : <Eye className="w-4 h-4" />}</Button>
                            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => openEditChannelDialog(menu.id, channel)}><Edit2 className="w-4 h-4" /></Button>
                            <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => handleDeleteChannel(channel.id)}><Trash2 className="w-4 h-4" /></Button>
                          </div>
                        ))
                      )}
                    </div>
                  </AccordionContent>
                </AccordionItem>
              );
            })}
          </Accordion>
        )}
      </CardContent>

      {/* Menu Dialog */}
      <Dialog open={isMenuDialogOpen} onOpenChange={setIsMenuDialogOpen}>
        <DialogContent className="bg-card border-border">
          <DialogHeader><DialogTitle>{editingMenu ? 'تعديل القائمة الجانبية' : 'إضافة قائمة جانبية'}</DialogTitle></DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>اسم القائمة</Label>
              <Input value={menuName} onChange={(e) => setMenuName(e.target.value)} placeholder="مثال: باقة رياضة" className="bg-secondary border-border" />
            </div>
            <div className="space-y-2">
              <Label className="flex items-center gap-2"><Lock className="w-4 h-4" /> رمز قفل القائمة (اختياري)</Label>
              <Input
                value={menuPin}
                onChange={(e) => setMenuPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
                placeholder="مثلاً 1234 — اتركه فارغاً لإلغاء القفل"
                inputMode="numeric"
                className="bg-secondary border-border font-mono tracking-widest"
                dir="ltr"
              />
              <p className="text-xs text-muted-foreground">عند تفعيله، يطلب التطبيق هذا الرمز قبل فتح أي قناة داخل القائمة (3 إلى 8 أرقام).</p>
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild><Button variant="outline">إلغاء</Button></DialogClose>
            <Button onClick={handleSaveMenu} className="bg-primary text-primary-foreground">{editingMenu ? 'حفظ التغييرات' : 'إضافة القائمة'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Sub-Channel Dialog */}
      <Dialog open={isChannelDialogOpen} onOpenChange={setIsChannelDialogOpen}>
        <DialogContent className="bg-card border-border max-w-3xl max-h-[90vh]">
          <DialogHeader><DialogTitle>{editingChannel.channel ? 'تعديل قناة فرعية' : 'إضافة قناة فرعية'}</DialogTitle></DialogHeader>
          <ScrollArea className="max-h-[70vh] pr-4">
            <div className="space-y-6 py-4">
              <div className="space-y-4 p-4 rounded-lg border border-border bg-muted/30">
                <h3 className="font-semibold text-foreground">المعلومات الأساسية</h3>
                <div className="space-y-2">
                  <Label>اسم القناة <span className="text-destructive">*</span></Label>
                  <Input value={channelForm.name || ''} onChange={(e) => setChannelForm((p: any) => ({ ...p, name: e.target.value }))} placeholder="مثال: beIN Sports HD" className="bg-secondary border-border" />
                </div>
                <ImageUploader value={channelForm.imageUrl || ''} onChange={(base64) => setChannelForm((p: any) => ({ ...p, imageUrl: base64 }))} label="صورة القناة" />

                <div className="flex items-center justify-between gap-3 p-3 rounded-lg bg-secondary border border-border">
                  <div>
                    <Label className="text-foreground font-medium">تفعيل التخزين المحلي المشفر (Offline Cache)</Label>
                    <p className="text-xs text-muted-foreground mt-1">يحفظ القناة (الاسم، الصورة، روابط البث، DRM، الترويسات) محلياً بتشفير عسكري ولا يطلب أي تحديث.</p>
                  </div>
                  <input
                    type="checkbox"
                    className="w-5 h-5 accent-primary"
                    checked={!!channelForm.offlineCacheEnabled}
                    onChange={(e) => setChannelForm((p: any) => ({ ...p, offlineCacheEnabled: e.target.checked }))}
                  />
                </div>
              </div>
              <Tabs defaultValue="web" className="w-full">
                <TabsList className="grid w-full grid-cols-4 bg-muted">
                  <TabsTrigger value="web" className="flex items-center gap-2"><Globe className="w-4 h-4" />🌐 الويب</TabsTrigger>
                  <TabsTrigger value="android" className="flex items-center gap-2"><Smartphone className="w-4 h-4" />📱 أندرويد</TabsTrigger>
                  <TabsTrigger value="ios" className="flex items-center gap-2"><Tv className="w-4 h-4" />🍏 آيفون</TabsTrigger>
                  <TabsTrigger value="windows" className="flex items-center gap-2"><Tv className="w-4 h-4" />🪟 ويندوز</TabsTrigger>
                </TabsList>
                <TabsContent value="web" className="mt-4">
                  <WebConfigForm streamConfig={channelForm.stream} playerType={channelForm.preferredPlayer}
                    onStreamChange={(stream) => setChannelForm((p: any) => ({ ...p, stream }))}
                    onPlayerTypeChange={(pt) => setChannelForm((p: any) => ({ ...p, preferredPlayer: pt }))} />
                </TabsContent>
                <TabsContent value="android" className="mt-4">
                  <AndroidConfigForm config={channelForm.androidStream} actionType={channelForm.androidActionType}
                    onChange={(config) => setChannelForm((p: any) => ({ ...p, androidStream: config }))}
                    onActionTypeChange={(at) => setChannelForm((p: any) => ({ ...p, androidActionType: at }))} />
                </TabsContent>
                <TabsContent value="ios" className="mt-4">
                  <IOSConfigForm
                    config={channelForm.iosStream || { playerType: 'native', externalApp: 'none' }}
                    onChange={(cfg) => setChannelForm((p: any) => ({ ...p, iosStream: cfg }))}
                  />
                </TabsContent>
                <TabsContent value="windows" className="mt-4">
                  <AndroidConfigForm config={channelForm.windowsStream || {}} actionType={channelForm.windowsActionType || 'native'}
                    onChange={(config) => setChannelForm((p: any) => ({ ...p, windowsStream: config }))}
                    onActionTypeChange={(at) => setChannelForm((p: any) => ({ ...p, windowsActionType: at }))} />
                </TabsContent>
              </Tabs>
            </div>
          </ScrollArea>
          <DialogFooter>
            <DialogClose asChild><Button variant="outline">إلغاء</Button></DialogClose>
            <Button onClick={handleSaveChannel} className="bg-primary text-primary-foreground">{editingChannel.channel ? 'حفظ التغييرات' : 'إضافة القناة'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
};

export default SideMenuManager;
