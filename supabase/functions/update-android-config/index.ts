// Edge Function: update-android-config
// Edits multiple Android source files in the GitHub repo so that AdMob App ID,
// app name, package name, and version are baked directly into the next build —
// no GitHub Secrets, no manifest placeholders, no chance of error at runtime.
//
// Allowed fields:
//   - admobAppId   → injected into AndroidManifest.xml meta-data
//   - appName      → strings.xml app_name
//   - packageName  → build.gradle namespace + applicationId, AndroidManifest package
//   - versionName  → build.gradle versionName
//   - versionCode  → build.gradle versionCode (integer, optional)

import { uploadToGithub, readGithubFile } from "../_shared/github.ts";
import { getGithubCreds } from "../_shared/github-creds.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

async function patchFile(
  token: string, repo: string, path: string,
  transform: (orig: string) => string,
  message: string,
): Promise<{ changed: boolean; commitSha?: string }> {
  const orig = await readGithubFile(token, repo, path);
  if (orig == null) throw new Error(`Cannot read ${path} from GitHub (check GITHUB_TOKEN scope and GITHUB_REPO=owner/repo)`);
  const next = transform(orig);
  if (next === orig) return { changed: false };
  const res = await uploadToGithub(token, repo, path, next, message);
  return { changed: true, commitSha: res.commitSha };
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  try {
    const body = await req.json();
    const {
      admobAppId, appName, packageName, versionName, versionCode,
      githubToken: bodyToken, githubRepo: bodyRepo,
    } = body ?? {};

    // Reads token+repo from panel-stored security_config first, then env, then body overrides.
    const { token, repo } = await getGithubCreds(bodyToken, bodyRepo);


    const results: Record<string, any> = {};

    // 1) AdMob App ID → AndroidManifest.xml meta-data literal
    if (typeof admobAppId === 'string' && admobAppId.trim()) {
      const id = admobAppId.trim();
      if (!/^ca-app-pub-\d{16}~\d{10}$/.test(id)) throw new Error('admobAppId format invalid');
      results.manifest = await patchFile(token, repo, 'android/app/src/main/AndroidManifest.xml', (s) => {
        return s.replace(
          /(<meta-data[^>]*android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"[^>]*android:value=")([^"]*)(")/,
          `$1${id}$3`,
        );
      }, `chore: update AdMob App ID`);
    }

    // 2) App name
    if (typeof appName === 'string' && appName.trim()) {
      const name = appName.trim().replace(/[<>&]/g, '');
      results.strings = await patchFile(token, repo, 'android/app/src/main/res/values/strings.xml', (s) => {
        return s.replace(
          /(<string\s+name="app_name">)([^<]*)(<\/string>)/,
          `$1${name}$3`,
        );
      }, `chore: update app_name`);
    }

    // 3) Package name (applicationId + namespace)
    if (typeof packageName === 'string' && packageName.trim()) {
      const pkg = packageName.trim();
      if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(pkg)) throw new Error('packageName invalid');
      results.gradlePkg = await patchFile(token, repo, 'android/app/build.gradle', (s) => {
        return s
          .replace(/(namespace\s+['"])([^'"]+)(['"])/, `$1${pkg}$3`)
          .replace(/(applicationId\s+["'])([^"']+)(["'])/, `$1${pkg}$3`);
      }, `chore: update package name to ${pkg}`);
    }

    // 4) Version name + code
    if (typeof versionName === 'string' && versionName.trim()) {
      const ver = versionName.trim();
      if (!/^\d+(\.\d+){0,3}$/.test(ver)) throw new Error('versionName must be e.g. 1.2 or 2.5.0');
      results.gradleVer = await patchFile(token, repo, 'android/app/build.gradle', (s) => {
        return s.replace(/(versionName\s+["'])([^"']+)(["'])/, `$1${ver}$3`);
      }, `chore: bump versionName to ${ver}`);
    }
    if (typeof versionCode === 'number' && Number.isInteger(versionCode) && versionCode > 0) {
      results.gradleCode = await patchFile(token, repo, 'android/app/build.gradle', (s) => {
        return s.replace(/(versionCode\s+)\d+/, `$1${versionCode}`);
      }, `chore: bump versionCode to ${versionCode}`);
    }

    return new Response(JSON.stringify({ ok: true, results }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e: any) {
    console.error('update-android-config error', e);
    return new Response(JSON.stringify({ error: e.message ?? 'Unknown error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
