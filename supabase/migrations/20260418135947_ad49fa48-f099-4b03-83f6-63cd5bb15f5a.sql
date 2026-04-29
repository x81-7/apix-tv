-- ============================================================
-- HYBRID CLOUD SYSTEM - Phase 1 Schema
-- ============================================================

-- 1) system_settings: إعدادات النظام العامة (Key/Value)
CREATE TABLE public.system_settings (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  key TEXT NOT NULL UNIQUE,
  value JSONB,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) encryption_keys: مفاتيح التشفير المُدوّرة
CREATE TABLE public.encryption_keys (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  key_version INTEGER NOT NULL UNIQUE,
  encrypted_key TEXT NOT NULL,           -- المفتاح بعد تشفيره بـ Master Key
  algorithm TEXT NOT NULL DEFAULT 'AES-256-GCM',
  is_active BOOLEAN NOT NULL DEFAULT false,
  activated_at TIMESTAMPTZ,
  rotated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3) categories: الأقسام
CREATE TABLE public.categories (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,                        -- معرّف Firebase الأصلي للمزامنة
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4) side_menus: القوائم الجانبية
CREATE TABLE public.side_menus (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_id TEXT,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) channels: القنوات
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
  -- إعدادات البث (مرنة لتغطية كل الحقول الموجودة في FirebaseModels)
  web_stream JSONB,           -- { url, userAgent, referrer, cookies, drm: {...} }
  android_stream JSONB,       -- AndroidStreamConfig كامل
  android_action_type TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_channels_category ON public.channels(category_id);
CREATE INDEX idx_channels_side_menu ON public.channels(side_menu_id);

-- 6) sub_channels: القنوات الفرعية داخل القوائم الجانبية
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
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sub_channels_side_menu ON public.sub_channels(side_menu_id);

-- 7) backup_history: سجل النسخ الاحتياطية
CREATE TABLE public.backup_history (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  source TEXT NOT NULL,                  -- 'firebase' | 'supabase'
  size_bytes BIGINT,
  storage_path TEXT,                     -- مسار في storage إذا تم رفعه
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- updated_at trigger function
-- ============================================================
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

CREATE TRIGGER trg_system_settings_updated BEFORE UPDATE ON public.system_settings
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_categories_updated BEFORE UPDATE ON public.categories
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_side_menus_updated BEFORE UPDATE ON public.side_menus
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_channels_updated BEFORE UPDATE ON public.channels
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER trg_sub_channels_updated BEFORE UPDATE ON public.sub_channels
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- ============================================================
-- RLS — Public read for app content; writes restricted to service_role only
-- (Admin panel writes via Edge Functions using service_role key)
-- ============================================================
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.encryption_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.side_menus ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sub_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backup_history ENABLE ROW LEVEL SECURITY;

-- Public READ for content tables (app needs to fetch data without auth)
CREATE POLICY "Public read categories" ON public.categories FOR SELECT USING (true);
CREATE POLICY "Public read side_menus" ON public.side_menus FOR SELECT USING (true);
CREATE POLICY "Public read channels" ON public.channels FOR SELECT USING (true);
CREATE POLICY "Public read sub_channels" ON public.sub_channels FOR SELECT USING (true);

-- Settings: only non-sensitive settings publicly readable; sensitive ones blocked
-- For now we keep it permissive on read, but encryption_keys & backup_history are PRIVATE.
CREATE POLICY "Public read system_settings" ON public.system_settings FOR SELECT USING (true);

-- encryption_keys & backup_history: NO public access. Only service_role (bypasses RLS) can access.
-- (No SELECT policy = no one with anon key can read them.)

-- No INSERT/UPDATE/DELETE policies => only service_role can write to all tables.
