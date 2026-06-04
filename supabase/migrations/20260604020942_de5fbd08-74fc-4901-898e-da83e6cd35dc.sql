-- Lock down cinema_providers to service_role only. The dashboard reads/writes
-- exclusively through the cinema-gateway edge function (service role), so no
-- direct client access is needed. This removes the permissive RLS policy.
DROP POLICY IF EXISTS "Authenticated admins manage cinema providers" ON public.cinema_providers;
REVOKE SELECT, INSERT, UPDATE, DELETE ON public.cinema_providers FROM authenticated;
-- RLS stays enabled with NO policies => no anon/authenticated access.
-- service_role bypasses RLS and retains full access via the earlier GRANT ALL.