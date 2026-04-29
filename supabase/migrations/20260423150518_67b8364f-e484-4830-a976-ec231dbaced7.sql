CREATE TABLE public.custom_ads (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  video_url TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.custom_ads ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public read visible custom ads"
ON public.custom_ads
FOR SELECT
USING (hidden = false);

CREATE INDEX idx_custom_ads_sort_order
ON public.custom_ads (sort_order, created_at);

CREATE TRIGGER update_custom_ads_updated_at
BEFORE UPDATE ON public.custom_ads
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();