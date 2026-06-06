// Edge Function: cinema-gateway
//
// The single backend for the Cinema / VOD + Live TV system. It proxies an
// Xtream Codes IPTV account (movies/series/live) and optionally enriches with
// TMDB metadata — all server-side, so the IPTV credentials and the TMDB key
// NEVER reach the app. Reached by the apps through the Cloudflare Worker
// (which forwards /functions/v1/*), exactly like cached-data.
//
// Body shape: { action, ...params }
//
// Admin actions (require a valid signed-in panel user):
//   - get-config   : returns masked provider config + app_mode
//   - save-config  : upsert the active provider (host/port/user/pass/tmdb/flags)
//   - set-app-mode : store app_mode (HYBRID | CINEMA_ONLY | SPORTS_ONLY)
//   - test         : verify the Xtream account (returns user_info/server_info)
//
// Public actions (apps, anon):
//   - catalog : { section: 'vod'|'series'|'live', kind: 'categories'|'streams'|'info',
//                 category_id?, id? }  → proxied Xtream data
//   - resolve : { section, id, ext? } → playable stream URL
//   - tmdb    : { path, query? }      → proxied TMDB GET using the stored key
//
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const svc = () => createClient(SUPABASE_URL, SERVICE_ROLE, { auth: { persistSession: false } });

interface Provider {
  id: string;
  host: string | null;
  port: number | null;
  username: string | null;
  password: string | null;
  tmdb_api_key: string | null;
  vod_enabled: boolean;
  series_enabled: boolean;
  live_enabled: boolean;
  anime_enabled: boolean;
  movie_link_template: string | null;
  series_link_template: string | null;
  active: boolean;
}

/** Canonical media item — MUST match Android com.apix.app.data.MediaItem. */
interface MediaItem {
  id: string;
  title: string;
  poster: string;
  backdrop: string;
  description: string;
  rating: string;
  year: string;
  section: string;       // vod | series | anime | live
  tmdb_id: string;
  url: string | null;    // direct stream (Xtream) or null when scraping is needed
  useLocalProxy: boolean;
  ext: string;           // container extension
}

/** Canonical home payload — MUST match Android com.apix.app.data.HomeData. */
interface HomeRow { id: string; title: string; items: MediaItem[]; }
interface HomePayload { hero: MediaItem[]; rows: HomeRow[]; }

