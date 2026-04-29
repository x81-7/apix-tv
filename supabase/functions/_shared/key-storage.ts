// Helpers for storing the rotated external encryption key in Supabase Storage.
// The Android client reads from this public bucket; the file at rest is
// AES-256-GCM encrypted with MASTER_ENCRYPTION_KEY, so the public exposure
// only leaks the wrapped blob, never the plaintext key.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

export const KEY_BUCKET = 'encrypted-keys';
export const KEY_OBJECT = 'external_key.enc';

/**
 * Upload (or overwrite) the encrypted key blob in Supabase Storage and return
 * the publicly accessible URL the Android app should fetch.
 */
export async function uploadEncryptedKey(
  supabaseUrl: string,
  serviceRole: string,
  blobText: string,
): Promise<string> {
  const supabase = createClient(supabaseUrl, serviceRole);
  const bytes = new TextEncoder().encode(blobText);

  const { error } = await supabase.storage
    .from(KEY_BUCKET)
    .upload(KEY_OBJECT, bytes, {
      contentType: 'application/json',
      upsert: true,
      cacheControl: '60',
    });
  if (error) throw new Error('Storage upload failed: ' + error.message);

  const { data: pub } = supabase.storage.from(KEY_BUCKET).getPublicUrl(KEY_OBJECT);
  return pub.publicUrl;
}
