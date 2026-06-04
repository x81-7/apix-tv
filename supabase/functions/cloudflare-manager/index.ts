// Edge Function: cloudflare-manager
// The panel's "Cloudflare Manager" backend. Drives the Cloudflare REST API so
// the operator can deploy/update the edge Worker, inject hidden secrets, and
// purge cache — without ever putting secrets in plaintext source.
//
// Actions (POST body { action, ... }):
//   - status        : verify token + return account subdomain & worker url
//   - deploy        : upload/publish the Worker script + inject secret bindings
//                     (SUPA_URL, SUPA_ANON, ENC_KEY) + enable workers.dev subdomain
//   - update-secrets: (re)inject secrets into an existing Worker
//   - purge-cache   : purge a zone cache (requires zoneId) or APK edge cache
//
// Credentials (accountId, apiToken) are supplied by the panel per-request over
// HTTPS and are NEVER stored in a public table.

import { buildWorkerScript } from "../_shared/worker-template.ts";
import { buildCinemaWorkerScript } from "../_shared/cinema-worker-template.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const CF = "https://api.cloudflare.com/client/v4";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function cfFetch(token: string, path: string, init: RequestInit = {}) {
  const res = await fetch(`${CF}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init.headers || {}),
    },
  });
  const text = await res.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (!res.ok || (data && data.success === false)) {
    const msg = data?.errors?.map((e: any) => e.message).join("; ") ||
      data?.raw || `Cloudflare API error ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

async function getSubdomain(token: string, accountId: string): Promise<string | null> {
  try {
    const d = await cfFetch(token, `/accounts/${accountId}/workers/subdomain`);
    return d?.result?.subdomain ?? null;
  } catch { return null; }
}

async function deployWorker(token: string, accountId: string, scriptName: string, secrets: Record<string, string>, scriptOverride?: string) {
  const script = scriptOverride ?? buildWorkerScript();
  const bindings = Object.entries(secrets)
    .filter(([, v]) => typeof v === "string" && v.length > 0)
    .map(([name, text]) => ({ type: "secret_text", name, text }));

  const metadata = {
    main_module: "worker.js",
    compatibility_date: "2024-11-01",
    bindings,
  };

  const form = new FormData();
  form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
  form.append(
    "worker.js",
    new Blob([script], { type: "application/javascript+module" }),
    "worker.js",
  );

  await cfFetch(token, `/accounts/${accountId}/workers/scripts/${scriptName}`, {
    method: "PUT",
    body: form,
  });

  // Enable the workers.dev subdomain so the app can reach it
  await cfFetch(token, `/accounts/${accountId}/workers/scripts/${scriptName}/subdomain`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: true }),
  }).catch(() => { /* may already be enabled */ });

  const sub = await getSubdomain(token, accountId);
  const workerUrl = sub ? `https://${scriptName}.${sub}.workers.dev` : null;
  return { workerUrl, secretsInjected: bindings.map((b) => b.name) };
}

async function updateSecrets(token: string, accountId: string, scriptName: string, secrets: Record<string, string>) {
  const names: string[] = [];
  for (const [name, text] of Object.entries(secrets)) {
    if (typeof text !== "string" || !text) continue;
    await cfFetch(token, `/accounts/${accountId}/workers/scripts/${scriptName}/secrets`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, text, type: "secret_text" }),
    });
    names.push(name);
  }
  return { secretsInjected: names };
}

async function purgeCache(token: string, zoneId: string) {
  await cfFetch(token, `/zones/${zoneId}/purge_cache`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ purge_everything: true }),
  });
  return { purged: true };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const body = await req.json().catch(() => ({}));
    const { action, accountId, apiToken, scriptName, zoneId, secrets } = body ?? {};

    if (!apiToken || typeof apiToken !== "string") {
      return json({ success: false, error: "apiToken مطلوب" }, 400);
    }

    if (action === "status") {
      // verify token works + return subdomain
      const verify = await cfFetch(apiToken, `/user/tokens/verify`).catch((e) => ({ error: e.message }));
      if (!accountId) return json({ success: true, tokenValid: !!(verify && !verify.error) });
      const sub = await getSubdomain(apiToken, accountId);
      return json({ success: true, tokenValid: !!(verify && !verify.error), subdomain: sub });
    }

    if (!accountId) return json({ success: false, error: "accountId مطلوب" }, 400);
    const name = (scriptName && String(scriptName).trim()) || "apix-gateway";

    // Secrets that get injected into the Worker (hidden, never in source)
    const injected: Record<string, string> = {
      SUPA_URL: secrets?.SUPA_URL ?? "",
      SUPA_ANON: secrets?.SUPA_ANON ?? "",
      ENC_KEY: secrets?.ENC_KEY ?? "",
      ...(secrets?.BAN_ENDPOINT ? { BAN_ENDPOINT: secrets.BAN_ENDPOINT } : {}),
    };

    if (action === "deploy") {
      const result = await deployWorker(apiToken, accountId, name, injected);
      return json({ success: true, ...result });
    }
    if (action === "update-secrets") {
      const result = await updateSecrets(apiToken, accountId, name, injected);
      return json({ success: true, ...result });
    }
    if (action === "purge-cache") {
      if (!zoneId) return json({ success: false, error: "zoneId مطلوب لمسح الكاش" }, 400);
      const result = await purgeCache(apiToken, zoneId);
      return json({ success: true, ...result });
    }

    return json({ success: false, error: "إجراء غير معروف" }, 400);
  } catch (e: any) {
    console.error("cloudflare-manager error", e);
    return json({ success: false, error: e?.message ?? "Unknown error" }, 500);
  }
});
