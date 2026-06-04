import React, { useState, useEffect } from 'react';
import { useAdminAuth } from '@/hooks/useAdminAuth';
import AdminLogin from './AdminLogin';
import CategoryManager from '@/components/admin/CategoryManager';
import ChannelManager from '@/components/admin/ChannelManager';
import SideMenuManager from '@/components/admin/SideMenuManager';
import NotificationManager from '@/components/admin/NotificationManager';
import AdConfigManager from '@/components/admin/AdConfigManager';
import SecurityConfigManager from '@/components/admin/SecurityConfigManager';
import AppUpdateManager from '@/components/admin/AppUpdateManager';
import BackupButton from '@/components/admin/BackupButton';
import GateConfigManager from '@/components/admin/GateConfigManager';
import AdminUsersManager from '@/components/admin/AdminUsersManager';
import BanConfigManager from '@/components/admin/BanConfigManager';
import SystemSettingsManager from '@/components/admin/SystemSettingsManager';
import AppInfoManager from '@/components/admin/AppInfoManager';
import AppAssetsManager from '@/components/admin/AppAssetsManager';
import VipManager from '@/components/admin/VipManager';
import CloudflareManager from '@/components/admin/CloudflareManager';
import CinemaManager from '@/components/admin/CinemaManager';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { LogOut, Settings, Tv, Menu, Folder, Shield, Bell, Megaphone, Lock, DoorOpen, Users, Smartphone, Crown, Cloud, Film } from 'lucide-react';

const updateAdminManifest = () => {
  const existingManifest = document.querySelector('link[rel="manifest"]');
  if (existingManifest) existingManifest.setAttribute('href', '/admin-manifest.json');
};

