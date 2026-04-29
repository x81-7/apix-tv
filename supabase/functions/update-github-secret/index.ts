// Edge Function: update-github-secret
// Saves AdMob App ID (or other allowed names) as a GitHub repository secret using the
// existing GITHUB_TOKEN (PAT with `repo` + `admin:repo_hook`/`secrets` scopes).
// Requires libsodium for sealed-box encryption of the secret value.

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const ALLOWED_NAMES = new Set([
  'ADMOB_APP_ID',
  'EXTERNAL_PANEL_DECRYPTION_KEY',
]);

interface PublicKeyResp { key: string; key_id: string }

function cleanRepo(raw: string): string {
  return raw.trim()
    .replace(/^https?:\/\/github\.com\//, '')
    .replace(/\.git$/, '')
    .replace(/\/$/, '');
}

async function getRepoPublicKey(token: string, repo: string): Promise<PublicKeyResp> {
  const res = await fetch(`https://api.github.com/repos/${repo}/actions/secrets/public-key`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'lovable-update-secret',
    },
  });
  if (!res.ok) throw new Error(`Failed to get public key: ${res.status} ${await res.text()}`);
  return await res.json();
}

async function putSecret(token: string, repo: string, name: string, encryptedValue: string, keyId: string) {
  const res = await fetch(`https://api.github.com/repos/${repo}/actions/secrets/${name}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
      'User-Agent': 'lovable-update-secret',
    },
    body: JSON.stringify({ encrypted_value: encryptedValue, key_id: keyId }),
  });
  if (!res.ok && res.status !== 201 && res.status !== 204) {
    throw new Error(`Failed to set secret: ${res.status} ${await res.text()}`);
  }
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const body = await req.json();
    const { name, value, githubToken: bodyToken, githubRepo: bodyRepo } = body ?? {};
    if (typeof name !== 'string' || typeof value !== 'string' || !name || !value) {
      return new Response(JSON.stringify({ error: 'name and value required' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
    if (!ALLOWED_NAMES.has(name)) {
      return new Response(JSON.stringify({ error: `Secret "${name}" is not allowed` }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Prefer values supplied by the panel (stored in system_settings → security_config),
    // fall back to env vars for backwards compatibility.
    const token = (typeof bodyToken === 'string' && bodyToken.trim())
      ? bodyToken.trim()
      : Deno.env.get('GITHUB_TOKEN');
    const repoRaw = (typeof bodyRepo === 'string' && bodyRepo.trim())
      ? bodyRepo.trim()
      : Deno.env.get('GITHUB_REPO');
    if (!token) throw new Error('GITHUB_TOKEN missing — أدخله في قسم الحماية بالبانل');
    if (!repoRaw) throw new Error('GITHUB_REPO missing — أدخله في قسم الحماية بالبانل');
    const repo = cleanRepo(repoRaw);
    if (!/^[^/\s]+\/[^/\s]+$/.test(repo)) {
      throw new Error(`Invalid GITHUB_REPO "${repoRaw}". Expected "owner/name".`);
    }

    const { key: publicKey, key_id } = await getRepoPublicKey(token, repo);

    // Use tweetnacl sealed-box (same approach as admin-write — proven to work in Deno edge)
    const sealedMod: any = await import('https://esm.sh/tweetnacl-sealedbox-js@1.2.0');
    const naclUtil: any = (await import('https://esm.sh/tweetnacl-util@0.15.1')).default;
    const seal = sealedMod.seal || sealedMod.default?.seal || sealedMod.default;
    if (typeof seal !== 'function') throw new Error('sealed-box seal() not found');
    const pubKeyBytes = naclUtil.decodeBase64(publicKey);
    const msgBytes = naclUtil.decodeUTF8(value);
    const encrypted = naclUtil.encodeBase64(seal(msgBytes, pubKeyBytes));

    await putSecret(token, repo, name, encrypted, key_id);

    return new Response(JSON.stringify({ ok: true, name }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e: any) {
    console.error('update-github-secret error', e);
    return new Response(JSON.stringify({ error: e.message ?? 'Unknown error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
