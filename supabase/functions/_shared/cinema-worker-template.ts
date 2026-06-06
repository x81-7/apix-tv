// Dedicated Cinema / VOD Cloudflare Worker template.
//
// Per the architecture: the movies/series ("cinema") traffic runs through its
// OWN Worker, separate from the live-TV/data gateway, so the two never collide.
// This Worker is a thin, hardened proxy whose ONLY upstream is the
// `cinema-gateway` edge function. It hides the backend origin, injects the
// hidden anon key server-side, and exposes a small, stable surface:
//
//   POST /v1/catalog   → cinema-gateway { action: 'catalog', ... }
//   POST /v1/resolve   → cinema-gateway { action: 'resolve', ... }
//   POST /v1/tmdb      → cinema-gateway { action: 'tmdb',    ... }
//   GET  /health       → ok
//
// Hidden bindings (set via the Cloudflare Manager, never in source):
//   SUPA_URL   — backend functions origin
//   SUPA_ANON  — anon key (injected here, never shipped in the APK)
//
// Admin actions (save-config / set-app-mode / test / get-config) are NOT exposed
// here — those are panel-only and go straight to the backend with a user token.

export function buildCinemaWorkerScript(): string {
  return `
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

const PUBLIC_ACTIONS = new Set(["home", "catalog", "resolve", "tmdb"]);

function j(body, status) {
  return new Response(JSON.stringify(body), {
    status: status || 200,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response("ok", { headers: CORS });

    if (url.pathname === "/" || url.pathname === "/health") {
      return j({ ok: true, service: "cinema" });
    }

    // Map /v1/<action> → cinema-gateway action.
    const m = url.pathname.match(/^\\/v1\\/([a-z-]+)$/i);
    if (!m) return j({ error: "not found" }, 404);
    const action = m[1].toLowerCase();
    if (!PUBLIC_ACTIONS.has(action)) return j({ error: "forbidden" }, 403);

    let payload = {};
    try { payload = request.method === "POST" ? await request.json() : {}; } catch { payload = {}; }

    const target = env.SUPA_URL + "/functions/v1/cinema-gateway";
    const upstream = await fetch(target, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        // Inject the anon key server-side; the client never carries it.
        apikey: env.SUPA_ANON,
        Authorization: "Bearer " + env.SUPA_ANON,
      },
      body: JSON.stringify({ ...payload, action }),
    });

    const text = await upstream.text();
    return new Response(text, {
      status: upstream.status,
      headers: { ...CORS, "Content-Type": "application/json" },
    });
  },
};
`;
}
