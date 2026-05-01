
ALTER TABLE public.categories REPLICA IDENTITY FULL;
ALTER TABLE public.channels REPLICA IDENTITY FULL;
ALTER TABLE public.side_menus REPLICA IDENTITY FULL;
ALTER TABLE public.sub_channels REPLICA IDENTITY FULL;
ALTER TABLE public.system_settings REPLICA IDENTITY FULL;
ALTER TABLE public.app_notifications REPLICA IDENTITY FULL;

ALTER PUBLICATION supabase_realtime ADD TABLE public.categories;
ALTER PUBLICATION supabase_realtime ADD TABLE public.channels;
ALTER PUBLICATION supabase_realtime ADD TABLE public.side_menus;
ALTER PUBLICATION supabase_realtime ADD TABLE public.sub_channels;
ALTER PUBLICATION supabase_realtime ADD TABLE public.system_settings;
ALTER PUBLICATION supabase_realtime ADD TABLE public.app_notifications;
