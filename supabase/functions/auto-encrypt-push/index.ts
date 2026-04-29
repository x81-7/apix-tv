import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0';
import {
  aesEncrypt,
  b64decode,
  b64encode,
  buildAppDataTree,
  combineKeys,
  deriveMasterKey,
} from '../_shared/crypto.ts';
import { uploadToGithub } from '../_shared/github.ts';

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
    const MASTER = Deno.env.get('MASTER_ENCRYPTION_KEY');
    const SALT = Deno.env.get('INTERNAL_KEY_SALT');
    const GH_TOKEN = Deno.env.get('GITHUB_TOKEN');
    const GH_REPO = Deno.env.get('GITHUB_REPO');
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
    const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    if (!MASTER) throw new Error('MASTER_ENCRYPTION_KEY missing');
    if (!SALT) throw new Error('INTERNAL_KEY_SALT missing');
    if (!GH_TOKEN) throw new Error('GITHUB_TOKEN missing');
    if (!GH_REPO) throw new Error('GITHUB_REPO missing');

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);

    // 1) Read all data
    const [cats, chans, menus, subs, sets] = await Promise.all([
      supabase.from('categories').select('*').order('sort_order'),
      supabase.from('channels').select('*').order('sort_order'),
      supabase.from('side_menus').select('*').order('sort_order'),
      supabase.from('sub_channels').select('*').order('sort_order'),
      supabase.from('system_settings').select('*'),
    ]);
    const errs = [cats.error, chans.error, menus.error, subs.error, sets.error].filter(Boolean);
    if (errs.length) throw new Error('DB read failed: ' + errs.map((e) => e!.message).join(', '));

    // 2) Build the app data tree
    const tree = buildAppDataTree({
      categories: cats.data ?? [],
      channels: chans.data ?? [],
      sideMenus: menus.data ?? [],
      subChannels: subs.data ?? [],
      systemSettings: sets.data ?? [],
    });

    // 3) Get the active hybrid key parts.
    //    internal_key_b64 + external_key_b64 + INTERNAL_KEY_SALT  →  SHA-256  →  AES key.
    const { data: keyRow, error: keyErr } = await supabase
      .from('encryption_keys')
      .select('*')
      .eq('is_active', true)
      .order('key_version', { ascending: false })
      .limit(1)
      .maybeSingle();

    let internalB64: string;
    let externalB64: string;
    let keyVersion: number;

    if (keyErr || !keyRow) {
      // Bootstrap first key pair if none exists yet
      internalB64 = b64encode(crypto.getRandomValues(new Uint8Array(32)));
      externalB64 = b64encode(crypto.getRandomValues(new Uint8Array(32)));
      keyVersion = 1;

      const masterKey = await deriveMasterKey(MASTER);
      const wrappedExternal = await aesEncrypt(masterKey, externalB64);

      await supabase.from('encryption_keys').insert({
        key_version: keyVersion,
        encrypted_key: JSON.stringify({
          internal_key_b64: internalB64,
          external_key_wrapped: wrappedExternal,
        }),
        algorithm: 'AES-256-GCM-HYBRID',
        is_active: true,
        activated_at: new Date().toISOString(),
      });
    } else {
      const parsed = JSON.parse(keyRow.encrypted_key);
      internalB64 = parsed.internal_key_b64;
      const masterKey = await deriveMasterKey(MASTER);
      // Unwrap external key
      const { aesDecrypt } = await import('../_shared/crypto.ts');
      externalB64 = await aesDecrypt(
        masterKey,
        parsed.external_key_wrapped.iv,
        parsed.external_key_wrapped.data
      );
      keyVersion = keyRow.key_version;
    }

    // 4) Combine keys → final AES key → encrypt the JSON
    const finalKey = await combineKeys(internalB64, externalB64, SALT);
    const encrypted = await aesEncrypt(finalKey, JSON.stringify(tree));

    const fileObj = {
      version: keyVersion,
      algorithm: 'AES-256-GCM',
      derivation: 'SHA-256(internal||external||salt)',
      payload: encrypted,
      generatedAt: tree.generatedAt,
    };
    const fileText = JSON.stringify(fileObj, null, 2);

    // 5) Push to GitHub
    const upload = await uploadToGithub(
      GH_TOKEN,
      GH_REPO,
      'encrypted_data.json',
      fileText,
      `data: auto-update v${keyVersion} @ ${tree.generatedAt}`
    );

    // 6) Backup log
    await supabase.from('backup_history').insert({
      source: 'auto-encrypt-push',
      size_bytes: fileText.length,
      storage_path: `github://${GH_REPO}/encrypted_data.json`,
      notes: `key_version=${keyVersion}`,
    });

    return new Response(
      JSON.stringify({
        success: true,
        keyVersion,
        size: fileText.length,
        rawUrl: upload.rawUrl,
        commit: upload.commitSha,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('auto-encrypt-push error:', message);
    return new Response(JSON.stringify({ success: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
