import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const OWNER_TELEGRAM_ID = Number(Deno.env.get("OWNER_TELEGRAM_ID") || "8254788387");

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
    const { supabase, profile, telegramId } = await requireAuthenticatedUser(req);
    const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
    const { count } = await supabase
      .from("activity_logs")
      .select("id", { count: "exact", head: true })
      .eq("actor_telegram_id", telegramId)
      .eq("action", "content_access")
      .gte("created_at", oneMinuteAgo);
    if ((count ?? 0) >= 60) throw new Error("Rate limit exceeded. Please wait a moment.");

    if (!profile.age_verified_at || !profile.consent_accepted_at) {
      return jsonResponse({ error: "Age verification and compliance consent are required before playback." }, 403);
    }

    const { contentId, positionSeconds } = await req.json();
    if (!contentId || typeof contentId !== "string") {
      return jsonResponse({ error: "Valid contentId is required" }, 400);
    }

    const { data: item, error } = await supabase.from("content").select("*").eq("id", contentId).single();
    if (error || !item) return jsonResponse({ error: "Content not found" }, 404);

    const isOwner = telegramId === OWNER_TELEGRAM_ID;
    if (!isOwner && (!item.is_published || !item.rights_verified || !item.consent_documented)) {
      return jsonResponse({ error: "Content is not available for playback" }, 403);
    }

    if (item.is_premium && !isOwner) {
      const { data: sub } = await supabase
        .from("subscriptions")
        .select("id")
        .eq("user_telegram_id", telegramId)
        .eq("status", "active")
        .gt("expires_at", new Date().toISOString())
        .limit(1)
        .maybeSingle();
      if (!sub) {
        return jsonResponse(
          { error: "Active Premium membership required for this content", code: "PREMIUM_REQUIRED" },
          403,
        );
      }
    }

    if (!item.media_path) return jsonResponse({ error: "No authorized media asset linked to this entry" }, 404);

    let playbackUrl = "";
    if (item.media_path.startsWith("https://")) {
      playbackUrl = item.media_path;
    } else {
      const { data: signed, error: signErr } = await supabase.storage
        .from("media-authorized")
        .createSignedUrl(item.media_path, 300);
      if (signErr || !signed?.signedUrl) {
        throw new Error(signErr?.message ?? "Failed to generate authorized playback URL");
      }
      playbackUrl = signed.signedUrl;
    }

    await supabase.from("viewing_history").upsert(
      {
        user_telegram_id: telegramId,
        content_id: item.id,
        last_position_seconds: Math.max(0, Number(positionSeconds ?? 0) || 0),
        viewed_at: new Date().toISOString(),
      },
      { onConflict: "user_telegram_id,content_id" },
    );

    await supabase.from("content").update({ view_count: Number(item.view_count ?? 0) + 1 }).eq("id", item.id);
    await supabase.from("activity_logs").insert({
      actor_telegram_id: telegramId,
      action: "content_access",
      target_type: "content",
      target_id: item.id,
      metadata: { is_premium: item.is_premium },
    });

    return jsonResponse({ playback_url: playbackUrl, expires_in_seconds: 300, media_type: item.media_type });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Access denied";
    return jsonResponse({ error: message }, 400);
  }
});
