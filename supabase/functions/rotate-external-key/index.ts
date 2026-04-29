import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import { aesEncrypt, b64encode, deriveMasterKey } from '../_shared/crypto.ts';
import { uploadEncryptedKey } from '../_shared/key-storage.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers':
    'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

/**
 * Rotates the EXTERNAL key half (the half stored in Supabase Storage).
 * The INTERNAL half stays the same (derived from the app signature on device,
 * mirrored as a constant on the server inside encryption_keys.encrypted_key).
 *
 * Run on cron every 7 days. Also re-encrypts the data JSON via auto-encrypt-push.
 */
Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const MASTER = Deno.env.get('MASTER_ENCRYPTION_KEY');
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    if (!MASTER) throw new Error('MASTER_ENCRYPTION_KEY missing');

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    // 1) Generate fresh external key (32 bytes)
    const externalKey = crypto.getRandomValues(new Uint8Array(32));
    const externalB64 = b64encode(externalKey);

    // 2) Wrap it with master for storage in our DB (recovery)
    const masterKey = await deriveMasterKey(MASTER);
    const wrappedExternal = await aesEncrypt(masterKey, externalB64);

    // 3) Keep the internal half from current active row (do NOT rotate it)
    const { data: current } = await supabase
      .from('encryption_keys')
      .select('*')
      .eq('is_active', true)
      .order('key_version', { ascending: false })
      .limit(1)
      .maybeSingle();

    let internalB64: string;
    if (current) {
      internalB64 = JSON.parse(current.encrypted_key).internal_key_b64;
    } else {
      internalB64 = b64encode(crypto.getRandomValues(new Uint8Array(32)));
    }

    const nextVersion = (current?.key_version ?? 0) + 1;

    // 4) Build the public blob. The blob is itself master-wrapped, so the
    //    Android app must combine app-signature-derived secrets with this
    //    payload to recover the actual external key.
    const publicBlob = {
      version: nextVersion,
      algorithm: 'AES-256-GCM',
      payload: wrappedExternal,
      rotatedAt: new Date().toISOString(),
    };
    const blobText = JSON.stringify(publicBlob);

    // 5) Upload to Supabase Storage (public bucket, file is encrypted at rest)
    const downloadUrl = await uploadEncryptedKey(
      SUPABASE_URL,
      SERVICE_ROLE,
      blobText,
    );

    // 6) Persist new active row in DB
    await supabase
      .from('encryption_keys')
      .update({ is_active: false, rotated_at: new Date().toISOString() })
      .eq('is_active', true);

    await supabase.from('encryption_keys').insert({
      key_version: nextVersion,
      encrypted_key: JSON.stringify({
        internal_key_b64: internalB64,
        external_key_wrapped: wrappedExternal,
        public_url: downloadUrl,
      }),
      algorithm: 'AES-256-GCM-HYBRID',
      is_active: true,
      activated_at: new Date().toISOString(),
    });

    // 7) Re-encrypt the JSON with the new key (call sibling function)
    const fnRes = await fetch(`${SUPABASE_URL}/functions/v1/auto-encrypt-push`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${SERVICE_ROLE}`,
        'Content-Type': 'application/json',
      },
      body: '{}',
    });
    const fnJson = await fnRes.json().catch(() => ({}));

    return new Response(
      JSON.stringify({
        success: true,
        keyVersion: nextVersion,
        publicKeyUrl: downloadUrl,
        reEncrypt: fnJson,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } },
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('rotate-external-key error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
