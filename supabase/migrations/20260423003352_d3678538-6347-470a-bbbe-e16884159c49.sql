-- Seed default system settings for new gate-screen and app-behavior features
INSERT INTO public.system_settings (key, value, description) VALUES
  ('gateConfig', jsonb_build_object(
    'enabled', false,
    'bypassCode', '2026',
    'telegramUrl', 'https://t.me/apix_tv',
    'title', 'تشغيل يدوي',
    'subtitle', 'أدخل بيانات البث أو كود الدخول'
  ), 'Gate screen configuration: enabled flag, bypass code, telegram link')
ON CONFLICT (key) DO NOTHING;

INSERT INTO public.system_settings (key, value, description) VALUES
  ('appSettings', jsonb_build_object(
    'showSettingsSection', true,
    'darkModeDefault', true,
    'allowOrientationChange', true
  ), 'General app behavior settings')
ON CONFLICT (key) DO NOTHING;

-- Notification table for direct realtime push (no polling)
CREATE TABLE IF NOT EXISTS public.app_notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title text NOT NULL,
  body text NOT NULL,
  action jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '7 days')
);

ALTER TABLE public.app_notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public read app_notifications" ON public.app_notifications;
CREATE POLICY "Public read app_notifications"
  ON public.app_notifications
  FOR SELECT
  TO public
  USING (expires_at > now());

-- Enable realtime
ALTER PUBLICATION supabase_realtime ADD TABLE public.app_notifications;
ALTER TABLE public.app_notifications REPLICA IDENTITY FULL;