import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import { aesDecrypt, deriveMasterKey } from '../_shared/crypto.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
};

/**
 * Returns ONLY the external key half (base64) by unwrapping it server-side
 * with MASTER_ENCRYPTION_KEY. This is a fallback channel when the public storage fetch fails.
 *
 * The Android app must combine: SHA-256(internal || external || salt) to get
 * the final AES key. Both `internal` and `salt` are baked into the APK.
 */
Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const MASTER = Deno.env.get('MASTER_ENCRYPTION_KEY');
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    if (!MASTER) throw new Error('MASTER_ENCRYPTION_KEY not configured');

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);
    const { data: row, error } = await supabase
      .from('encryption_keys')
      .select('key_version, encrypted_key, algorithm, activated_at')
      .eq('is_active', true)
      .order('key_version', { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error) throw error;
    if (!row) throw new Error('No active encryption key');

    const parsed = JSON.parse(row.encrypted_key);
    const masterKey = await deriveMasterKey(MASTER);

    let externalB64: string;
    if (parsed.external_key_wrapped) {
      // Hybrid format
      externalB64 = await aesDecrypt(
        masterKey,
        parsed.external_key_wrapped.iv,
        parsed.external_key_wrapped.data,
      );
    } else if (parsed.iv && parsed.data) {
      // Legacy single-key format
      externalB64 = await aesDecrypt(masterKey, parsed.iv, parsed.data);
    } else {
      throw new Error('Unknown key format');
    }

    return new Response(JSON.stringify({
      success: true,
      keyVersion: row.key_version,
      algorithm: row.algorithm,
      activatedAt: row.activated_at,
      externalKey: externalB64,
    }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('get-decryption-key error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
