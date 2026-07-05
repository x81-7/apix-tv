CREATE TABLE IF NOT EXISTS public.ban_signals (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id  TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ban_signals_device ON public.ban_signals(device_id);

GRANT SELECT ON public.ban_signals TO anon, authenticated;
GRANT ALL    ON public.ban_signals TO service_role;

ALTER TABLE public.ban_signals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "ban_signals readable" ON public.ban_signals;
CREATE POLICY "ban_signals readable" ON public.ban_signals
  FOR SELECT USING (true);

CREATE OR REPLACE FUNCTION public.emit_ban_signal()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW.status IS NOT NULL
     AND upper(NEW.status) <> 'ACTIVE'
     AND upper(NEW.status) NOT LIKE 'UNBAN%' THEN
    INSERT INTO public.ban_signals(device_id) VALUES (NEW.device_id);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_emit_ban_signal ON public.ban_history;
CREATE TRIGGER trg_emit_ban_signal
  AFTER INSERT ON public.ban_history
  FOR EACH ROW EXECUTE FUNCTION public.emit_ban_signal();

ALTER TABLE public.ban_signals REPLICA IDENTITY FULL;
DO $$
BEGIN
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.ban_signals;
  EXCEPTION WHEN duplicate_object THEN
    NULL;
  END;
END $$;