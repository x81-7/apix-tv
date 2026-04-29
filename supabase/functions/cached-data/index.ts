// Edge Function: cached-data
// Aggregates categories + channels + side_menus + sub_channels + system_settings
// into a single response, with ETag (max(updated_at) + cache_version sum) and
// long Cache-Control. Used by Android/iOS/Windows apps to drastically reduce DB
// load — clients send If-None-Match and receive 304 when nothing changed.
//
// In-memory edge cache (per warm instance) stores the last computed bundle so
// concurrent users hit DB at most once per ~30s window.

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

// Soft TTL — within this window, warm instance returns the cached payload
// without re-querying Postgres. Set to 30s; clients still always send
// If-None-Match so they discover changes nearly instantly when a new instance
// serves them.
const SOFT_TTL_MS = 30_000;

interface Bundle {
  categories: any[];
  channels: any[];
  side_menus: any[];
  sub_channels: any[];
  system_settings: any[];
  bundle_version: number; // sum of cache_version across channels+sub_channels
  generated_at: number;
}

let memCache: { etag: string; body: string; computedAt: number } | null = null;

async function buildBundle(): Promise<{ bundle: Bundle; etag: string; body: string }> {
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

  // Compute a global bundle_version: sum of cache_version on channels+sub_channels.
  // Any change in the panel bumps a row, which changes this sum → ETag changes →
  // clients refetch. Other tables (categories/menus/settings) use updated_at for
  // hashing.
  let bundleVersion = 0;
  for (const c of chans.data ?? []) bundleVersion += Number((c as any).cache_version ?? 0);
  for (const s of subs.data ?? []) bundleVersion += Number((s as any).cache_version ?? 0);

  // Hash other tables' max updated_at into a short suffix.
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

  const bundle: Bundle = {
    categories: cats.data ?? [],
    channels: chans.data ?? [],
    side_menus: menus.data ?? [],
    sub_channels: subs.data ?? [],
    system_settings: settings.data ?? [],
    bundle_version: bundleVersion,
    generated_at: Date.now(),
  };

  const etag = `W/"v${bundleVersion}-a${auxTs}"`;
  const body = JSON.stringify(bundle);
  return { bundle, etag, body };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const url = new URL(req.url);

    // POST /invalidate — called by panel after admin-write to drop the warm cache
    // immediately so clients see new data on next request.
    if (req.method === "POST" && url.pathname.endsWith("/invalidate")) {
      memCache = null;
      return new Response(JSON.stringify({ ok: true }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const ifNoneMatch = req.headers.get("if-none-match") ?? "";

    let payload: { etag: string; body: string };
    let cacheStatus: "HIT" | "MISS" | "REVALIDATE";

    if (memCache && Date.now() - memCache.computedAt < SOFT_TTL_MS) {
      payload = { etag: memCache.etag, body: memCache.body };
      cacheStatus = "HIT";
    } else {
      const fresh = await buildBundle();
      memCache = {
        etag: fresh.etag,
        body: fresh.body,
        computedAt: Date.now(),
      };
      payload = { etag: fresh.etag, body: fresh.body };
      cacheStatus = memCache ? "MISS" : "REVALIDATE";
    }

    // 304 path — saves DB & egress (just headers).
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

    return new Response(payload.body, {
      status: 200,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
        ETag: payload.etag,
        "Cache-Control": "public, max-age=10, stale-while-revalidate=60",
        "X-Cache": cacheStatus,
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
