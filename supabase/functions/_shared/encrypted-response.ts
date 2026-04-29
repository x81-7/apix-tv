// Shared helper: wraps any JSON response in AES-256-GCM envelope { iv, data }
// Uses ENCRYPTION_SECRET_KEY from environment (hex string → 32 bytes)
import { aesEncrypt, deriveMasterKey } from './crypto.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

let _cachedKey: Uint8Array | null = null;

async function getMasterKey(): Promise<Uint8Array> {
  if (_cachedKey) return _cachedKey;
  const secret = Deno.env.get('ENCRYPTION_SECRET_KEY');
  if (!secret) throw new Error('ENCRYPTION_SECRET_KEY not set');
  _cachedKey = await deriveMasterKey(secret);
  return _cachedKey;
}

/**
 * Returns an encrypted JSON response: { iv: string, data: string }
 * The `data` field is AES-256-GCM ciphertext of JSON.stringify(payload).
 */
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

/** Unencrypted JSON response (for errors before encryption is possible) */
export function plainJson(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}