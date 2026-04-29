-- ============================================================
-- APiX TV — full consolidated schema for fresh Cloud project
-- ============================================================

-- updated_at helper
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

-- bump_cache_version helper
CREATE OR REPLACE FUNCTION public.bump_cache_version()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  NEW.cache_version := COALESCE(OLD.cache_version, 0) + 1;
  NEW.updated_at    := now();
  RETURN NEW;
END;
$$;

-- 1) system_settings
CREATE TABLE public.system_settings (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  key TEXT NOT NULL UNIQUE,
  value JSONB,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) encryption_keys
CREATE TABLE public.encryption_keys (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  key_version INTEGER NOT NULL UNIQUE,
  encrypted_key TEXT NOT NULL,
  algorithm TEXT NOT NULL DEFAULT 'AES-256-GCM',
  is_active BOOLEAN NOT NULL DEFAULT false,
  activated_at TIMESTAMPTZ,
  rotated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3) categories
CREATE TABLE public.categories (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4) side_menus
CREATE TABLE public.side_menus (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  pin_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) channels
CREATE TABLE public.channels (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,
  category_id UUID REFERENCES public.categories(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  image_url TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  action_type TEXT NOT NULL DEFAULT 'direct_play',
  side_menu_id UUID REFERENCES public.side_menus(id) ON DELETE SET NULL,
  external_url TEXT,
  preferred_player TEXT,
  web_stream JSONB,
  android_stream JSONB,
  android_action_type TEXT,
  ios_stream JSONB,
  ios_action_type TEXT,
  ios_player_type TEXT CHECK (ios_player_type IS NULL OR ios_player_type IN ('native','webview')),
  windows_stream JSONB,
  windows_action_type TEXT,
  offline_cache_enabled BOOLEAN NOT NULL DEFAULT false,
  pin_code TEXT,
  cache_version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_channels_category ON public.channels(category_id);
CREATE INDEX idx_channels_side_menu ON public.channels(side_menu_id);

-- 6) sub_channels
CREATE TABLE public.sub_channels (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,
  side_menu_id UUID NOT NULL REFERENCES public.side_menus(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  image_url TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  preferred_player TEXT,
  web_stream JSONB,
  android_stream JSONB,
  android_action_type TEXT,
  ios_stream JSONB,
  ios_action_type TEXT,
  windows_stream JSONB,
  windows_action_type TEXT,
  offline_cache_enabled BOOLEAN NOT NULL DEFAULT false,
  pin_code TEXT,
  cache_version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sub_channels_side_menu ON public.sub_channels(side_menu_id);

-- 7) backup_history
CREATE TABLE public.backup_history (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  source TEXT NOT NULL,
  size_bytes BIGINT,
  storage_path TEXT,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8) custom_ads
CREATE TABLE public.custom_ads (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  video_url TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_custom_ads_sort_order ON public.custom_ads (sort_order, created_at);

-- 9) app_notifications
CREATE TABLE public.app_notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  action JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '7 days')
);

-- 10) app_users / ban_history / integrity_logs
CREATE TABLE public.app_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT NOT NULL UNIQUE,
  ip_address TEXT,
  country TEXT,
  city TEXT,
  region TEXT,
  install_count INTEGER NOT NULL DEFAULT 1,
  strike_count INTEGER NOT NULL DEFAULT 0,
  last_strike_at TIMESTAMPTZ,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  ban_until TIMESTAMPTZ,
  ban_reason TEXT,
  signature_hash TEXT,
  dex_checksum TEXT,
  app_version TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_users_status ON public.app_users(status);
CREATE INDEX idx_app_users_last_seen ON public.app_users(last_seen_at);

CREATE TABLE public.ban_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT NOT NULL,
  status TEXT NOT NULL,
  reason TEXT,
  ip_address TEXT,
  ban_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ban_history_device ON public.ban_history(device_id);

CREATE TABLE public.integrity_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT NOT NULL,
  signature_hash TEXT,
  dex_checksum TEXT,
  threat_type TEXT NOT NULL,
  ip_address TEXT,
  details JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_integrity_device ON public.integrity_logs(device_id);