async function getActiveProvider(): Promise<Provider | null> {
  const { data, error } = await svc()
    .from("cinema_providers")
    .select("*")
    .eq("active", true)
    .order("updated_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return (data as Provider) ?? null;
}

/** Normalise host into a scheme+host[:port] base, defaulting to http. */
function providerBase(p: Provider): string {
  let host = (p.host ?? "").trim().replace(/\/+$/, "");
  if (!host) throw new Error("provider host not configured");
  if (!/^https?:\/\//i.test(host)) host = `http://${host}`;
  if (p.port && !/:\d+$/.test(host.replace(/^https?:\/\//i, ""))) {
    host = `${host}:${p.port}`;
  }
  return host;
}

async function xtream(p: Provider, params: Record<string, string>): Promise<unknown> {
  const base = providerBase(p);
  const qs = new URLSearchParams({
    username: p.username ?? "",
    password: p.password ?? "",
    ...params,
  });
  const url = `${base}/player_api.php?${qs.toString()}`;
  const res = await fetch(url, { headers: { "User-Agent": "AppleCoreMedia/1.0" } });
  if (!res.ok) throw new Error(`Xtream HTTP ${res.status}`);
  const text = await res.text();
  try { return text ? JSON.parse(text) : null; } catch { return { raw: text }; }
}

/** Verify the caller is an authenticated panel user (for admin actions). */
async function requireUser(req: Request): Promise<boolean> {
  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.replace(/^Bearer\s+/i, "").trim();
  if (!token || token === ANON_KEY) return false;
  try {
    const client = createClient(SUPABASE_URL, ANON_KEY, {
      global: { headers: { Authorization: `Bearer ${token}` } },
      auth: { persistSession: false },
    });
    const { data, error } = await client.auth.getUser();
    return !error && !!data?.user;
  } catch { return false; }
}

function maskConfig(p: Provider | null, appMode: string) {
  if (!p) return { configured: false, app_mode: appMode };
  return {
    configured: true,
    app_mode: appMode,
    host: p.host,
    port: p.port,
    username: p.username,
    has_password: !!p.password,
    has_tmdb: !!p.tmdb_api_key,
    vod_enabled: p.vod_enabled,
    series_enabled: p.series_enabled,
    live_enabled: p.live_enabled,
    anime_enabled: p.anime_enabled,
    movie_link_template: p.movie_link_template,
    series_link_template: p.series_link_template,
    active: p.active,
  };
}

async function getAppMode(): Promise<string> {
  const { data } = await svc()
    .from("system_settings").select("value").eq("key", "appMode").maybeSingle();
  const v = (data?.value as { mode?: string } | null) ?? null;
  return (v?.mode ?? "HYBRID").toUpperCase();
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const body = await req.json().catch(() => ({}));
    const action = String(body?.action ?? "");

    // ───────── Admin actions ─────────
    const ADMIN = new Set(["get-config", "save-config", "set-app-mode", "test"]);
    if (ADMIN.has(action)) {
      const ok = await requireUser(req);
      if (!ok) return json({ success: false, error: "unauthorized" }, 401);
    }

    if (action === "get-config") {
      const p = await getActiveProvider();
      return json({ success: true, ...maskConfig(p, await getAppMode()) });
    }

    if (action === "save-config") {
      const existing = await getActiveProvider();
      const payload: Record<string, unknown> = {
        name: body.name ?? "Primary",
        kind: "xtream",
        host: body.host ?? null,
        port: body.port != null ? Number(body.port) : null,
        username: body.username ?? null,
        // Keep existing password if a blank one is submitted (masked field).
        password: body.password ? body.password : existing?.password ?? null,
        tmdb_api_key: body.tmdb_api_key !== undefined
          ? (body.tmdb_api_key || null)
          : existing?.tmdb_api_key ?? null,
        vod_enabled: body.vod_enabled ?? true,
        series_enabled: body.series_enabled ?? true,
        live_enabled: body.live_enabled ?? true,
        anime_enabled: body.anime_enabled ?? true,
        movie_link_template: body.movie_link_template !== undefined
          ? (body.movie_link_template || null)
          : existing?.movie_link_template ?? null,
        series_link_template: body.series_link_template !== undefined
          ? (body.series_link_template || null)
          : existing?.series_link_template ?? null,
        active: true,
      };
      let res;
      if (existing) {
        res = await svc().from("cinema_providers").update(payload).eq("id", existing.id).select().maybeSingle();
      } else {
        res = await svc().from("cinema_providers").insert(payload).select().maybeSingle();
      }
      if (res.error) throw res.error;
      // Bust the cached-data bundle so app_mode/settings propagate fast.
      try {
        await fetch(`${SUPABASE_URL}/functions/v1/cached-data/invalidate`, {
          method: "POST",
          headers: { Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
          body: "{}",
        });
      } catch { /* best-effort */ }
      return json({ success: true, ...maskConfig(res.data as Provider, await getAppMode()) });
    }

    if (action === "set-app-mode") {
      const mode = String(body.mode ?? "HYBRID").toUpperCase();
      const allowed = new Set(["HYBRID", "CINEMA_ONLY", "SPORTS_ONLY"]);
      if (!allowed.has(mode)) return json({ success: false, error: "invalid mode" }, 400);
      const { data: existing } = await svc()
        .from("system_settings").select("id").eq("key", "appMode").maybeSingle();
      if (existing?.id) {
        await svc().from("system_settings").update({ value: { mode } }).eq("id", existing.id);
      } else {
        await svc().from("system_settings").insert({ key: "appMode", value: { mode }, description: "App mode" });
      }
      try {
        await fetch(`${SUPABASE_URL}/functions/v1/cached-data/invalidate`, {
          method: "POST",
          headers: { Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
          body: "{}",
        });
      } catch { /* best-effort */ }
      return json({ success: true, app_mode: mode });
    }

    if (action === "test") {
      const p = await getActiveProvider();
      if (!p) return json({ success: false, error: "no provider configured" }, 400);
      const info = await xtream(p, {});
      const ok = !!(info && (info as any).user_info);
      return json({ success: ok, info });
    }

    if (action === "sync") {
      // Trigger the TMDB → Supabase catalog sync (admin only).
      const res = await fetch(`${SUPABASE_URL}/functions/v1/cinema-sync`, {
        method: "POST",
        headers: { Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
        body: JSON.stringify({ action: "run" }),
      });
      const data = await res.json().catch(() => null);
      return json({ success: res.ok && data?.success !== false, ...data }, res.ok ? 200 : 502);
    }

    // ───────── Public content actions ─────────
    const appMode = await getAppMode();
    const p = await getActiveProvider();

    // home → the SINGLE unified payload the app renders. Shape matches the
    // Android data classes exactly: { success, app_mode, hero, rows[] }.
    if (action === "home") {
      const payload = await buildHome(p, appMode);
      return json({ success: true, app_mode: appMode, hero: payload.hero, rows: payload.rows });
    }

    if (!p) return json({ success: false, error: "no provider" }, 503);


    if (action === "catalog") {
      const section = String(body.section ?? "vod");
      const kind = String(body.kind ?? "categories");
      const actionMap: Record<string, Record<string, string>> = {
        vod: { categories: "get_vod_categories", streams: "get_vod_streams", info: "get_vod_info" },
        series: { categories: "get_series_categories", streams: "get_series", info: "get_series_info" },
        live: { categories: "get_live_categories", streams: "get_live_streams", info: "" },
      };
      const xa = actionMap[section]?.[kind];
      if (!xa) return json({ success: false, error: "bad section/kind" }, 400);
      const params: Record<string, string> = { action: xa };
      if (body.category_id) params["category_id"] = String(body.category_id);
      if (kind === "info") {
        if (section === "vod" && body.id) params["vod_id"] = String(body.id);
        if (section === "series" && body.id) params["series_id"] = String(body.id);
      }
      const data = await xtream(p, params);
      return json({ success: true, section, kind, data });
    }

    if (action === "resolve") {
      const section = String(body.section ?? "vod");
      const id = String(body.id ?? "");
      const tmdbId = String(body.tmdb_id ?? "");
      if (!id && !tmdbId) return json({ success: false, error: "id required" }, 400);

      // TMDB-backed titles (vod/series/anime) resolve to a scraper embed URL via
      // the panel link template. The app then runs the Hidden WebView Scraper on
      // this URL to extract the real .m3u8 + headers.
      if (tmdbId && section !== "live") {
        const season = String(body.season ?? body.s ?? "1");
        const episode = String(body.episode ?? body.e ?? "1");
        const tpl = section === "series" || section === "anime"
          ? p.series_link_template
          : p.movie_link_template;
        if (tpl && tpl.trim()) {
          const embed = tpl
            .replace(/\{tmdb_id\}/g, tmdbId)
            .replace(/\{tmdb\}/g, tmdbId)
            .replace(/\{season\}/g, season).replace(/\{s\}/g, season)
            .replace(/\{episode\}/g, episode).replace(/\{e\}/g, episode);
          let referer = "";
          try { referer = new URL(embed).origin + "/"; } catch { /* ignore */ }
          return json({ success: true, url: embed, scrape: true, referer });
        }
        return json({ success: false, error: "no link template configured" }, 400);
      }

      // Xtream direct stream (no scraping needed).
      if (!p) return json({ success: false, error: "no provider" }, 503);
      const ext = String(body.ext ?? (section === "live" ? "ts" : "mp4")).replace(/[^a-z0-9]/gi, "");
      const base = providerBase(p);
      const seg = section === "live" ? "live" : section === "series" ? "series" : "movie";
      const url = `${base}/${seg}/${encodeURIComponent(p.username ?? "")}/${encodeURIComponent(p.password ?? "")}/${id}.${ext}`;
      return json({ success: true, url, scrape: false });
    }


    if (action === "tmdb") {
      if (!p.tmdb_api_key) return json({ success: false, error: "tmdb not configured" }, 400);
      const path = String(body.path ?? "").replace(/^\/+/, "");
      if (!path) return json({ success: false, error: "path required" }, 400);
      const qs = new URLSearchParams({ api_key: p.tmdb_api_key, ...(body.query ?? {}) });
      const res = await fetch(`https://api.themoviedb.org/3/${path}?${qs.toString()}`);
      const data = await res.json().catch(() => null);
      return json({ success: res.ok, data });
    }

    return json({ success: false, error: "unknown action" }, 400);
  } catch (e) {
    console.error("cinema-gateway error", e);
    const msg = e instanceof Error ? e.message : "Unknown error";
    return json({ success: false, error: msg }, 500);
  }
});
