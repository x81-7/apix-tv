// Shared helper to retrieve the GitHub token & repo from the panel-stored
// system_settings.security_config row, falling back to Deno env vars.
//
// The admin panel writes both fields into:
//   system_settings(key='security_config', value={ githubToken, githubRepo, ... })
// so every edge function that needs to push to GitHub can read them from a
// single source of truth without forcing the operator to also set GitHub
// Actions secrets / Supabase env vars manually.

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

export interface GithubCreds {
  token: string;
  repo: string;
}

function cleanRepo(raw: string): string {
  return raw.trim()
    .replace(/^https?:\/\/github\.com\//, '')
    .replace(/\.git$/, '')
    .replace(/\/$/, '');
}

/**
 * Reads { githubToken, githubRepo } from `system_settings.security_config`.
 * Throws if neither the row nor the env fallbacks have usable values.
 *
 * @param overrideToken Optional value coming from the request body — wins.
 * @param overrideRepo  Optional value coming from the request body — wins.
 */
export async function getGithubCreds(
  overrideToken?: string,
  overrideRepo?: string,
): Promise<GithubCreds> {
  let token = (overrideToken ?? '').trim();
  let repo = (overrideRepo ?? '').trim();

  if (!token || !repo) {
    try {
      const res = await fetch(
        `${SUPABASE_URL}/rest/v1/system_settings?key=eq.security_config&select=value`,
        {
          headers: {
            apikey: SERVICE_KEY,
            Authorization: `Bearer ${SERVICE_KEY}`,
          },
        },
      );
      if (res.ok) {
        const rows = await res.json();
        const v = (rows?.[0]?.value ?? {}) as { githubToken?: string; githubRepo?: string };
        if (!token && typeof v.githubToken === 'string') token = v.githubToken.trim();
        if (!repo && typeof v.githubRepo === 'string') repo = v.githubRepo.trim();
      }
    } catch (_err) {
      // fall through to env fallbacks below
    }
  }

  if (!token) token = (Deno.env.get('GITHUB_TOKEN') ?? '').trim();
  if (!repo) repo = (Deno.env.get('GITHUB_REPO') ?? '').trim();

  if (!token) {
    throw new Error('GitHub Token مفقود — أضِفه من قسم الحماية في البانل');
  }
  if (!repo) {
    throw new Error('GitHub Repo مفقود — أضِفه من قسم الحماية في البانل (owner/repo)');
  }
  return { token, repo: cleanRepo(repo) };
}
