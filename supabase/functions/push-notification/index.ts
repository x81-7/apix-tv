import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers':
    'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { title, body, action, channelId } = await req.json();
    if (!title || !body) throw new Error('title and body are required');

    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    const sentAt = Date.now();
    const notification = {
      id: crypto.randomUUID(),
      title: String(title).trim(),
      body: String(body).trim(),
      action: action ?? { type: 'open_app' },
      sentAt,
      ttlSeconds: 600,
    };

    await supabase.from('system_settings').upsert({
      key: 'latest_notification',
      value: {
        id: notification.id,
        title: notification.title,
        body: notification.body,
        action: notification.action,
        channelId: channelId ?? 'broadcast',
        sentAt,
      },
      description: 'Latest notification (legacy fallback)',
    }, { onConflict: 'key' });

    await supabase.from('app_notifications').insert({
      id: notification.id,
      title: notification.title,
      body: notification.body,
      action: notification.action,
    });

    await supabase.channel(String(channelId ?? 'broadcast')).send({
      type: 'broadcast',
      event: 'app_notification',
      payload: {
        id: notification.id,
        title: notification.title,
        body: notification.body,
        action: notification.action,
        channelId: channelId ?? 'broadcast',
        sentAt,
      },
    });

    return new Response(
      JSON.stringify({
        success: true,
        notificationId: notification.id,
        sentAt,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('push-notification error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
