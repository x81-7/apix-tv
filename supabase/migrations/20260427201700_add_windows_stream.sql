-- Add Windows-specific stream + action type to channels and sub_channels.
-- Mirrors the existing android_stream / android_action_type design so the
-- desktop app (windows/) can have its own URLs and player choice.
alter table public.channels
  add column if not exists windows_stream jsonb,
  add column if not exists windows_action_type text;

alter table public.sub_channels
  add column if not exists windows_stream jsonb,
  add column if not exists windows_action_type text;
