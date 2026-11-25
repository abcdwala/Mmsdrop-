import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-dropmms-session",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function timingSafeEqualStr(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function hmacSha256(keyBytes: Uint8Array, message: string): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
}

function base64UrlEncode(input: Uint8Array): string {
  let str = "";
  for (const b of input) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(input: string): Uint8Array {
  const padded = input.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((input.length + 3) % 4);
  const raw = atob(padded);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

async function verifySessionJwt(token: string, jwtSecret: string): Promise<{ telegram_id: number }> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Malformed session token");
  const [headerPart, payloadPart, sigPart] = parts;
  const signingInput = `${headerPart}.${payloadPart}`;
  const expectedSig = base64UrlEncode(
    new Uint8Array(await hmacSha256(new TextEncoder().encode(jwtSecret), signingInput)),
  );
  if (!timingSafeEqualStr(expectedSig, sigPart)) throw new Error("Invalid session token signature");
  const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(payloadPart)));
  if (!payload.exp || payload.exp < Math.floor(Date.now() / 1000)) throw new Error("Session expired");
  const tid = Number(payload.telegram_id);
  if (!tid) throw new Error("Missing telegram_id in session");
  return { telegram_id: tid };
}

async function requireAuthenticatedUser(req: Request) {
  const jwtSecret =
    Deno.env.get("DROPMMS_JWT_SECRET") ||
    Deno.env.get("JWT_SECRET") ||
    Deno.env.get("SUPABASE_JWT_SECRET") ||
    "";
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!jwtSecret || !url || !serviceKey) throw new Error("Missing server secret (DROPMMS_JWT_SECRET)");

  const sessionHeader = req.headers.get("x-dropmms-session") ??
    req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  if (!sessionHeader) throw new Error("Authentication required");

  const claims = await verifySessionJwt(sessionHeader, jwtSecret);
  const supabase = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: profile, error } = await supabase
    .from("profiles")
    .select("*")
    .eq("telegram_id", claims.telegram_id)
    .single();
  if (error || !profile) throw new Error("Profile not found");
  if (profile.is_blocked) throw new Error("Account is blocked");
  return { supabase, profile, telegramId: claims.telegram_id };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { supabase, telegramId } = await requireAuthenticatedUser(req);
    const body = await req.json();
    const action = String(body.action ?? "");

    if (action === "confirm_compliance") {
      const ageConfirmed = Boolean(body.age_confirmed);
      const consentConfirmed = Boolean(body.consent_confirmed);
      if (!ageConfirmed || !consentConfirmed) {
        return jsonResponse({ error: "Both age verification (18+) and consent/rights terms acceptance are required." }, 400);
      }
      const now = new Date().toISOString();
      const { data, error } = await supabase
        .from("profiles")
        .update({ age_verified_at: now, consent_accepted_at: now })
        .eq("telegram_id", telegramId)
        .select("*")
        .single();
      if (error) throw new Error(error.message);
      await supabase.from("activity_logs").insert({
        actor_telegram_id: telegramId,
        action: "confirm_compliance",
        target_type: "profile",
        target_id: String(telegramId),
      });
      return jsonResponse({ profile: data });
    }

    if (action === "toggle_bookmark") {
      const contentId = String(body.content_id ?? "");
      if (!contentId) return jsonResponse({ error: "content_id required" }, 400);

      const { data: existing } = await supabase
        .from("bookmarks")
        .select("id")
        .eq("user_telegram_id", telegramId)
        .eq("content_id", contentId)
        .maybeSingle();

      if (existing) {
        await supabase.from("bookmarks").delete().eq("id", existing.id);
        return jsonResponse({ bookmarked: false });
      } else {
        const { error } = await supabase.from("bookmarks").insert({ user_telegram_id: telegramId, content_id: contentId });
        if (error) throw new Error(error.message);
        return jsonResponse({ bookmarked: true });
      }
    }

    if (action === "submit_report") {
      const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
      const { count } = await supabase
        .from("activity_logs")
        .select("id", { count: "exact", head: true })
        .eq("actor_telegram_id", telegramId)
        .eq("action", "submit_report")
        .gte("created_at", oneMinuteAgo);
      if ((count ?? 0) >= 10) throw new Error("Rate limit exceeded");

      const contentId = String(body.content_id ?? "");
      const reason = String(body.reason ?? "other");
      const details = String(body.details ?? "").trim();
      const removalRequested = Boolean(body.removal_requested);
      if (!contentId || details.length < 5) {
        return jsonResponse({ error: "content_id and report details (min 5 chars) required" }, 400);
      }

      const { data, error } = await supabase
        .from("content_reports")
        .insert({
          reporter_telegram_id: telegramId,
          content_id: contentId,
          reason,
          details: details.slice(0, 2000),
          removal_requested: removalRequested,
          status: "pending",
        })
        .select("*")
        .single();
      if (error) throw new Error(error.message);

      await supabase.from("activity_logs").insert({
        actor_telegram_id: telegramId,
        action: "submit_report",
        target_type: "content_report",
        target_id: data.id,
        metadata: { content_id: contentId, reason, removal_requested: removalRequested },
      });
      return jsonResponse({ report: data });
    }

    if (action === "submit_support") {
      const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
      const { count } = await supabase
        .from("activity_logs")
        .select("id", { count: "exact", head: true })
        .eq("actor_telegram_id", telegramId)
        .eq("action", "submit_support")
        .gte("created_at", oneMinuteAgo);
      if ((count ?? 0) >= 5) throw new Error("Rate limit exceeded");

      const subject = String(body.subject ?? "").trim();
      const message = String(body.message ?? "").trim();
      const category = String(body.category ?? "general");
      if (subject.length < 3 || message.length < 5) {
        return jsonResponse({ error: "Subject (min 3 chars) and message (min 5 chars) required" }, 400);
      }

      const { data, error } = await supabase
        .from("support_requests")
        .insert({
          user_telegram_id: telegramId,
          subject: subject.slice(0, 140),
          message: message.slice(0, 2000),
          category,
          status: "open",
        })
        .select("*")
        .single();
      if (error) throw new Error(error.message);

      await supabase.from("activity_logs").insert({
        actor_telegram_id: telegramId,
        action: "submit_support",
        target_type: "support_request",
        target_id: data.id,
      });
      return jsonResponse({ support_request: data });
    }

    return jsonResponse({ error: "Unsupported user action" }, 400);
  } catch (err) {
    const message = err instanceof Error ? err.message : "User action failed";
    return jsonResponse({ error: message }, 400);
  }
});
