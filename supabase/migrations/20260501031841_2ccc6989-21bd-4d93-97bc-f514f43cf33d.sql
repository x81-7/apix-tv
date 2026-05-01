-- VIP Subscriptions table
CREATE TABLE public.vip_subscriptions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username text NOT NULL,
  notes text,
  starts_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  device_ids text[] NOT NULL DEFAULT '{}',
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT vip_max_5_devices CHECK (array_length(device_ids, 1) IS NULL OR array_length(device_ids, 1) <= 5)
);

CREATE INDEX idx_vip_device_ids ON public.vip_subscriptions USING GIN (device_ids);
CREATE INDEX idx_vip_expires ON public.vip_subscriptions (expires_at);

ALTER TABLE public.vip_subscriptions ENABLE ROW LEVEL SECURITY;

-- Deny all direct access — only edge functions (service role) can read.
CREATE POLICY "vip_subscriptions private"
  ON public.vip_subscriptions FOR SELECT
  USING (false);

CREATE TRIGGER trg_vip_updated_at
BEFORE UPDATE ON public.vip_subscriptions
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Add to realtime so admin panel updates live
ALTER TABLE public.vip_subscriptions REPLICA IDENTITY FULL;
ALTER PUBLICATION supabase_realtime ADD TABLE public.vip_subscriptions;