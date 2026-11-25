import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const OWNER_TELEGRAM_ID = Number(Deno.env.get("OWNER_TELEGRAM_ID") || "8254788387");

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-dropmms-session",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

interface TelegramUserPayload {
  id: number;
  username?: string;
  first_name: string;
  last_name?: string;
  photo_url?: string;
}

function toHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function timingSafeEqualStr(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function hmacSha256(keyBytes: Uint8Array, message: string): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
}

function base64UrlEncode(input: Uint8Array): string {
  let str = "";
  for (const b of input) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function signSupabaseJwt(payload: Record<string, unknown>, jwtSecret: string): Promise<string> {
  const enc = new TextEncoder();
  const headerPart = base64UrlEncode(enc.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payloadPart = base64UrlEncode(enc.encode(JSON.stringify(payload)));
  const signingInput = `${headerPart}.${payloadPart}`;
  const sig = await hmacSha256(enc.encode(jwtSecret), signingInput);
  return `${signingInput}.${base64UrlEncode(new Uint8Array(sig))}`;
}

async function verifyTelegramInitData(initDataRaw: string, botToken: string, maxAgeSeconds = 86400) {
  if (!initDataRaw || !botToken) throw new Error("Missing initData or TELEGRAM_BOT_TOKEN");
  const params = new URLSearchParams(initDataRaw);
  const hash = params.get("hash");
  if (!hash) throw new Error("Missing Telegram signature hash");

  const pairs: string[] = [];
  for (const [k, v] of params.entries()) {
    if (k !== "hash") pairs.push(`${k}=${v}`);
  }
  pairs.sort();
  const dataCheckString = pairs.join("\n");

  const secretKey = await hmacSha256(new TextEncoder().encode("WebAppData"), botToken);
  const computedHex = toHex(await hmacSha256(new Uint8Array(secretKey), dataCheckString));
  if (!timingSafeEqualStr(computedHex, hash.toLowerCase())) {
    throw new Error("Invalid Telegram initData signature");
  }

  const authDate = Number(params.get("auth_date") ?? "0");
  const nowSec = Math.floor(Date.now() / 1000);
  if (!authDate || nowSec - authDate > maxAgeSeconds) {
    throw new Error("Telegram authentication expired; please reopen from Telegram");
  }

  const userJson = params.get("user");
  if (!userJson) throw new Error("Missing Telegram user payload");
  const user = JSON.parse(userJson) as TelegramUserPayload;
  if (!user || typeof user.id !== "number") throw new Error("Invalid Telegram user ID");

  return { user, startParam: params.get("start_param") ?? undefined };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function buildReferralCode(telegramId: number): string {
  return `DM${telegramId.toString(36).toUpperCase()}`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
    const jwtSecret =
      Deno.env.get("DROPMMS_JWT_SECRET") ||
      Deno.env.get("JWT_SECRET") ||
      Deno.env.get("SUPABASE_JWT_SECRET") ||
      "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!botToken || !jwtSecret || !supabaseUrl || !serviceKey) {
      return jsonResponse(
        { error: "Missing server secret: ensure TELEGRAM_BOT_TOKEN and DROPMMS_JWT_SECRET are configured." },
        500,
      );
    }

    const body = await req.json().catch(() => ({}));
    const initData = typeof body.initData === "string" ? body.initData : "";
    const verified = await verifyTelegramInitData(initData, botToken);
    const tgUser = verified.user;

    const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

    const { data: existing } = await supabase
      .from("profiles")
      .select("*")
      .eq("telegram_id", tgUser.id)
      .maybeSingle();

    if (existing?.is_blocked) {
      return jsonResponse(
        { error: existing.block_reason || "Your account has been suspended by the administrator." },
        403,
      );
    }

    let referredBy: number | null = existing?.referred_by ?? null;
    const rawStart = verified.startParam || (typeof body.startParam === "string" ? body.startParam : "");
    if (!existing && rawStart && rawStart.startsWith("ref_")) {
      const refCode = rawStart.slice(4).trim().toUpperCase();
      if (refCode) {
        const { data: referrer } = await supabase
          .from("profiles")
          .select("telegram_id")
          .eq("referral_code", refCode)
          .maybeSingle();
        if (referrer && referrer.telegram_id !== tgUser.id) referredBy = referrer.telegram_id;
      }
    }

    const referralCode = existing?.referral_code || buildReferralCode(tgUser.id);
    const { data: profile, error: upsertError } = await supabase
      .from("profiles")
      .upsert(
        {
          telegram_id: tgUser.id,
          username: tgUser.username ?? null,
          first_name: tgUser.first_name ?? "",
          last_name: tgUser.last_name ?? null,
          photo_url: tgUser.photo_url ?? null,
          referral_code: referralCode,
          referred_by: referredBy,
          last_seen_at: new Date().toISOString(),
        },
        { onConflict: "telegram_id" },
      )
      .select("*")
      .single();

    if (upsertError || !profile) {
      throw new Error(upsertError?.message ?? "Failed to synchronize Telegram profile");
    }

    if (!existing && referredBy) {
      await supabase.from("referrals").insert({
        referrer_telegram_id: referredBy,
        referred_telegram_id: tgUser.id,
        referral_code: rawStart.slice(4).trim().toUpperCase(),
        reward_status: "pending",
        reward_days: 0,
      });
    }

    if (tgUser.id === OWNER_TELEGRAM_ID) {
      await supabase.from("admin_roles").upsert(
        { telegram_id: OWNER_TELEGRAM_ID, role: "owner", granted_by: OWNER_TELEGRAM_ID },
        { onConflict: "telegram_id" },
      );
    }

    const { data: roleRow } = await supabase
      .from("admin_roles")
      .select("role")
      .eq("telegram_id", tgUser.id)
      .maybeSingle();

    const isOwner = tgUser.id === OWNER_TELEGRAM_ID && roleRow?.role === "owner";

    const { data: activeSub } = await supabase
      .from("subscriptions")
      .select("*, premium_plans(name)")
      .eq("user_telegram_id", tgUser.id)
      .eq("status", "active")
      .gt("expires_at", new Date().toISOString())
      .order("expires_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const nowSec = Math.floor(Date.now() / 1000);
    const accessToken = await signSupabaseJwt(
      {
        aud: "authenticated",
        role: "authenticated",
        sub: `tg_${tgUser.id}`,
        telegram_id: tgUser.id,
        is_owner: isOwner,
        iat: nowSec,
        exp: nowSec + 60 * 60 * 12,
      },
      jwtSecret,
    );

    await supabase.from("activity_logs").insert({
      actor_telegram_id: tgUser.id,
      action: "telegram_auth",
      target_type: "profile",
      target_id: String(tgUser.id),
      metadata: { is_owner: isOwner, has_start_param: Boolean(rawStart) },
    });

    return jsonResponse({
      access_token: accessToken,
      profile,
      is_owner: isOwner,
      active_subscription: activeSub ?? null,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Authentication failed";
    return jsonResponse({ error: message }, 401);
  }
});
