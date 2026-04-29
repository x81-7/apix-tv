
-- Create public bucket for APK builds
INSERT INTO storage.buckets (id, name, public)
VALUES ('app-builds', 'app-builds', true)
ON CONFLICT (id) DO NOTHING;

-- Public read so devices can download
DROP POLICY IF EXISTS "Public read app-builds" ON storage.objects;
CREATE POLICY "Public read app-builds"
ON storage.objects FOR SELECT
USING (bucket_id = 'app-builds');

-- Authenticated users (admin panel users) can upload/replace/delete builds
DROP POLICY IF EXISTS "Auth upload app-builds" ON storage.objects;
CREATE POLICY "Auth upload app-builds"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'app-builds');

DROP POLICY IF EXISTS "Auth update app-builds" ON storage.objects;
CREATE POLICY "Auth update app-builds"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'app-builds');

DROP POLICY IF EXISTS "Auth delete app-builds" ON storage.objects;
CREATE POLICY "Auth delete app-builds"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'app-builds');
