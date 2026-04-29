// Shared helper: wraps any JSON response in AES-256-GCM envelope { iv, data }
// Key resolution order:
//   1. system_settings.security_config.cloudDecryptionKey  (managed via panel)
//   2. ENCRYPTION_SECRET_KEY env var                       (legacy fallback)
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import { aesEncrypt, deriveMasterKey } from './crypto.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

let _cachedKey: Uint8Array | null = null;
let _cachedAt = 0;
const CACHE_TTL_MS = 60_000;

async function loadKeyFromPanel(): Promise<string | null> {
  try {
    const url = Deno.env.get('SUPABASE_URL');
    const srv = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    if (!url || !srv) return null;
    const sb = createClient(url, srv, { auth: { persistSession: false } });
    const { data } = await sb
      .from('system_settings')
      .select('value')
      .eq('key', 'security_config')
      .maybeSingle();
    const v = (data?.value ?? null) as Record<string, unknown> | null;
    const k = v?.cloudDecryptionKey;
    if (typeof k === 'string' && k.trim()) return k.trim();
  } catch (_) { /* ignore — fall back to env */ }
  return null;
}

async function getMasterKey(): Promise<Uint8Array> {
  if (_cachedKey && Date.now() - _cachedAt < CACHE_TTL_MS) return _cachedKey;
  let secret = await loadKeyFromPanel();
  if (!secret) secret = Deno.env.get('ENCRYPTION_SECRET_KEY') ?? '';
  if (!secret) throw new Error('No encryption key — set Cloud Decryption Key in panel');
  _cachedKey = await deriveMasterKey(secret);
  _cachedAt = Date.now();
  return _cachedKey;
}

/** Force refresh next call (used after panel updates the key). */
export function invalidateKeyCache() { _cachedKey = null; _cachedAt = 0; }

export async function encryptedJson(
  payload: unknown,
  status = 200,
  extraHeaders: Record<string, string> = {},
): Promise<Response> {
  const key = await getMasterKey();
  const plaintext = JSON.stringify(payload);
  const envelope = await aesEncrypt(key, plaintext);
  return new Response(JSON.stringify(envelope), {
    status,
    headers: {
      ...corsHeaders,
      'Content-Type': 'application/json',
      'X-Payload-Encryption': 'AES-256-GCM',
      ...extraHeaders,
    },
  });
}

export function plainJson(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}
