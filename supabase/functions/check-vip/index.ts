// supabase/functions/check-vip/index.ts
// Checks if a device_id has an active VIP subscription.
// Returns an encrypted envelope: { active, expiresAt, vipToken? }
//
// When VIP_JWT_SECRET is configured it ALSO returns a compact HS256 JWT
// (vipToken) that the Android/iOS/Windows apps verify ENTIRELY in native code
// (see sec.cpp / x.verifyVip). A man-in-the-middle cannot forge {"vip":true}
// because the signing secret lives only in native + this function.
// IMPORTANT: VIP_JWT_SECRET must equal the app's native HMAC secret (the
// HMAC_SECRET build value). If it is not set, apps fall back to the (already
// MitM-resistant, AES-GCM-encrypted) `active` flag.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import { encryptedJson } from '../_shared/encrypted-response.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

function b64url(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlStr(str: string): string {
  return b64url(new TextEncoder().encode(str));
}

function normalizeDeviceId(value: unknown): string {
  const id = typeof value === 'string' ? value.trim() : '';
  return /^(?:[a-z]{2,4}_)?[0-9a-f]{32,128}$/i.test(id) ? id.toLowerCase() : id;
}

/** Sign a compact HS256 JWT with claims {vip, device_id, exp}. */
async function signVipToken(deviceId: string, expiresAtMs: number): Promise<string | null> {
  const secret = Deno.env.get('VIP_JWT_SECRET');
  if (!secret) return null;
  const header = { alg: 'HS256', typ: 'JWT' };
  // exp: min(subscription expiry, now+12h) so a stolen token is short-lived.
  const nowSec = Math.floor(Date.now() / 1000);
  const subExpSec = Math.floor(expiresAtMs / 1000);
  const exp = Math.min(subExpSec, nowSec + 12 * 3600);
  const payload = { vip: true, device_id: deviceId, iat: nowSec, exp };
  const signingInput = `${b64urlStr(JSON.stringify(header))}.${b64urlStr(JSON.stringify(payload))}`;
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'],
  );
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(signingInput));
  return `${signingInput}.${b64url(new Uint8Array(sig))}`;
}


Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }
  try {
    const body = await req.json().catch(() => ({}));
    const deviceId = normalizeDeviceId(body?.device_id);
    if (!deviceId) {
      return await encryptedJson({ active: false, expiresAt: null, reason: 'missing_device_id' }, 200);
    }

    const url = Deno.env.get('SUPABASE_URL')!;
    const srv = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const sb = createClient(url, srv, { auth: { persistSession: false } });

    const { data: candidates, error } = await sb
      .from('vip_subscriptions')
      .select('expires_at, active, device_ids')
      .eq('active', true)
      .order('expires_at', { ascending: false })
      .limit(500);

    if (error) {
      console.error('[check-vip] db error', error);
      return await encryptedJson({ active: false, expiresAt: null, reason: 'db_error' }, 200);
    }

    const data = (candidates ?? []).find((row: any) =>
      Array.isArray(row.device_ids) && row.device_ids.some((id: unknown) => normalizeDeviceId(id) === deviceId)
    );
    if (!data) {
      return await encryptedJson({ active: false, expiresAt: null }, 200);
    }
    const now = Date.now();
    const exp = new Date(data.expires_at).getTime();
    const active = exp > now;
    const vipToken = active ? await signVipToken(deviceId, exp) : null;
    return await encryptedJson(
      { active, expiresAt: data.expires_at, vipToken },
      200,
    );
  } catch (e) {
    console.error('[check-vip]', e);
    return await encryptedJson({ active: false, expiresAt: null, reason: 'exception' }, 200);
  }
});
