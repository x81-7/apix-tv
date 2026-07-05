CREATE OR REPLACE FUNCTION public.emit_ban_signal()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
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

REVOKE EXECUTE ON FUNCTION public.emit_ban_signal() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.emit_ban_signal() TO service_role;