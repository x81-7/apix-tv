-- Cinema / IPTV provider configuration (server-side only).
-- Holds Xtream Codes credentials + TMDB key. NEVER exposed to anon clients;
-- the cinema-gateway edge function reads it with the service role and proxies
-- requests so credentials never leave the server.
CREATE TABLE IF NOT EXISTS public.cinema_providers (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  name text NOT NULL DEFAULT 'Primary',
  kind text NOT NULL DEFAULT 'xtream',
  host text,
  port integer,
  username text,
  password text,
  tmdb_api_key text,
  vod_enabled boolean NOT NULL DEFAULT true,
  series_enabled boolean NOT NULL DEFAULT true,
  live_enabled boolean NOT NULL DEFAULT true,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Grants: admin panel runs as an authenticated user; edge functions run as
-- service_role. Anon (the apps) must NEVER read provider credentials.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.cinema_providers TO authenticated;
GRANT ALL ON public.cinema_providers TO service_role;

ALTER TABLE public.cinema_providers ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated admins manage cinema providers"
  ON public.cinema_providers FOR ALL TO authenticated
  USING (true) WITH CHECK (true);

CREATE TRIGGER update_cinema_providers_updated_at
  BEFORE UPDATE ON public.cinema_providers
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();