ALTER TABLE public.channels
ADD COLUMN IF NOT EXISTS ios_player_type text;

ALTER TABLE public.channels
ADD CONSTRAINT channels_ios_player_type_check
CHECK (ios_player_type IS NULL OR ios_player_type IN ('native', 'webview'));