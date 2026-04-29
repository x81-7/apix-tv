
-- 1) Offline cache toggle on channels
ALTER TABLE public.channels ADD COLUMN IF NOT EXISTS offline_cache_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE public.sub_channels ADD COLUMN IF NOT EXISTS offline_cache_enabled boolean NOT NULL DEFAULT false;

-- 2) app_users table (device-based, no auth)
CREATE TABLE IF NOT EXISTS public.app_users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id text NOT NULL UNIQUE,
  ip_address text,
  country text,
  city text,
  region text,
  install_count integer NOT NULL DEFAULT 1,
  strike_count integer NOT NULL DEFAULT 0,
  last_strike_at timestamptz,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'ACTIVE',
  ban_until timestamptz,
  ban_reason text,
  signature_hash text,
  dex_checksum text,
  app_version text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public read app_users denied"
ON public.app_users FOR SELECT
USING (false);

-- 3) ban_history
CREATE TABLE IF NOT EXISTS public.ban_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id text NOT NULL,
  status text NOT NULL,
  reason text,
  ip_address text,
  ban_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.ban_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY "ban_history private"
ON public.ban_history FOR SELECT
USING (false);

-- 4) integrity_logs
CREATE TABLE IF NOT EXISTS public.integrity_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id text NOT NULL,
  signature_hash text,
  dex_checksum text,
  threat_type text NOT NULL,
  ip_address text,
  details jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.integrity_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "integrity_logs private"
ON public.integrity_logs FOR SELECT
USING (false);

-- 5) Trigger updated_at
DROP TRIGGER IF EXISTS trg_app_users_updated ON public.app_users;
CREATE TRIGGER trg_app_users_updated
BEFORE UPDATE ON public.app_users
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Indexes
CREATE INDEX IF NOT EXISTS idx_app_users_status ON public.app_users(status);
CREATE INDEX IF NOT EXISTS idx_app_users_last_seen ON public.app_users(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_ban_history_device ON public.ban_history(device_id);
CREATE INDEX IF NOT EXISTS idx_integrity_device ON public.integrity_logs(device_id);

-- 6) System settings defaults for security
INSERT INTO public.system_settings (key, value, description) VALUES
  ('security_config', '{"official_signature_sha256":"","telegram_url":"https://t.me/your_channel","temp_ban_minutes":15,"temp_ban_threshold":4,"perma_ban_threshold":6,"strike_window_hours":24,"reset_after_days":2,"integrity_check_enabled":true,"anti_debug_enabled":true,"anti_hook_enabled":true}'::jsonb, 'Anti-tamper and ban system configuration')
ON CONFLICT (key) DO NOTHING;