const AdminDashboard: React.FC = () => {
  const { user, loading, isAuthorized, logout } = useAdminAuth();
  const [selectedCategory, setSelectedCategory] = useState<{ id: string; name: string } | null>(null);
  const [showSettingsSection, setShowSettingsSection] = useState(true);
  const [settingsConfigLoaded, setSettingsConfigLoaded] = useState(false);

  useEffect(() => {
    updateAdminManifest();
    document.title = 'TV Control - لوحة التحكم';
  }, []);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from('system_settings')
        .select('value')
        .eq('key', 'appSettings')
        .maybeSingle();
      const val = (data?.value as any) ?? {};
      setShowSettingsSection(val.showSettingsSection !== false);
      setSettingsConfigLoaded(true);
    })();
  }, []);

  const handleSettingsSectionToggle = async (checked: boolean) => {
    setShowSettingsSection(checked);
    try {
      await adminDb.upsert('system_settings', {
        key: 'appSettings',
        value: { showSettingsSection: checked },
        description: 'App Settings',
      }, true);
    } catch (err) {
      console.error('Failed to save appSettings:', err);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground">جارٍ التحميل...</p>
        </div>
      </div>
    );
  }

  if (!user || !isAuthorized) return <AdminLogin />;

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-50 bg-card border-b border-border">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Shield className="w-5 h-5 text-primary" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-foreground">لوحة تحكم المشرف</h1>
              </div>
            </div>
            <Button variant="outline" onClick={logout}
              className="border-border hover:bg-destructive hover:text-destructive-foreground hover:border-destructive">
              <LogOut className="w-4 h-4 mr-2" />
              تسجيل الخروج
            </Button>
          </div>
        </div>
      </header>

      <main className="container mx-auto px-4 py-6">
        <Tabs defaultValue="categories" className="space-y-6">
          <TabsList className="bg-secondary border border-border w-full overflow-x-auto flex-nowrap justify-start">
            <TabsTrigger value="categories" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Folder className="w-4 h-4 mr-2" />الأقسام
            </TabsTrigger>
            <TabsTrigger value="sidemenus" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Menu className="w-4 h-4 mr-2" />القوائم الجانبية
            </TabsTrigger>
            <TabsTrigger value="notifications" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Bell className="w-4 h-4 mr-2" />الإشعارات
            </TabsTrigger>
            <TabsTrigger value="ads" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Megaphone className="w-4 h-4 mr-2" />الإعلانات
            </TabsTrigger>
            <TabsTrigger value="security" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Lock className="w-4 h-4 mr-2" />الحماية
            </TabsTrigger>
            <TabsTrigger value="gate" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <DoorOpen className="w-4 h-4 mr-2" />نظام الدخول
            </TabsTrigger>
            <TabsTrigger value="users" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Users className="w-4 h-4 mr-2" />المستخدمون
            </TabsTrigger>
            <TabsTrigger value="appinfo" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Smartphone className="w-4 h-4 mr-2" />معلومات التطبيق
            </TabsTrigger>
            <TabsTrigger value="vip" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Crown className="w-4 h-4 mr-2" />اشتراكات VIP
            </TabsTrigger>
            <TabsTrigger value="cinema" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Film className="w-4 h-4 mr-2" />السينما والبث
            </TabsTrigger>
            <TabsTrigger value="cloudflare" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Cloud className="w-4 h-4 mr-2" />Cloudflare
            </TabsTrigger>
            <TabsTrigger value="settings" className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground">
              <Settings className="w-4 h-4 mr-2" />الإعدادات
            </TabsTrigger>
          </TabsList>

          <TabsContent value="gate">
            <div className="max-w-3xl">
              <GateConfigManager />
            </div>
          </TabsContent>

          <TabsContent value="categories" className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-1">
                <CategoryManager
                  onSelectCategory={(cat) => setSelectedCategory(cat ? { id: cat.id, name: cat.name } : null)}
                  selectedCategoryId={selectedCategory?.id || null}
                />
              </div>
              <div className="lg:col-span-2">
                {selectedCategory ? (
                  <ChannelManager categoryId={selectedCategory.id} categoryName={selectedCategory.name} />
                ) : (
                  <div className="border border-dashed border-border rounded-2xl p-12 text-center">
                    <Tv className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
                    <p className="text-muted-foreground">اختر قسماً لإدارة قنواته</p>
                  </div>
                )}
              </div>
            </div>
          </TabsContent>

          <TabsContent value="sidemenus"><SideMenuManager /></TabsContent>
          <TabsContent value="notifications"><div className="max-w-2xl"><NotificationManager /></div></TabsContent>
          <TabsContent value="ads"><div className="max-w-2xl"><AdConfigManager /></div></TabsContent>
          <TabsContent value="security"><div className="max-w-2xl"><SecurityConfigManager /></div></TabsContent>
          <TabsContent value="users">
            <div className="max-w-4xl space-y-6">
              <BanConfigManager />
              <AdminUsersManager />
            </div>
          </TabsContent>

          <TabsContent value="appinfo">
            <div className="max-w-2xl space-y-6">
              <AppInfoManager />
              <AppAssetsManager />
            </div>
          </TabsContent>

          <TabsContent value="vip"><div className="max-w-4xl"><VipManager /></div></TabsContent>

          <TabsContent value="cinema"><div className="max-w-3xl"><CinemaManager /></div></TabsContent>

          <TabsContent value="cloudflare"><div className="max-w-2xl"><CloudflareManager /></div></TabsContent>

          <TabsContent value="settings">
            <div className="max-w-2xl space-y-6">
              {/* Full system settings: Import JSON + Bulk offline cache + Encrypt + GitHub + behavior */}
              <SystemSettingsManager />

              <BackupButton />
              <AppUpdateManager />

              <div className="bg-card rounded-2xl p-6 border border-border space-y-6">
                <h3 className="text-lg font-bold text-foreground">إعدادات عامة</h3>
                <div className="space-y-4">
                  <div className="flex items-center justify-between gap-4 py-3 border-b border-border">
                    <div>
                      <p className="text-foreground font-medium">إظهار قسم الإعدادات داخل التطبيق</p>
                      <p className="text-xs text-muted-foreground mt-1">يظهر كآخر عنصر بعد جميع الأقسام.</p>
                    </div>
                    <Switch checked={showSettingsSection} disabled={!settingsConfigLoaded} onCheckedChange={handleSettingsSectionToggle} />
                  </div>
                  <div className="flex items-center justify-between py-3 border-b border-border">
                    <span className="text-muted-foreground">مسجل الدخول كـ</span>
                    <span className="text-foreground font-medium">{user.email}</span>
                  </div>
                  <div className="flex items-center justify-between py-3 border-b border-border">
                    <span className="text-muted-foreground">قاعدة البيانات</span>
                    <span className="text-primary font-medium">Lovable Cloud</span>
                  </div>
                  <div className="flex items-center justify-between py-3">
                    <span className="text-muted-foreground">إصدار اللوحة</span>
                    <span className="text-foreground font-medium">2.0.0</span>
                  </div>
                </div>
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </main>
    </div>
  );
};

export default AdminDashboard;
