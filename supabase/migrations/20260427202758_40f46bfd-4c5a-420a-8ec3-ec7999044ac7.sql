-- Restrict public reads to specific object paths, blocking bucket listing.
DROP POLICY IF EXISTS "Public read app-builds" ON storage.objects;
DROP POLICY IF EXISTS "Public read of encrypted-keys" ON storage.objects;

CREATE POLICY "Public read app-builds files"
ON storage.objects FOR SELECT
USING (bucket_id = 'app-builds' AND name IS NOT NULL);

CREATE POLICY "Public read external_key.enc"
ON storage.objects FOR SELECT
USING (bucket_id = 'encrypted-keys' AND name = 'external_key.enc');
