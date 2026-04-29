CREATE POLICY "backup_history private"
ON public.backup_history FOR SELECT
USING (false);