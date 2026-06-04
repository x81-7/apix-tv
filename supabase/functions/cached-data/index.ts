// Edge Function: cached-data
// Aggregates categories + channels + side_menus + sub_channels + system_settings
// into a single response, AES-256-GCM encrypted with ENCRYPTION_SECRET_KEY
// (32-byte key, hex or base64). Always returns { iv, data } — clients MUST
// decrypt to read channels/DRM keys. ETag still works for 304s.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, if-none-match",
  "Access-Control-Expose-Headers": "etag, x-cache, x-bundle-version",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const SOFT_TTL_MS = 30_000;

interface Bundle {
  categories: any[];
  channels: any[];
  side_menus: any[];
  sub_channels: any[];
  system_settings: any[];
  bundle_version: number;
  generated_at: number;
}

let memCache: { etag: string; encrypted: string; computedAt: number } | null = null;
let cachedKey: CryptoKey | null = null;

// ---------- Key + encryption helpers ----------

function decodeKey(raw: string): Uint8Array {
  const trimmed = raw.trim();
  if (!trimmed) throw new Error("ENCRYPTION_SECRET_KEY is not set");
  // Try hex first (64 chars)
  if (/^[0-9a-fA-F]+$/.test(trimmed) && trimmed.length === 64) {
    const out = new Uint8Array(32);
    for (let i = 0; i < 32; i++) out[i] = parseInt(trimmed.substr(i * 2, 2), 16);
    return out;
  }
  // Otherwise treat as base64
  const bin = atob(trimmed.replace(/-/g, "+").replace(/_/g, "/"));
  if (bin.length !== 32) {
    throw new Error(`ENCRYPTION_SECRET_KEY must decode to 32 bytes (got ${bin.length})`);
  }
  const out = new Uint8Array(32);
  for (let i = 0; i < 32; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function resolveSecret(): Promise<string> {
  // 1) Panel-managed value (system_settings.security_config.cloudDecryptionKey)
  try {
    const sb = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });
    const { data } = await sb
      .from("system_settings")
      .select("value")
      .eq("key", "security_config")
      .maybeSingle();
    const v = (data?.value ?? null) as Record<string, unknown> | null;
    const k = v?.cloudDecryptionKey;
    if (typeof k === "string" && k.trim()) return k.trim();
  } catch (_) { /* fall through */ }
  // 2) Legacy env-var fallback
  return Deno.env.get("ENCRYPTION_SECRET_KEY") ?? "";
}

async function getKey(): Promise<CryptoKey> {
  if (cachedKey) return cachedKey;
  const secret = await resolveSecret();
  if (!secret) throw new Error("No encryption key configured (panel or env)");
  const raw = decodeKey(secret);
  cachedKey = await crypto.subtle.importKey(
    "raw",
    raw,
    { name: "AES-GCM" },
    false,
    ["encrypt"],
  );
  return cachedKey;
}

function b64encode(bytes: Uint8Array): string {
  let s = "";
  for (let i = 0; i < bytes.byteLength; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

async function encryptPayload(plain: string): Promise<{ iv: string; data: string }> {
  const key = await getKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(plain),
  );
  return { iv: b64encode(iv), data: b64encode(new Uint8Array(ct)) };
}

// ---------- Bundle build ----------

async function buildBundle(): Promise<{ etag: string; encrypted: string }> {
  const sb = createClient(SUPABASE_URL, SERVICE_KEY, {
    auth: { persistSession: false },
  });

  const [cats, chans, menus, subs, settings] = await Promise.all([
    sb.from("categories").select("*").order("sort_order", { ascending: true }),
    sb.from("channels").select("*").order("sort_order", { ascending: true }),
    sb.from("side_menus").select("*").order("sort_order", { ascending: true }),
    sb.from("sub_channels").select("*").order("sort_order", { ascending: true }),
    sb.from("system_settings").select("*"),
  ]);

  if (cats.error) throw cats.error;
  if (chans.error) throw chans.error;
  if (menus.error) throw menus.error;
  if (subs.error) throw subs.error;
  if (settings.error) throw settings.error;

  let bundleVersion = 0;
  for (const c of chans.data ?? []) bundleVersion += Number((c as any).cache_version ?? 0);
  for (const s of subs.data ?? []) bundleVersion += Number((s as any).cache_version ?? 0);

  const maxTs = (rows: any[]) =>
    rows.reduce((m, r) => {
      const t = Date.parse(r.updated_at ?? r.created_at ?? "") || 0;
      return t > m ? t : m;
    }, 0);
  const auxTs = Math.max(
    maxTs(cats.data ?? []),
    maxTs(menus.data ?? []),
    maxTs(settings.data ?? []),
  );

  // Content hash of system_settings so toggles like showSettingsSection bust the
  // cache even when no updated_at trigger exists (otherwise edits return 304 and
  // never reach the app). djb2 over the serialized settings rows.
  const settingsHash = (() => {
    const str = JSON.stringify(settings.data ?? []);
    let h = 5381;
    for (let i = 0; i < str.length; i++) h = ((h << 5) + h + str.charCodeAt(i)) >>> 0;
    return h;
  })();

  const bundle: Bundle = {
    categories: cats.data ?? [],
    channels: chans.data ?? [],
    side_menus: menus.data ?? [],
    sub_channels: subs.data ?? [],
    system_settings: settings.data ?? [],
    bundle_version: bundleVersion,
    generated_at: Date.now(),
  };

  const plain = JSON.stringify(bundle);
  const enc = await encryptPayload(plain);
  const etag = `W/"v${bundleVersion}-a${auxTs}-s${settingsHash}-e1"`; // suffix bumped: payload format = encrypted v1
  return { etag, encrypted: JSON.stringify(enc) };
}

// ---------- HTTP handler ----------

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const url = new URL(req.url);

    if (req.method === "POST" && url.pathname.endsWith("/invalidate")) {
      memCache = null;
      cachedKey = null; // also flush key so panel updates take effect immediately
      return new Response(JSON.stringify({ ok: true }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const ifNoneMatch = req.headers.get("if-none-match") ?? "";

    let payload: { etag: string; encrypted: string };
    let cacheStatus: "HIT" | "MISS";

    if (memCache && Date.now() - memCache.computedAt < SOFT_TTL_MS) {
      payload = { etag: memCache.etag, encrypted: memCache.encrypted };
      cacheStatus = "HIT";
    } else {
      const fresh = await buildBundle();
      memCache = {
        etag: fresh.etag,
        encrypted: fresh.encrypted,
        computedAt: Date.now(),
      };
      payload = fresh;
      cacheStatus = "MISS";
    }

    if (ifNoneMatch && ifNoneMatch === payload.etag) {
      return new Response(null, {
        status: 304,
        headers: {
          ...corsHeaders,
          ETag: payload.etag,
          "Cache-Control": "public, max-age=10, stale-while-revalidate=60",
          "X-Cache": cacheStatus,
        },
      });
    }

    return new Response(payload.encrypted, {
      status: 200,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
        ETag: payload.etag,
        "Cache-Control": "public, max-age=10, stale-while-revalidate=60",
        "X-Cache": cacheStatus,
        "X-Payload-Encryption": "AES-256-GCM",
      },
    });
  } catch (e) {
    console.error("cached-data error", e);
    return new Response(
      JSON.stringify({ error: e instanceof Error ? e.message : "unknown" }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }
});
