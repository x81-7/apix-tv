import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    const { key, value, description } = await req.json();
    if (!key) throw new Error('key is required');

    const { data: existing } = await supabase
      .from('system_settings').select('id').eq('key', key).maybeSingle();

    if (existing?.id) {
      const { error } = await supabase
        .from('system_settings')
        .update({ value, description: description ?? null })
        .eq('id', existing.id);
      if (error) throw error;
    } else {
      const { error } = await supabase
        .from('system_settings')
        .insert({ key, value, description: description ?? null });
      if (error) throw error;
    }

    // Drop cached-data warm cache so apps see updated settings on next poll.
    try {
      await fetch(`${SUPABASE_URL}/functions/v1/cached-data/invalidate`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${SERVICE_ROLE}`, 'Content-Type': 'application/json' },
        body: '{}',
      });
    } catch (e) {
      console.warn('cached-data invalidate failed', e);
    }

    // Auto re-encrypt + push to GitHub
    let reencrypt: unknown = null;
    try {
      const r = await fetch(`${SUPABASE_URL}/functions/v1/auto-encrypt-push`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${SERVICE_ROLE}`,
          'Content-Type': 'application/json',
        },
        body: '{}',
      });
      reencrypt = await r.json().catch(() => null);
    } catch (e) {
      console.warn('re-encrypt failed', e);
    }

    return new Response(JSON.stringify({ success: true, reencrypt }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
