-- Public storage bucket that hosts the rotated external key blob.
-- The file at rest is itself AES-256-GCM encrypted with MASTER_ENCRYPTION_KEY,
-- so making the bucket public is safe and lets the Android client fetch it
-- with no auth / no extra headers.

insert into storage.buckets (id, name, public)
values ('encrypted-keys', 'encrypted-keys', true)
on conflict (id) do update set public = excluded.public;

-- Public read of the bucket
do $$
begin
  if not exists (
    select 1 from pg_policies
    where policyname = 'Public read of encrypted-keys'
      and schemaname = 'storage'
      and tablename = 'objects'
  ) then
    create policy "Public read of encrypted-keys"
      on storage.objects for select
      using (bucket_id = 'encrypted-keys');
  end if;
end $$;

-- Only the service role (used by edge functions) may write/update/delete.
-- Service role bypasses RLS, so no explicit policy is needed for it,
-- and we intentionally do NOT grant insert/update/delete to anon/auth roles.
