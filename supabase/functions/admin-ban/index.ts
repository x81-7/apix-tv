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
    const action = url.searchParams.get("action") ?? "list";

    if (action === "list") {
      const { data, error } = await supabase
        .from("app_users")
        .select("*")
        .order("last_seen_at", { ascending: false })
        .limit(500);
      if (error) throw error;
      return json({ users: data });
    }

    const body = await req.json().catch(() => ({}));

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

    const deviceId = body.device_id as string;
    if (!deviceId) return json({ error: "device_id required" }, 400);

    if (action === "ban") {
      const status = (body.status as string) ?? "PERMA_BAN";
      const reason = (body.reason as string) ?? "MANUAL_BAN";
      const minutes = Number(body.minutes ?? 0);
      const banUntil = minutes > 0 ? new Date(Date.now() + minutes * 60_000).toISOString() : null;

      await supabase
        .from("app_users")
        .update({ status, ban_reason: reason, ban_until: banUntil })
        .eq("device_id", deviceId);
      await supabase.from("ban_history").insert({
        device_id: deviceId,
        status,
        reason,
        ban_until: banUntil,
      });
      return json({ ok: true });
    }

    if (action === "unban") {
      await supabase
        .from("ban_history")
        .delete()
        .eq("device_id", deviceId);
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
        .eq("device_id", deviceId);
      return json({ ok: true });
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
