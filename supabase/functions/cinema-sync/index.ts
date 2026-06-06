// Edge Function: cinema-sync
//
// Pulls movies / series / anime metadata from TMDB using the key stored in the
// panel (cinema_providers.tmdb_api_key) and writes a ready-to-serve catalog into
// the `cinema_catalog` table. The Android app NEVER talks to TMDB — it only ever
// reads the prepared catalog through the Cloudflare cinema worker → cinema-gateway
// (action: "home"). This is the "TMDB → Supabase → Worker" pipeline.
//
// Trigger:
//   - Admin (panel user) POST { action: "run" }
//   - Or a Cron job hitting it with the service key.
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

const IMG = "https://image.tmdb.org/t/p/w500";
const IMG_BG = "https://image.tmdb.org/t/p/w1280";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const svc = () => createClient(SUPABASE_URL, SERVICE_ROLE, { auth: { persistSession: false } });

async function requireUser(req: Request): Promise<boolean> {
  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.replace(/^Bearer\s+/i, "").trim();
  if (!token) return false;
  if (token === SERVICE_ROLE) return true; // cron / internal
  if (token === ANON_KEY) return false;
  try {
    const client = createClient(SUPABASE_URL, ANON_KEY, {
      global: { headers: { Authorization: `Bearer ${token}` } },
      auth: { persistSession: false },
    });
    const { data, error } = await client.auth.getUser();
    return !error && !!data?.user;
  } catch { return false; }
}

interface RowDef {
  section: string;       // vod | series | anime
  row_key: string;
  row_title: string;
  path: string;          // TMDB endpoint
  query?: Record<string, string>;
  hero?: boolean;        // contribute to the hero carousel
}

// The ordered set of carousels each section exposes. The app renders them
// top-to-bottom exactly in this order.
const ROWS: RowDef[] = [
  // ── Movies (vod) ──
  { section: "vod", row_key: "trending", row_title: "الأكثر رواجاً", path: "trending/movie/week", hero: true },
  { section: "vod", row_key: "popular", row_title: "أفلام شائعة", path: "movie/popular" },
  { section: "vod", row_key: "top_rated", row_title: "الأعلى تقييماً", path: "movie/top_rated" },
  { section: "vod", row_key: "now_playing", row_title: "في الصالات الآن", path: "movie/now_playing" },
  // ── Series ──
  { section: "series", row_key: "trending", row_title: "مسلسلات رائجة", path: "trending/tv/week", hero: true },
  { section: "series", row_key: "popular", row_title: "مسلسلات شائعة", path: "tv/popular" },
  { section: "series", row_key: "top_rated", row_title: "الأعلى تقييماً", path: "tv/top_rated" },
  // ── Anime ──
  {
    section: "anime", row_key: "popular", row_title: "أنمي شائع",
    path: "discover/tv", hero: true,
    query: { with_genres: "16", with_origin_country: "JP", sort_by: "popularity.desc" },
  },
  {
    section: "anime", row_key: "top_rated", row_title: "أعلى الأنمي تقييماً",
    path: "discover/tv",
    query: { with_genres: "16", with_origin_country: "JP", sort_by: "vote_average.desc", "vote_count.gte": "200" },
  },
];

async function tmdb(key: string, path: string, query: Record<string, string> = {}) {
  const qs = new URLSearchParams({ api_key: key, language: "ar", ...query });
  const res = await fetch(`https://api.themoviedb.org/3/${path}?${qs.toString()}`);
  if (!res.ok) throw new Error(`TMDB ${path} → HTTP ${res.status}`);
  return await res.json();
}

function mapItem(section: string, row: RowDef, raw: any, idx: number) {
  const title = raw.title || raw.name || raw.original_title || raw.original_name || "";
  const date = raw.release_date || raw.first_air_date || "";
  return {
    section,
    tmdb_id: String(raw.id),
    title,
    poster: raw.poster_path ? IMG + raw.poster_path : "",
    backdrop: raw.backdrop_path ? IMG_BG + raw.backdrop_path : "",
    description: raw.overview || "",
    rating: raw.vote_average ? Number(raw.vote_average).toFixed(1) : "",
    year: date ? String(date).slice(0, 4) : "",
    popularity: Number(raw.popularity ?? 0),
    row_key: row.row_key,
    row_title: row.row_title,
    is_hero: !!row.hero && idx < 5,
    sort_order: idx,
    extra: {},
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const ok = await requireUser(req);
    if (!ok) return json({ success: false, error: "unauthorized" }, 401);

    const { data: provider, error: pErr } = await svc()
      .from("cinema_providers")
      .select("tmdb_api_key, anime_enabled, active")
      .eq("active", true)
      .order("updated_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (pErr) throw pErr;

    const key = provider?.tmdb_api_key?.trim();
    if (!key) return json({ success: false, error: "TMDB API key not configured" }, 400);

    const animeEnabled = provider?.anime_enabled !== false;
    const rows = ROWS.filter((r) => r.section !== "anime" || animeEnabled);

    const sections = [...new Set(rows.map((r) => r.section))];

    // Fetch everything first so a single failing call doesn't wipe the catalog.
    const allItems: any[] = [];
    for (const row of rows) {
      try {
        const data = await tmdb(key, row.path, row.query ?? {});
        const results: any[] = Array.isArray(data?.results) ? data.results : [];
        results.slice(0, 20).forEach((raw, idx) => {
          if (!raw?.id) return;
          allItems.push(mapItem(row.section, row, raw, idx));
        });
      } catch (e) {
        console.warn("cinema-sync row failed", row.row_key, e);
      }
    }

    if (allItems.length === 0) {
      return json({ success: false, error: "no content fetched from TMDB" }, 502);
    }

    // Replace catalog for the sections we just synced.
    const del = await svc().from("cinema_catalog").delete().in("section", sections);
    if (del.error) throw del.error;

    const ins = await svc().from("cinema_catalog").insert(allItems);
    if (ins.error) throw ins.error;

    // Bust the cached bundle so apps pick up new content quickly.
    try {
      await fetch(`${SUPABASE_URL}/functions/v1/cached-data/invalidate`, {
        method: "POST",
        headers: { Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
        body: "{}",
      });
    } catch { /* best effort */ }

    return json({ success: true, inserted: allItems.length, sections });
  } catch (e) {
    console.error("cinema-sync error", e);
    return json({ success: false, error: e instanceof Error ? e.message : "unknown" }, 500);
  }
});
