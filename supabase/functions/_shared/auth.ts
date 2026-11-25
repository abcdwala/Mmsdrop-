// Shared Telegram initData HMAC-SHA256 validation, JWT minting, and Owner verification

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

export const OWNER_TELEGRAM_ID = 8254788387;

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-dropmms-session",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export interface TelegramUserPayload {
  id: number;
  username?: string;
  first_name: string;
  last_name?: string;
  photo_url?: string;
}

export interface VerifiedInitData {
  user: TelegramUserPayload;
  authDate: number;
  startParam?: string;
}

function toHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function timingSafeEqualHex(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
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

export async function verifyTelegramInitData(
  initDataRaw: string,
  botToken: string,
  maxAgeSeconds = 86400,
): Promise<VerifiedInitData> {
  if (!initDataRaw || !botToken) {
    throw new Error("Missing initData or TELEGRAM_BOT_TOKEN");
  }

  const params = new URLSearchParams(initDataRaw);
  const hash = params.get("hash");
  if (!hash) {
    throw new Error("Missing Telegram signature hash");
  }

  const pairs: string[] = [];
  for (const [k, v] of params.entries()) {
    if (k !== "hash") {
      pairs.push(`${k}=${v}`);
    }
  }
  pairs.sort();
  const dataCheckString = pairs.join("\n");

  const secretKeyBuffer = await hmacSha256(
    new TextEncoder().encode("WebAppData"),
    botToken,
  );
  const computedHashBuffer = await hmacSha256(
    new Uint8Array(secretKeyBuffer),
    dataCheckString,
  );
  const computedHex = toHex(computedHashBuffer);

  if (!timingSafeEqualHex(computedHex, hash.toLowerCase())) {
    throw new Error("Invalid Telegram initData signature");
  }

  const authDate = Number(params.get("auth_date") ?? "0");
  const nowSec = Math.floor(Date.now() / 1000);
  if (!authDate || nowSec - authDate > maxAgeSeconds) {
    throw new Error("Telegram authentication expired; please reopen from Telegram");
  }

  const userJson = params.get("user");
  if (!userJson) {
    throw new Error("Missing Telegram user payload");
  }
  const user = JSON.parse(userJson) as TelegramUserPayload;
  if (!user || typeof user.id !== "number") {
    throw new Error("Invalid Telegram user ID");
  }

  return {
    user,
    authDate,
    startParam: params.get("start_param") ?? undefined,
  };
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

export async function signSupabaseJwt(
  payload: Record<string, unknown>,
  jwtSecret: string,
): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const enc = new TextEncoder();
  const headerPart = base64UrlEncode(enc.encode(JSON.stringify(header)));
  const payloadPart = base64UrlEncode(enc.encode(JSON.stringify(payload)));
  const signingInput = `${headerPart}.${payloadPart}`;
  const sig = await hmacSha256(enc.encode(jwtSecret), signingInput);
  return `${signingInput}.${base64UrlEncode(new Uint8Array(sig))}`;
}

export async function verifySessionJwt(
  token: string,
  jwtSecret: string,
): Promise<{ telegram_id: number; exp: number }> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Malformed session token");
  const [headerPart, payloadPart, sigPart] = parts;
  const signingInput = `${headerPart}.${payloadPart}`;
  const expectedSig = base64UrlEncode(
    new Uint8Array(await hmacSha256(new TextEncoder().encode(jwtSecret), signingInput)),
  );
  if (!timingSafeEqualHex(expectedSig, sigPart)) {
    throw new Error("Invalid session token signature");
  }
  const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(payloadPart)));
  const nowSec = Math.floor(Date.now() / 1000);
  if (!payload.exp || payload.exp < nowSec) {
    throw new Error("Session expired");
  }
  const tid = Number(payload.telegram_id);
  if (!tid) throw new Error("Missing telegram_id in session");
  return { telegram_id: tid, exp: Number(payload.exp) };
}

export function getServiceClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !serviceKey) {
    throw new Error("Server missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(url, serviceKey, { auth: { persistSession: false } });
}

export async function requireAuthenticatedUser(req: Request) {
  const jwtSecret = Deno.env.get("SUPABASE_JWT_SECRET") ?? "";
  if (!jwtSecret) throw new Error("Server missing SUPABASE_JWT_SECRET");

  const sessionHeader = req.headers.get("x-dropmms-session") ??
    req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  if (!sessionHeader) throw new Error("Authentication required");

  const claims = await verifySessionJwt(sessionHeader, jwtSecret);
  const supabase = getServiceClient();

  const { data: profile, error } = await supabase
    .from("profiles")
    .select("*")
    .eq("telegram_id", claims.telegram_id)
    .single();

  if (error || !profile) throw new Error("Profile not found");
  if (profile.is_blocked) throw new Error("Account is blocked");

  return { supabase, profile, telegramId: claims.telegram_id };
}

export async function requireOwner(req: Request) {
  const { supabase, profile, telegramId } = await requireAuthenticatedUser(req);
  if (telegramId !== OWNER_TELEGRAM_ID) {
    throw new Error("Forbidden: Owner authorization required");
  }
  const { data: roleRow } = await supabase
    .from("admin_roles")
    .select("role")
    .eq("telegram_id", OWNER_TELEGRAM_ID)
    .eq("role", "owner")
    .maybeSingle();

  if (!roleRow) {
    throw new Error("Forbidden: Owner role not active in admin_roles");
  }
  return { supabase, profile, telegramId };
}

export async function checkRateLimit(
  supabase: ReturnType<typeof getServiceClient>,
  actorTelegramId: number,
  action: string,
  maxPerMinute: number,
) {
  const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
  const { count } = await supabase
    .from("activity_logs")
    .select("id", { count: "exact", head: true })
    .eq("actor_telegram_id", actorTelegramId)
    .eq("action", action)
    .gte("created_at", oneMinuteAgo);

  if ((count ?? 0) >= maxPerMinute) {
    throw new Error("Rate limit exceeded. Please wait a moment and try again.");
  }
}

export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
