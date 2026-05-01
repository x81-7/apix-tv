import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

/**
 * Generic CRUD endpoint for the admin panel.
 * The dashboard authenticates via the admin-auth flow, so direct DB writes are blocked
 * by RLS. This function performs writes with the service role and then triggers
 * an automatic re-encrypt + push of the JSON file to GitHub.
 *
 * Body shape:
 *   { table: string, op: 'insert'|'update'|'delete'|'upsert',
 *     values?: object|object[], match?: object, returning?: boolean,
 *     skipReencrypt?: boolean }
 */
const ALLOWED_TABLES = new Set([
  'categories',
  'channels',
  'side_menus',
  'sub_channels',
  'system_settings',
  'custom_ads',
  'vip_subscriptions',
]);

/** Encrypt and upload a secret to GitHub repository */
async function syncGitHubSecret(repo: string, token: string, secretName: string, secretValue: string) {
  // Validate repo format: owner/name
  const cleanRepo = repo.trim().replace(/^https?:\/\/github\.com\//, '').replace(/\.git$/, '').replace(/\/$/, '');
  if (!/^[^/\s]+\/[^/\s]+$/.test(cleanRepo)) {
    throw new Error(`Invalid GITHUB_REPO format "${repo}". Expected "owner/name" (e.g. "user/my-repo").`);
  }

  // 1. Get repo public key
  const pkRes = await fetch(`https://api.github.com/repos/${cleanRepo}/actions/secrets/public-key`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'User-Agent': 'lovable-admin-write' },
  });
  if (!pkRes.ok) {
    const txt = await pkRes.text();
    throw new Error(`GitHub public-key fetch failed (${pkRes.status}) for "${cleanRepo}". Check token scopes (repo) and repo name. Response: ${txt}`);
  }
  const { key, key_id } = await pkRes.json();

  // 2. Encrypt using libsodium sealed box
  let encryptedB64: string;
  try {
    const sealedMod: any = await import('https://esm.sh/tweetnacl-sealedbox-js@1.2.0');
    const naclUtil: any = (await import('https://esm.sh/tweetnacl-util@0.15.1')).default;
    const seal = sealedMod.seal || sealedMod.default?.seal || sealedMod.default;
    if (typeof seal !== 'function') throw new Error('sealed-box seal() not found in module');

    const pubKeyBytes = naclUtil.decodeBase64(key);
    const msgBytes = naclUtil.decodeUTF8(secretValue);
    const encrypted = seal(msgBytes, pubKeyBytes);
    encryptedB64 = naclUtil.encodeBase64(encrypted);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(`Sealed-box encryption failed: ${msg}`);
  }

  // 3. PUT secret
  const putRes = await fetch(`https://api.github.com/repos/${cleanRepo}/actions/secrets/${secretName}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json', 'User-Agent': 'lovable-admin-write' },
    body: JSON.stringify({ encrypted_value: encryptedB64, key_id }),
  });
  if (!putRes.ok && putRes.status !== 204) {
    const txt = await putRes.text();
    throw new Error(`GitHub PUT secret "${secretName}" failed (${putRes.status}): ${txt}`);
  }
  return { success: true, repo: cleanRepo, secretName };
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    const body = await req.json();
    const { table, op, values, match, returning, skipReencrypt } = body;

    // Handle GitHub secret sync
    if (table === '__github_sync' && op === 'github_secret') {
      const { githubRepo, githubToken, secretName, secretValue } = body;
      const missing: string[] = [];
      if (!githubRepo) missing.push('githubRepo');
      if (!githubToken) missing.push('githubToken');
      if (!secretName) missing.push('secretName');
      if (secretValue === undefined || secretValue === null || secretValue === '') missing.push('secretValue');
      if (missing.length) throw new Error(`Missing github sync params: ${missing.join(', ')}`);
      const result = await syncGitHubSecret(githubRepo, githubToken, secretName, String(secretValue));
      return new Response(JSON.stringify({ success: true, result }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (!table || !ALLOWED_TABLES.has(table)) throw new Error('invalid table');
    if (!op) throw new Error('op required');

    // Coerce empty strings to null for uuid/foreign-key columns to avoid
    // Postgres "invalid input syntax for type uuid" errors.
    const UUID_FIELDS = new Set([
      'id', 'category_id', 'side_menu_id', 'parent_id',
    ]);
    const sanitize = (v: unknown): unknown => {
      if (Array.isArray(v)) return v.map(sanitize);
      if (v && typeof v === 'object') {
        const out: Record<string, unknown> = {};
        for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
          if (UUID_FIELDS.has(k) && val === '') out[k] = null;
          else out[k] = val;
        }
        return out;
      }
      return v;
    };
    const cleanValues = sanitize(values);

    let result: unknown = null;

    if (op === 'insert') {
      const q = supabase.from(table).insert(cleanValues as never);
      const { data, error } = returning ? await q.select() : await q;
      if (error) throw error;
      result = data;
    } else if (op === 'upsert') {
      const upsertOpts = table === 'system_settings' ? { onConflict: 'key' } : undefined;
      const q = supabase.from(table).upsert(cleanValues as never, upsertOpts as never);
      const { data, error } = returning ? await q.select() : await q;
      if (error) throw error;
      result = data;
    } else if (op === 'update') {
      if (!match || typeof match !== 'object') throw new Error('match required');
      let q = supabase.from(table).update(cleanValues as never);
      // Support `{ field: { in: [...] } }` for bulk updates
      for (const [k, v] of Object.entries(match)) {
        if (v && typeof v === 'object' && Array.isArray((v as { in?: unknown[] }).in)) {
          q = q.in(k, (v as { in: unknown[] }).in);
        } else {
          q = q.eq(k, v);
        }
      }
      const { data, error } = returning ? await q.select() : await q;
      if (error) throw error;
      result = data;
    } else if (op === 'delete') {
      if (!match || typeof match !== 'object') throw new Error('match required');
      let q = supabase.from(table).delete();
      for (const [k, v] of Object.entries(match)) q = q.eq(k, v);
      const { error } = await q;
      if (error) throw error;
      result = { deleted: true };
    } else {
      throw new Error('unknown op');
    }

    // Drop the cached-data warm cache so apps see fresh data on next poll.
    try {
      await fetch(`${SUPABASE_URL}/functions/v1/cached-data/invalidate`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${SERVICE_ROLE}`, 'Content-Type': 'application/json' },
        body: '{}',
      });
    } catch (e) {
      console.warn('cached-data invalidate failed', e);
    }

    // Fire-and-forget re-encrypt
    let reencrypt: unknown = null;
    if (!skipReencrypt) {
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
    }

    return new Response(JSON.stringify({ success: true, result, reencrypt }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : (typeof err === 'string' ? err : JSON.stringify(err));
    const stack = err instanceof Error ? err.stack : undefined;
    console.error('admin-write error:', message, stack);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
