// Edge Function: trigger-android-build
// Dispatches the build_apk.yml workflow via GitHub API (workflow_dispatch).
// Reads token+repo from system_settings.security_config first (same as the
// rest of the GitHub-touching functions), falls back to env.

import { getGithubCreds } from "../_shared/github-creds.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const body = await req.json().catch(() => ({}));
    const { workflow = 'build_apk.yml', githubToken: bToken, githubRepo: bRepo } = body ?? {};
    const { token, repo } = await getGithubCreds(bToken, bRepo);

    const res = await fetch(
      `https://api.github.com/repos/${repo}/actions/workflows/${workflow}/dispatches`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/vnd.github+json',
          'Content-Type': 'application/json',
          'User-Agent': 'lovable-trigger-build',
        },
        body: JSON.stringify({ ref: 'main' }),
      },
    );
    if (!res.ok && res.status !== 204) {
      const txt = await res.text();
      throw new Error(`GitHub dispatch failed [${res.status}]: ${txt}`);
    }
    return new Response(JSON.stringify({ ok: true }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e: any) {
    return new Response(JSON.stringify({ error: e.message ?? 'Unknown error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
