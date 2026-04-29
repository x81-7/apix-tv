// Wrapper that routes admin writes through the admin-write edge function.
// The function uses the service role and triggers auto-encrypt-push afterwards.
import { supabase } from '@/integrations/supabase/client';

type Op = 'insert' | 'update' | 'delete' | 'upsert';

async function call(table: string, op: Op, payload: {
  values?: unknown;
  match?: Record<string, unknown>;
  returning?: boolean;
  skipReencrypt?: boolean;
}) {
  const { data, error } = await supabase.functions.invoke('admin-write', {
    body: { table, op, ...payload },
  });
  if (error) throw error;
  if (!data?.success) throw new Error(data?.error || 'admin-write failed');
  return data.result;
}

export const adminDb = {
  insert: (table: string, values: unknown, returning = true, skipReencrypt = false) =>
    call(table, 'insert', { values, returning, skipReencrypt }),
  update: (table: string, match: Record<string, unknown>, values: unknown, skipReencrypt = false) =>
    call(table, 'update', { values, match, skipReencrypt }),
  upsert: (table: string, values: unknown, skipReencrypt = false) =>
    call(table, 'upsert', { values, skipReencrypt }),
  delete: (table: string, match: Record<string, unknown>, skipReencrypt = false) =>
    call(table, 'delete', { match, skipReencrypt }),

  // Push notification (writes notifications.json on GitHub)
  pushNotification: async (title: string, body: string, action?: unknown, channelId?: string) => {
    const { data, error } = await supabase.functions.invoke('push-notification', {
      body: { title, body, action, channelId },
    });
    if (error) throw error;
    if (!data?.success) throw new Error(data?.error || 'push failed');
    return data;
  },

  // Manual triggers
  rotateKey: async () => {
    const { data, error } = await supabase.functions.invoke('rotate-external-key', { body: {} });
    if (error) throw error;
    if (!data?.success) throw new Error(data?.error || 'rotate failed');
    return data;
  },
  forceReencrypt: async () => {
    const { data, error } = await supabase.functions.invoke('auto-encrypt-push', { body: {} });
    if (error) throw error;
    if (!data?.success) throw new Error(data?.error || 'encrypt failed');
    return data;
  },
};
