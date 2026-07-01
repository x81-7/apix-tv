// Device handshake: receives MediaDrm ID + integrity payload, returns ban status
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";
import { encryptedJson, plainJson } from "../_shared/encrypted-response.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface HandshakePayload {
  device_id: string;            // MediaDrm Widevine ID
  platform?: string;
  signature_hash?: string;      // SHA-256 of installed APK signature
  dex_checksum?: string;        // CRC32/MD5 of classes.dex
  app_version?: string;
  is_fresh_install?: boolean;   // true when local marker missing
  environment_danger?: boolean; // debugger/frida detected client-side
  danger_details?: string;
}

type Status = "ACTIVE" | "TEMP_BAN" | "PERMA_BAN" | "TAMPERED_MOD" | "ENVIRONMENT_DANGER";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const body = (await req.json()) as HandshakePayload;
    if (!body.device_id || body.device_id.length < 8) {
      return plainJson({ status: "ERROR", message: "invalid device_id" }, 400);
    }
    const platform = String(body.platform ?? "android").toLowerCase();

    // IP + Geo (best-effort)
    const ip =
      req.headers.get("x-forwarded-for")?.split(",")[0].trim() ||
      req.headers.get("cf-connecting-ip") ||
      "0.0.0.0";
    const country = req.headers.get("cf-ipcountry") || req.headers.get("x-country") || null;
    const city = req.headers.get("cf-ipcity") || null;
    const region = req.headers.get("cf-region") || null;

    // Load ban config
    const { data: cfgRow } = await supabase
      .from("system_settings")
      .select("value")
      .eq("key", "ban_config")
      .maybeSingle();
    const cfg = (cfgRow?.value ?? {}) as Record<string, unknown>;
    const officialSig = String(cfg.official_signature_sha256 ?? "").toLowerCase();
    const maxIpChangesPerDevice = Math.max(Number(cfg.max_ip_changes_per_device ?? 2), 1);
    const tempBanMin = Number(cfg.temp_ban_minutes ?? 15);
    const tempThreshold = Number(cfg.temp_ban_threshold ?? 4);
    const permaThreshold = Number(cfg.perma_ban_threshold ?? 6);
    const strikeWindowH = Number(cfg.strike_window_hours ?? 24);
    const resetDays = Number(cfg.reset_after_days ?? 2);
    const telegramUrl = String(cfg.telegram_url ?? "");
    const integrityCheckEnabled = cfg.integrity_check_enabled !== false;
    const autoTempBanEnabled = cfg.auto_temp_ban_enabled !== false;
    const autoPermaBanEnabled = cfg.auto_perma_ban_enabled !== false;

    // Fetch existing user
    const { data: existing } = await supabase
      .from("app_users")
      .select("*")
      .eq("device_id", body.device_id)
      .maybeSingle();

    const now = new Date();
    let status: Status = "ACTIVE";
    let strikes = existing?.strike_count ?? 0;
    let installCount = existing?.install_count ?? 0;
    let banUntil: Date | null = existing?.ban_until ? new Date(existing.ban_until) : null;
    let banReason = existing?.ban_reason ?? null;
    const normalizedSignature = (body.signature_hash ?? "").toLowerCase();

    const { data: allowedRow } = await supabase
      .from("system_settings")
      .select("value")
      .eq("key", "security_signatures")
      .maybeSingle();
    const allowedSignatures = Array.isArray(allowedRow?.value)
      ? allowedRow.value
          .filter((entry: any) => entry?.enabled !== false && entry?.hash)
          .map((entry: any) => String(entry.hash).toLowerCase())
      : [];

    const { data: blockedRow } = await supabase
      .from("system_settings")
      .select("value")
      .eq("key", "security_blocked_signatures")
      .maybeSingle();
    const blockedSignatures = Array.isArray(blockedRow?.value)
      ? blockedRow.value
          .filter((entry: any) => entry?.enabled !== false && entry?.hash)
          .map((entry: any) => String(entry.hash).toLowerCase())
      : [];

    // Reset old strikes (no violation in resetDays)
    if (existing?.last_strike_at) {
      const last = new Date(existing.last_strike_at);
      const ageDays = (now.getTime() - last.getTime()) / 86_400_000;
      if (ageDays >= resetDays && existing.status === "ACTIVE") {
        strikes = 0;
      }
    }

    if (status === "ACTIVE" && integrityCheckEnabled) {
      const hasManualAllowList = allowedSignatures.length > 0;
      const isIos = platform === "ios";
      const isBlocked = !isIos && !!normalizedSignature && blockedSignatures.includes(normalizedSignature);
      const isAllowed = isIos || (hasManualAllowList
        ? !!normalizedSignature && allowedSignatures.includes(normalizedSignature)
        : !!officialSig && !!normalizedSignature && normalizedSignature === officialSig);

      if (isBlocked) {
        status = "TAMPERED_MOD";
        banReason = "BLOCKED_SIGNATURE";
        await supabase.from("integrity_logs").insert({
          device_id: body.device_id,
          signature_hash: body.signature_hash ?? null,
          dex_checksum: body.dex_checksum ?? null,
          threat_type: "BLOCKED_SIGNATURE",
          ip_address: ip,
          details: {
            blocked_signatures: blockedSignatures,
            received_signature: normalizedSignature || null,
          },
        });
      } else if (!isAllowed) {
        status = "TAMPERED_MOD";
        banReason = normalizedSignature ? "UNAUTHORIZED_SIGNATURE" : "MISSING_SIGNATURE";
        await supabase.from("integrity_logs").insert({
          device_id: body.device_id,
          signature_hash: body.signature_hash ?? null,
          dex_checksum: body.dex_checksum ?? null,
          threat_type: "UNAUTHORIZED_SIGNATURE",
          ip_address: ip,
          details: {
            official_signature: officialSig || null,
            allowed_signatures: allowedSignatures,
            received_signature: normalizedSignature || null,
          },
        });
      }
    }

    // 1) ENVIRONMENT DANGER (debugger/frida) — instant ban
    if (body.environment_danger && (cfg.anti_debug_enabled !== false || cfg.anti_hook_enabled !== false)) {
      status = "PERMA_BAN";
      banReason = `ENVIRONMENT_DANGER: ${body.danger_details ?? "debugger/hook"}`;
      await supabase.from("integrity_logs").insert({
        device_id: body.device_id,
        signature_hash: body.signature_hash ?? null,
        dex_checksum: body.dex_checksum ?? null,
        threat_type: "ENVIRONMENT_DANGER",
        ip_address: ip,
        details: { danger_details: body.danger_details ?? null },
      });
    }

    // 2) Legacy fallback — keep official single-signature comparison when no manual list exists
    if (
      status === "ACTIVE" &&
      integrityCheckEnabled &&
      platform !== "ios" &&
      allowedSignatures.length === 0 &&
      officialSig &&
      body.signature_hash &&
      body.signature_hash.toLowerCase() !== officialSig
    ) {
      status = "TAMPERED_MOD";
      banReason = "SIGNATURE_MISMATCH";
      await supabase.from("integrity_logs").insert({
        device_id: body.device_id,
        signature_hash: body.signature_hash,
        dex_checksum: body.dex_checksum ?? null,
        threat_type: "CRITICAL_THREAT",
        ip_address: ip,
        details: { expected: officialSig, got: body.signature_hash.toLowerCase() },
      });
    }

    if (status === "ACTIVE" && existing?.ip_address && ip && existing.ip_address !== ip) {
      const windowStart = new Date(now.getTime() - 24 * 3600_000).toISOString();
      const { count: ipCount } = await supabase
        .from("ban_history")
        .select("*", { count: "exact", head: true })
        .eq("device_id", body.device_id)
        .eq("status", "IP_CHANGE")
        .gte("created_at", windowStart);

      const newIpChangeCount = (ipCount ?? 0) + 1;
      await supabase.from("ban_history").insert({
        device_id: body.device_id,
        status: "IP_CHANGE",
        reason: `IP_CHANGE:${existing.ip_address}->${ip}`,
        ip_address: ip,
        ban_until: null,
      });

      if (newIpChangeCount > maxIpChangesPerDevice) {
        if (autoPermaBanEnabled) {
          status = "PERMA_BAN";
          banReason = `MULTI_IP_DEVICE_MISMATCH:${newIpChangeCount}`;
        } else {
          await supabase.from("integrity_logs").insert({
            device_id: body.device_id,
            signature_hash: body.signature_hash ?? null,
            dex_checksum: body.dex_checksum ?? null,
            threat_type: "AUTO_PERMA_DISABLED_WARNING",
            ip_address: ip,
            details: { reason: "MULTI_IP_DEVICE_MISMATCH", count: newIpChangeCount },
          });
        }
      }
    }

    // 3) Strike system — fresh install detection
    if (status === "ACTIVE" && body.is_fresh_install) {
      installCount += 1;
      // Count strikes within window
      const windowStart = new Date(now.getTime() - strikeWindowH * 3600_000).toISOString();
      const { count } = await supabase
        .from("ban_history")
        .select("*", { count: "exact", head: true })
        .eq("device_id", body.device_id)
        .gte("created_at", windowStart);
      const recentInstalls = (count ?? 0) + 1;
      strikes = recentInstalls;

      if (recentInstalls >= permaThreshold) {
        if (autoPermaBanEnabled) {
          status = "PERMA_BAN";
          banReason = `REINSTALL_ABUSE: ${recentInstalls} fresh installs in ${strikeWindowH}h`;
        } else {
          await supabase.from("integrity_logs").insert({
            device_id: body.device_id,
            signature_hash: body.signature_hash ?? null,
            dex_checksum: body.dex_checksum ?? null,
            threat_type: "AUTO_PERMA_DISABLED_WARNING",
            ip_address: ip,
            details: { reason: "REINSTALL_ABUSE", count: recentInstalls, window_hours: strikeWindowH },
          });
        }
      } else if (recentInstalls >= tempThreshold) {
        if (autoTempBanEnabled) {
          status = "TEMP_BAN";
          banUntil = new Date(now.getTime() + tempBanMin * 60_000);
          banReason = `REINSTALL_TEMP: ${recentInstalls} fresh installs`;
        } else {
          await supabase.from("integrity_logs").insert({
            device_id: body.device_id,
            signature_hash: body.signature_hash ?? null,
            dex_checksum: body.dex_checksum ?? null,
            threat_type: "AUTO_TEMP_DISABLED_WARNING",
            ip_address: ip,
            details: { reason: "REINSTALL_TEMP", count: recentInstalls, minutes: tempBanMin },
          });
        }
      }

      await supabase.from("ban_history").insert({
        device_id: body.device_id,
        status: status === "ACTIVE" ? "FRESH_INSTALL" : status,
        reason: banReason,
        ip_address: ip,
        ban_until: banUntil ? banUntil.toISOString() : null,
      });
    }

    // 4) Honor existing ban
    if (status === "ACTIVE" && existing) {
      if (existing.status === "PERMA_BAN" || existing.status === "TAMPERED_MOD") {
        status = existing.status as Status;
        banReason = existing.ban_reason;
      } else if (existing.status === "TEMP_BAN" && banUntil && banUntil > now) {
        status = "TEMP_BAN";
      } else if (existing.status === "TEMP_BAN" && banUntil && banUntil <= now) {
        status = "ACTIVE";
        banUntil = null;
        banReason = null;
      }
    }

    // Upsert user record
    const upsertPayload = {
      device_id: body.device_id,
      ip_address: ip,
      country,
      city,
      region,
      install_count: installCount,
      strike_count: strikes,
      last_strike_at: body.is_fresh_install ? now.toISOString() : existing?.last_strike_at ?? null,
      last_seen_at: now.toISOString(),
      status,
      ban_until: banUntil ? banUntil.toISOString() : null,
      ban_reason: banReason,
      signature_hash: body.signature_hash ?? existing?.signature_hash ?? null,
      dex_checksum: body.dex_checksum ?? existing?.dex_checksum ?? null,
      app_version: body.app_version ?? existing?.app_version ?? null,
    };

    await supabase.from("app_users").upsert(upsertPayload, { onConflict: "device_id" });

    // A ban that must destroy any locally-cached channels/streams on device.
    // The app reads `wipe` and clears its offline cache before exiting silently.
    const wipe =
      status === "PERMA_BAN" ||
      status === "TAMPERED_MOD" ||
      status === "ENVIRONMENT_DANGER";

    return encryptedJson({
      status,
      ban_until: banUntil?.toISOString() ?? null,
      ban_reason: banReason,
      telegram_url: telegramUrl,
      wipe,
      message: messageFor(status),
    });

  } catch (e) {
    console.error("handshake error", e);
    return plainJson({ status: "ERROR", message: String(e) }, 500);
  }
});

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function messageFor(s: Status) {
  switch (s) {
    case "TEMP_BAN":
      return "تم إيقاف التطبيق مؤقتاً بسبب نشاط مريب";
    case "PERMA_BAN":
      return "تم حظر هذا الجهاز نهائياً";
    case "TAMPERED_MOD":
      return "تم اكتشاف نسخة معدلة";
    case "ENVIRONMENT_DANGER":
      return "بيئة تشغيل غير آمنة";
    default:
      return "OK";
  }
}
