// Admin endpoint to manually ban / unban / list users
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-admin-token",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

  // Simple admin token gate (replace with proper auth later)
  const adminToken = Deno.env.get("ADMIN_API_TOKEN");
  if (adminToken && req.headers.get("x-admin-token") !== adminToken) {
    return json({ error: "unauthorized" }, 401);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );

  try {
    const url = new URL(req.url);
    const body = req.method === "GET" ? {} : await req.json().catch(() => ({}));
    const action = url.searchParams.get("action") ?? String(body.action ?? "list");

    if (action === "list") {
      const { data, error } = await supabase
        .from("app_users")
        .select("*")
        .order("last_seen_at", { ascending: false })
        .limit(500);
      if (error) throw error;
      return json({ users: data });
    }

    // Bulk unban every banned/tampered device — admin emergency reset.
    if (action === "unban_all") {
      const BANNED = ["PERMA_BAN", "TEMP_BAN", "TAMPERED_MOD", "ENVIRONMENT_DANGER"];
      await supabase.from("ban_history").delete().neq("device_id", "");
      const { error } = await supabase
        .from("app_users")
        .update({
          status: "ACTIVE",
          ban_reason: null,
          ban_until: null,
          strike_count: 0,
          install_count: 0,
          last_strike_at: null,
        })
        .in("status", BANNED);
      if (error) throw error;
      return json({ ok: true });
    }

    const rawId = (body.device_id as string) || "";
    if (!rawId) return json({ error: "device_id required" }, 400);
    const deviceId = rawId.trim();
    const idLower = deviceId.toLowerCase();
    // The admin may paste EITHER a real device_id (Widevine ID) OR the SHA-256
    // signature_hash shown in the panel. We try device_id first, then fall
    // back to signature_hash so both work transparently.
    const findTargets = async (): Promise<string[]> => {
      const byId = await supabase.from("app_users").select("device_id").eq("device_id", deviceId);
      const ids = new Set<string>((byId.data ?? []).map((r: any) => r.device_id));
      if (idLower.length === 64) {
        const bySig = await supabase.from("app_users").select("device_id").eq("signature_hash", idLower);
        for (const r of bySig.data ?? []) ids.add(r.device_id);
      }
      return Array.from(ids);
    };

    if (action === "ban") {
      const status = (body.status as string) ?? "PERMA_BAN";
      const reason = (body.reason as string) ?? "MANUAL_BAN";
      const minutes = Number(body.minutes ?? 0);
      const banUntil = minutes > 0 ? new Date(Date.now() + minutes * 60_000).toISOString() : null;

      const targets = await findTargets();
      // If no existing row, create a stub row keyed on whatever the admin
      // typed so the next handshake for that id/signature will hit the ban.
      const finalIds = targets.length > 0 ? targets : [deviceId];
      for (const id of finalIds) {
        const { error: upsertError } = await supabase
          .from("app_users")
          .upsert(
            {
              device_id: id,
              status,
              ban_reason: reason,
              ban_until: banUntil,
              // Store the pasted value in signature_hash too so cross-fingerprint
              // lookup in device-handshake catches it when the real device connects.
              ...(idLower.length === 64 ? { signature_hash: idLower } : {}),
              last_seen_at: new Date().toISOString(),
            },
            { onConflict: "device_id" },
          );
        if (upsertError) throw upsertError;
        const { error: historyError } = await supabase.from("ban_history").insert({
          device_id: id,
          status,
          reason,
          ban_until: banUntil,
        });
        if (historyError) throw historyError;
      }
      return json({ ok: true, targets: finalIds.length });
    }

    if (action === "unban") {
      const targets = await findTargets();
      const finalIds = targets.length > 0 ? targets : [deviceId];
      for (const id of finalIds) {
        await supabase.from("ban_history").delete().eq("device_id", id);
        await supabase
          .from("app_users")
          .update({
            status: "ACTIVE",
            ban_reason: null,
            ban_until: null,
            strike_count: 0,
            install_count: 0,
            last_strike_at: null,
          })
          .eq("device_id", id);
      }
      return json({ ok: true, targets: finalIds.length });
    }


    if (action === "rename") {
      const raw = (body.custom_name as string | null | undefined) ?? "";
      const cleaned = String(raw).trim().slice(0, 60);
      const { error } = await supabase
        .from("app_users")
        .update({ custom_name: cleaned.length ? cleaned : null })
        .eq("device_id", deviceId);
      if (error) throw error;
      return json({ ok: true });
    }

    return json({ error: "unknown action" }, 400);
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});

function json(d: unknown, status = 200) {
  return new Response(JSON.stringify(d), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
