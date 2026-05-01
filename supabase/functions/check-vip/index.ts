// supabase/functions/check-vip/index.ts
// Checks if a device_id has an active VIP subscription.
// Returns an encrypted envelope: { active: bool, expiresAt: string | null }
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import { encryptedJson } from '../_shared/encrypted-response.ts';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }
  try {
    const body = await req.json().catch(() => ({}));
    const deviceId = typeof body?.device_id === 'string' ? body.device_id.trim() : '';
    if (!deviceId) {
      return await encryptedJson({ active: false, expiresAt: null, reason: 'missing_device_id' }, 200);
    }

    const url = Deno.env.get('SUPABASE_URL')!;
    const srv = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const sb = createClient(url, srv, { auth: { persistSession: false } });

    const { data, error } = await sb
      .from('vip_subscriptions')
      .select('expires_at, active, device_ids')
      .contains('device_ids', [deviceId])
      .eq('active', true)
      .order('expires_at', { ascending: false })
      .limit(1)
      .maybeSingle();

    if (error) {
      console.error('[check-vip] db error', error);
      return await encryptedJson({ active: false, expiresAt: null, reason: 'db_error' }, 200);
    }

    if (!data) {
      return await encryptedJson({ active: false, expiresAt: null }, 200);
    }
    const now = Date.now();
    const exp = new Date(data.expires_at).getTime();
    const active = exp > now;
    return await encryptedJson(
      { active, expiresAt: data.expires_at },
      200,
    );
  } catch (e) {
    console.error('[check-vip]', e);
    return await encryptedJson({ active: false, expiresAt: null, reason: 'exception' }, 200);
  }
});