-- updated_at + cache_version triggers
CREATE TRIGGER trg_system_settings_updated BEFORE UPDATE ON public.system_settings
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_categories_updated BEFORE UPDATE ON public.categories
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_side_menus_updated BEFORE UPDATE ON public.side_menus
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_custom_ads_updated BEFORE UPDATE ON public.custom_ads
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_app_users_updated BEFORE UPDATE ON public.app_users
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trg_channels_bump_version
BEFORE UPDATE ON public.channels
FOR EACH ROW EXECUTE FUNCTION public.bump_cache_version();

CREATE TRIGGER trg_sub_channels_bump_version
BEFORE UPDATE ON public.sub_channels
FOR EACH ROW EXECUTE FUNCTION public.bump_cache_version();

-- RLS
ALTER TABLE public.system_settings    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.encryption_keys    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.side_menus         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channels           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sub_channels       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backup_history     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.custom_ads         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_notifications  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_users          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ban_history        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.integrity_logs     ENABLE ROW LEVEL SECURITY;

-- Public read policies
CREATE POLICY "Public read categories"      ON public.categories      FOR SELECT USING (true);
CREATE POLICY "Public read side_menus"      ON public.side_menus      FOR SELECT USING (true);
CREATE POLICY "Public read channels"        ON public.channels        FOR SELECT USING (true);
CREATE POLICY "Public read sub_channels"    ON public.sub_channels    FOR SELECT USING (true);
CREATE POLICY "Public read system_settings" ON public.system_settings FOR SELECT USING (true);
CREATE POLICY "Public read visible custom ads" ON public.custom_ads   FOR SELECT USING (hidden = false);
CREATE POLICY "Public read active encryption key" ON public.encryption_keys FOR SELECT USING (is_active = true);
CREATE POLICY "Public read app_notifications" ON public.app_notifications FOR SELECT TO public USING (expires_at > now());

-- Locked-down (writes via service role / edge functions only)
CREATE POLICY "app_users private"      ON public.app_users      FOR SELECT USING (false);
CREATE POLICY "ban_history private"    ON public.ban_history    FOR SELECT USING (false);
CREATE POLICY "integrity_logs private" ON public.integrity_logs FOR SELECT USING (false);

-- Realtime for notifications
ALTER PUBLICATION supabase_realtime ADD TABLE public.app_notifications;
ALTER TABLE public.app_notifications REPLICA IDENTITY FULL;

-- Storage buckets
INSERT INTO storage.buckets (id, name, public) VALUES ('app-builds', 'app-builds', true)
ON CONFLICT (id) DO NOTHING;
INSERT INTO storage.buckets (id, name, public) VALUES ('encrypted-keys', 'encrypted-keys', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Public read app-builds files"
ON storage.objects FOR SELECT
USING (bucket_id = 'app-builds' AND name IS NOT NULL);

CREATE POLICY "Auth upload app-builds"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (bucket_id = 'app-builds');

CREATE POLICY "Auth update app-builds"
ON storage.objects FOR UPDATE TO authenticated
USING (bucket_id = 'app-builds');

CREATE POLICY "Auth delete app-builds"
ON storage.objects FOR DELETE TO authenticated
USING (bucket_id = 'app-builds');

CREATE POLICY "Public read external_key.enc"
ON storage.objects FOR SELECT
USING (bucket_id = 'encrypted-keys' AND name = 'external_key.enc');

-- Seeds
INSERT INTO public.system_settings (key, value, description) VALUES
  ('gateConfig', jsonb_build_object(
    'enabled', false, 'bypassCode', '2026',
    'telegramUrl', 'https://t.me/apix_tv',
    'title', 'تشغيل يدوي',
    'subtitle', 'أدخل بيانات البث أو كود الدخول'
  ), 'Gate screen configuration'),
  ('appSettings', jsonb_build_object(
    'showSettingsSection', true, 'darkModeDefault', true, 'allowOrientationChange', true
  ), 'General app behavior settings'),
  ('security_config',
    '{"official_signature_sha256":"","telegram_url":"https://t.me/your_channel","temp_ban_minutes":15,"temp_ban_threshold":4,"perma_ban_threshold":6,"strike_window_hours":24,"reset_after_days":2,"integrity_check_enabled":true,"anti_debug_enabled":true,"anti_hook_enabled":true,"emulator_block_strict":true,"root_block":false,"developer_override_uuids":[]}'::jsonb,
    'Security configuration: strict emulator block, no root block, developer-bypass UUIDs')
ON CONFLICT (key) DO NOTHING;