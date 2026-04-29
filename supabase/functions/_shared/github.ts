// GitHub Contents API helpers
const GH_API = 'https://api.github.com';

async function getSha(token: string, repo: string, path: string): Promise<string | undefined> {
  const res = await fetch(`${GH_API}/repos/${repo}/contents/${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
    },
  });
  if (!res.ok) {
    await res.text();
    return undefined;
  }
  const j = await res.json();
  return j.sha as string | undefined;
}

export async function uploadToGithub(
  token: string,
  repo: string,
  path: string,
  content: string,
  message: string
): Promise<{ commitSha?: string; htmlUrl?: string; rawUrl: string }> {
  const sha = await getSha(token, repo, path);
  const contentB64 = btoa(unescape(encodeURIComponent(content)));
  const res = await fetch(`${GH_API}/repos/${repo}/contents/${path}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, content: contentB64, sha }),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`GitHub upload failed [${res.status}]: ${JSON.stringify(data)}`);
  }
  return {
    commitSha: data.commit?.sha,
    htmlUrl: data.content?.html_url,
    rawUrl: `https://raw.githubusercontent.com/${repo}/main/${path}`,
  };
}

export async function downloadFromGithubRaw(
  repo: string,
  path: string,
  branch = 'main'
): Promise<string | null> {
  // Try unauthenticated raw first (works for public repos and is fast)
  const url = `https://raw.githubusercontent.com/${repo}/${branch}/${path}?t=${Date.now()}`;
  const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache' } });
  if (res.ok) return await res.text();
  await res.text();
  return null;
}

// Authenticated read via Contents API — works for private repos.
export async function downloadFromGithubAuth(
  token: string,
  repo: string,
  path: string,
  branch = 'main'
): Promise<string | null> {
  const res = await fetch(`${GH_API}/repos/${repo}/contents/${path}?ref=${branch}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github.raw',
      'Cache-Control': 'no-cache',
    },
  });
  if (!res.ok) {
    const body = await res.text();
    console.error(`[github] auth read failed ${res.status} ${path}: ${body.slice(0, 200)}`);
    return null;
  }
  return await res.text();
}

// Convenience: try raw, then authenticated.
export async function readGithubFile(
  token: string,
  repo: string,
  path: string,
  branch = 'main'
): Promise<string | null> {
  const raw = await downloadFromGithubRaw(repo, path, branch);
  if (raw != null) return raw;
  return await downloadFromGithubAuth(token, repo, path, branch);
}

/**
 * Upload binary content (as raw bytes) to GitHub. Used for PNGs/icons that
 * must NOT go through the unicode-encoding step in `uploadToGithub`.
 */
export async function uploadBinaryToGithub(
  token: string,
  repo: string,
  path: string,
  bytes: Uint8Array,
  message: string,
): Promise<{ commitSha?: string; rawUrl: string }> {
  const sha = await getSha(token, repo, path);
  // Convert bytes → base64 in chunks (avoid call stack overflow on big files)
  let binary = '';
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + CHUNK)) as number[]);
  }
  const contentB64 = btoa(binary);
  const res = await fetch(`${GH_API}/repos/${repo}/contents/${path}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, content: contentB64, sha }),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`GitHub binary upload failed [${res.status}]: ${JSON.stringify(data)}`);
  }
  return {
    commitSha: data.commit?.sha,
    rawUrl: `https://raw.githubusercontent.com/${repo}/main/${path}`,
  };
}
