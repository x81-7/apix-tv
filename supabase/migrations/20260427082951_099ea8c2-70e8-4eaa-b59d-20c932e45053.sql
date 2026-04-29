-- iOS dedicated stream config (parallel to web_stream / android_stream)
ALTER TABLE public.channels ADD COLUMN IF NOT EXISTS ios_stream jsonb;
ALTER TABLE public.sub_channels ADD COLUMN IF NOT EXISTS ios_stream jsonb;

-- PIN lock per side menu
ALTER TABLE public.side_menus ADD COLUMN IF NOT EXISTS pin_code text;