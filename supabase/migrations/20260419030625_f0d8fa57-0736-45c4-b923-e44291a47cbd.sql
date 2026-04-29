
-- السماح للمشرفين المسجلين بكتابة الإعدادات والمفاتيح
CREATE POLICY "Auth users insert system_settings" ON public.system_settings
  FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Auth users update system_settings" ON public.system_settings
  FOR UPDATE TO authenticated USING (true);

-- encryption_keys: قراءة فقط للمصادقين، الكتابة عبر service role (Edge Function)
CREATE POLICY "Auth users read encryption_keys" ON public.encryption_keys
  FOR SELECT TO authenticated USING (true);

-- backup_history: قراءة وإدراج للمصادقين
CREATE POLICY "Auth users read backup_history" ON public.backup_history
  FOR SELECT TO authenticated USING (true);
CREATE POLICY "Auth users insert backup_history" ON public.backup_history
  FOR INSERT TO authenticated WITH CHECK (true);
