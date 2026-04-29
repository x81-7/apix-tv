
-- Tighten RLS: writes go through edge functions only (service role bypasses RLS)
-- Drop permissive authenticated policies and restrict to service_role for writes

DROP POLICY IF EXISTS "Auth users insert system_settings" ON public.system_settings;
DROP POLICY IF EXISTS "Auth users update system_settings" ON public.system_settings;
DROP POLICY IF EXISTS "Auth users insert backup_history" ON public.backup_history;
DROP POLICY IF EXISTS "Auth users read backup_history" ON public.backup_history;
DROP POLICY IF EXISTS "Auth users read encryption_keys" ON public.encryption_keys;

-- Public can read system_settings (existing) — keep as-is
-- Encryption keys: allow public to read ONLY active key (for Android decrypt)
CREATE POLICY "Public read active encryption key"
ON public.encryption_keys FOR SELECT
USING (is_active = true);

-- backup_history: admin reads only via edge function (service role)
-- No public select policy => locked down
