
-- Add cache_version columns (used by Android/iOS/Windows to detect panel changes)
ALTER TABLE public.channels     ADD COLUMN IF NOT EXISTS cache_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE public.sub_channels ADD COLUMN IF NOT EXISTS cache_version BIGINT NOT NULL DEFAULT 1;

-- Auto-bump cache_version + updated_at on every UPDATE
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

DROP TRIGGER IF EXISTS trg_channels_bump_version     ON public.channels;
DROP TRIGGER IF EXISTS trg_sub_channels_bump_version ON public.sub_channels;

CREATE TRIGGER trg_channels_bump_version
BEFORE UPDATE ON public.channels
FOR EACH ROW EXECUTE FUNCTION public.bump_cache_version();

CREATE TRIGGER trg_sub_channels_bump_version
BEFORE UPDATE ON public.sub_channels
FOR EACH ROW EXECUTE FUNCTION public.bump_cache_version();

-- Also keep updated_at fresh on categories / side_menus / system_settings
DROP TRIGGER IF EXISTS trg_categories_updated_at    ON public.categories;
DROP TRIGGER IF EXISTS trg_side_menus_updated_at    ON public.side_menus;
DROP TRIGGER IF EXISTS trg_system_settings_updated_at ON public.system_settings;

CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON public.categories
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trg_side_menus_updated_at
BEFORE UPDATE ON public.side_menus
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trg_system_settings_updated_at
BEFORE UPDATE ON public.system_settings
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
