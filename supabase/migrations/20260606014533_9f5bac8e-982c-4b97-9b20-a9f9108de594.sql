ALTER TABLE public.cinema_providers
  ADD COLUMN IF NOT EXISTS movie_link_template text,
  ADD COLUMN IF NOT EXISTS series_link_template text,
  ADD COLUMN IF NOT EXISTS anime_enabled boolean NOT NULL DEFAULT true;

CREATE TABLE IF NOT EXISTS public.cinema_catalog (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  section text NOT NULL DEFAULT 'vod',
  tmdb_id text NOT NULL,
  title text NOT NULL DEFAULT '',
  poster text,
  backdrop text,
  description text,
  rating text,
  year text,
  popularity double precision NOT NULL DEFAULT 0,
  row_key text NOT NULL DEFAULT 'popular',
  row_title text NOT NULL DEFAULT '',
  is_hero boolean NOT NULL DEFAULT false,
  sort_order integer NOT NULL DEFAULT 0,
  extra jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  UNIQUE (section, tmdb_id, row_key)
);

CREATE INDEX IF NOT EXISTS cinema_catalog_section_idx ON public.cinema_catalog (section);
CREATE INDEX IF NOT EXISTS cinema_catalog_row_idx ON public.cinema_catalog (row_key);

GRANT SELECT ON public.cinema_catalog TO anon;
GRANT SELECT ON public.cinema_catalog TO authenticated;
GRANT ALL ON public.cinema_catalog TO service_role;

ALTER TABLE public.cinema_catalog ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Catalog is publicly readable"
  ON public.cinema_catalog FOR SELECT
  USING (true);

CREATE TRIGGER update_cinema_catalog_updated_at
  BEFORE UPDATE ON public.cinema_catalog
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();