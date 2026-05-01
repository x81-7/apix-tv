// supabase/functions/list-vip/index.ts
// Returns list of VIP subscriptions for the admin panel.
// Uses service role; the frontend already gates this UI behind admin auth.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const url = Deno.env.get('SUPABASE_URL')!;
    const srv = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const sb = createClient(url, srv, { auth: { persistSession: false } });
    const { data, error } = await sb
      .from('vip_subscriptions')
      .select('id, username, notes, starts_at, expires_at, device_ids, active')
      .order('expires_at', { ascending: false });
    if (error) throw error;
    return new Response(JSON.stringify({ success: true, rows: data || [] }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e) {
    return new Response(JSON.stringify({ success: false, error: e instanceof Error ? e.message : String(e) }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
