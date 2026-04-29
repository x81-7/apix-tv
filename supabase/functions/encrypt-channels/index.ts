import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers':
    'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

// ===== Crypto helpers (AES-256-GCM) =====
const enc = new TextEncoder();
const dec = new TextDecoder();

const toArrayBuffer = (view: Uint8Array) => {
  const { buffer, byteOffset, byteLength } = view;
  if (buffer instanceof ArrayBuffer && byteOffset === 0 && byteLength === buffer.byteLength) {
    return buffer;
  }
  return view.slice().buffer;
};

const b64encode = (buf: ArrayBuffer | Uint8Array) => {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
};

const b64decode = (s: string) => {
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
};

async function importAesKey(rawKey: Uint8Array) {
  return await crypto.subtle.importKey(
    'raw',
    toArrayBuffer(rawKey),
    { name: 'AES-GCM' },
    false,
    ['encrypt', 'decrypt']
  );
}

async function aesEncrypt(rawKey: Uint8Array, plaintext: string) {
  const key = await importAesKey(rawKey);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    toArrayBuffer(enc.encode(plaintext))
  );
  return { iv: b64encode(iv), data: b64encode(ct) };
}

async function deriveMasterKey(masterSecret: string): Promise<Uint8Array> {
  // Derive a deterministic 32-byte key from the master secret string
  const hash = await crypto.subtle.digest('SHA-256', toArrayBuffer(enc.encode(masterSecret)));
  return new Uint8Array(hash);
}

// ===== GitHub upload =====
async function uploadToGithub(
  token: string,
  repo: string,
  path: string,
  content: string,
  message: string
) {
  const apiUrl = `https://api.github.com/repos/${repo}/contents/${path}`;
  // Get current SHA if file exists
  let sha: string | undefined;
  const head = await fetch(apiUrl, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
    },
  });
  if (head.ok) {
    const j = await head.json();
    sha = j.sha;
  }

  const contentB64 = btoa(unescape(encodeURIComponent(content)));
  const res = await fetch(apiUrl, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, content: contentB64, sha }),
  });
  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`GitHub upload failed [${res.status}]: ${errText}`);
  }
  return await res.json();
}

// ===== Main handler =====
Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const MASTER = Deno.env.get('MASTER_ENCRYPTION_KEY');
    const GH_TOKEN = Deno.env.get('GITHUB_TOKEN');
    const GH_REPO = Deno.env.get('GITHUB_REPO');
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    if (!MASTER) throw new Error('MASTER_ENCRYPTION_KEY not configured');
    if (!GH_TOKEN) throw new Error('GITHUB_TOKEN not configured');
    if (!GH_REPO) throw new Error('GITHUB_REPO not configured');

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    // 1) Fetch all content tables
    const [cats, chans, menus, subs, sets] = await Promise.all([
      supabase.from('categories').select('*').order('sort_order'),
      supabase.from('channels').select('*').order('sort_order'),
      supabase.from('side_menus').select('*').order('sort_order'),
      supabase.from('sub_channels').select('*').order('sort_order'),
      supabase.from('system_settings').select('*'),
    ]);
    const errs = [cats.error, chans.error, menus.error, subs.error, sets.error].filter(Boolean);
    if (errs.length) throw new Error('DB read failed: ' + errs.map((e) => e!.message).join(', '));

    const payload = {
      categories: cats.data ?? [],
      channels: chans.data ?? [],
      sideMenus: menus.data ?? [],
      subChannels: subs.data ?? [],
      systemSettings: sets.data ?? [],
      generatedAt: new Date().toISOString(),
    };

    // 2) Generate a fresh per-build data key (32 bytes)
    const dataKey = crypto.getRandomValues(new Uint8Array(32));

    // 3) Encrypt payload with data key
    const encryptedPayload = await aesEncrypt(dataKey, JSON.stringify(payload));

    // 4) Wrap data key with master key
    const masterKey = await deriveMasterKey(MASTER);
    const wrappedKey = await aesEncrypt(masterKey, b64encode(dataKey));

    // 5) Build the public file
    const fileObj = {
      version: 1,
      algorithm: 'AES-256-GCM',
      payload: encryptedPayload, // {iv, data}
      generatedAt: new Date().toISOString(),
    };
    const fileText = JSON.stringify(fileObj, null, 2);

    // 6) Persist new key version (active=true, deactivate previous)
    const { data: lastVer } = await supabase
      .from('encryption_keys')
      .select('key_version')
      .order('key_version', { ascending: false })
      .limit(1)
      .maybeSingle();
    const nextVersion = (lastVer?.key_version ?? 0) + 1;

    await supabase
      .from('encryption_keys')
      .update({ is_active: false, rotated_at: new Date().toISOString() })
      .eq('is_active', true);

    await supabase.from('encryption_keys').insert({
      key_version: nextVersion,
      encrypted_key: JSON.stringify(wrappedKey),
      algorithm: 'AES-256-GCM',
      is_active: true,
      activated_at: new Date().toISOString(),
    });

    // 7) Upload to GitHub
    const upload = await uploadToGithub(
      GH_TOKEN,
      GH_REPO,
      'encrypted_data.json',
      fileText,
      `chore: encrypted data v${nextVersion} @ ${new Date().toISOString()}`
    );

    // 8) Log backup
    await supabase.from('backup_history').insert({
      source: 'encrypt-channels',
      size_bytes: fileText.length,
      storage_path: `github://${GH_REPO}/encrypted_data.json`,
      notes: `key_version=${nextVersion}`,
    });

    return new Response(
      JSON.stringify({
        success: true,
        keyVersion: nextVersion,
        size: fileText.length,
        github: {
          repo: GH_REPO,
          path: 'encrypted_data.json',
          commit: upload.commit?.sha,
          url: upload.content?.html_url,
        },
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('encrypt-channels error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 500,
    });
  }
});
