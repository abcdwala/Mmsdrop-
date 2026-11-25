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

async function requireOwner(req: Request) {
  const ctx = await requireAuthenticatedUser(req);
  if (ctx.telegramId !== OWNER_TELEGRAM_ID) throw new Error("Forbidden: Owner authorization required");
  const { data: roleRow } = await ctx.supabase
    .from("admin_roles")
    .select("role")
    .eq("telegram_id", OWNER_TELEGRAM_ID)
    .eq("role", "owner")
    .maybeSingle();
  if (!roleRow) throw new Error("Forbidden: Owner role not active in admin_roles");
  return ctx;
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
    const body = await req.json();
    const action = body.action as string;

    if (action === "create_proof_upload_url") {
      const { supabase, telegramId } = await requireAuthenticatedUser(req);
      const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
      const { count } = await supabase
        .from("activity_logs")
        .select("id", { count: "exact", head: true })
        .eq("actor_telegram_id", telegramId)
        .eq("action", "proof_upload")
        .gte("created_at", oneMinuteAgo);
      if ((count ?? 0) >= 10) throw new Error("Rate limit exceeded");

      const ext = String(body.fileExt || "jpg").replace(/[^a-zA-Z0-9]/g, "").slice(0, 8) || "jpg";
      const objectPath = `${telegramId}/${crypto.randomUUID()}.${ext}`;
      const { data, error } = await supabase.storage.from("payment-proofs").createSignedUploadUrl(objectPath);
      if (error || !data) throw new Error(error?.message ?? "Failed to create proof upload URL");
      return jsonResponse({ path: objectPath, signed_url: data.signedUrl, token: data.token });
    }

    if (action === "submit_payment") {
      const { supabase, telegramId } = await requireAuthenticatedUser(req);
      const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
      const { count } = await supabase
        .from("activity_logs")
        .select("id", { count: "exact", head: true })
        .eq("actor_telegram_id", telegramId)
        .eq("action", "payment_submit")
        .gte("created_at", oneMinuteAgo);
      if ((count ?? 0) >= 5) throw new Error("Rate limit exceeded");

      const planId = String(body.plan_id ?? "");
      const paymentMethod = String(body.payment_method ?? "").trim();
      const transactionRef = String(body.transaction_reference ?? "").trim();
      const proofPath = String(body.proof_path ?? "").trim();
      const notes = String(body.notes ?? "").trim().slice(0, 500);

      if (!planId || paymentMethod.length < 2 || transactionRef.length < 3) {
        return jsonResponse({ error: "Plan, payment method, and transaction reference (min 3 chars) are required." }, 400);
      }

      const { data: plan, error: planErr } = await supabase
        .from("premium_plans")
        .select("*")
        .eq("id", planId)
        .eq("is_active", true)
        .single();
      if (planErr || !plan) return jsonResponse({ error: "Selected plan is unavailable" }, 404);

      const { data: payment, error: insertErr } = await supabase
        .from("payment_requests")
        .insert({
          user_telegram_id: telegramId,
          plan_id: plan.id,
          amount: plan.price_amount,
          currency: plan.currency,
          payment_method: paymentMethod.slice(0, 80),
          transaction_reference: transactionRef.slice(0, 160),
          proof_path: proofPath,
          notes,
          status: "pending",
        })
        .select("*")
        .single();
      if (insertErr || !payment) throw new Error(insertErr?.message ?? "Failed to submit payment request");

      await supabase.from("activity_logs").insert({
        actor_telegram_id: telegramId,
        action: "payment_submit",
        target_type: "payment_request",
        target_id: payment.id,
        metadata: { plan_id: plan.id, amount: plan.price_amount, currency: plan.currency },
      });

      return jsonResponse({ payment_request: payment });
    }

    if (action === "review_payment") {
      const { supabase, telegramId: ownerId } = await requireOwner(req);
      const paymentId = String(body.payment_id ?? "");
      const decision = body.decision === "approved" ? "approved" : body.decision === "rejected" ? "rejected" : "";
      const reviewNotes = String(body.review_notes ?? "").trim().slice(0, 500);
      if (!paymentId || !decision) {
        return jsonResponse({ error: "payment_id and valid decision ('approved'|'rejected') required" }, 400);
      }

      const { data: reqRow, error: fetchErr } = await supabase
        .from("payment_requests")
        .select("*, premium_plans(*)")
        .eq("id", paymentId)
        .single();
      if (fetchErr || !reqRow) return jsonResponse({ error: "Payment request not found" }, 404);
      if (reqRow.status !== "pending") return jsonResponse({ error: `Payment request is already ${reqRow.status}` }, 409);

      const { data: updated, error: updErr } = await supabase
        .from("payment_requests")
        .update({
          status: decision,
          reviewed_by: ownerId,
          review_notes: reviewNotes || null,
          reviewed_at: new Date().toISOString(),
        })
        .eq("id", paymentId)
        .select("*")
        .single();
      if (updErr || !updated) throw new Error(updErr?.message ?? "Failed to update payment request");

      let subscription = null;
      if (decision === "approved" && reqRow.premium_plans) {
        const durationDays = Number(reqRow.premium_plans.duration_days ?? 30);
        const now = new Date();
        const expiresAt = new Date(now.getTime() + durationDays * 86_400_000);

        const { data: sub, error: subErr } = await supabase
          .from("subscriptions")
          .insert({
            user_telegram_id: reqRow.user_telegram_id,
            plan_id: reqRow.plan_id,
            payment_request_id: reqRow.id,
            status: "active",
            starts_at: now.toISOString(),
            expires_at: expiresAt.toISOString(),
            granted_by: ownerId,
          })
          .select("*")
          .single();
        if (subErr) throw new Error(subErr.message);
        subscription = sub;

        const { data: refRow } = await supabase
          .from("referrals")
          .select("*")
          .eq("referred_telegram_id", reqRow.user_telegram_id)
          .eq("reward_status", "pending")
          .maybeSingle();
        if (refRow) {
          const { data: refSettings } = await supabase
            .from("app_settings")
            .select("value")
            .eq("key", "referral_settings")
            .maybeSingle();
          const rewardDays = Number(refSettings?.value?.reward_days_referrer ?? 3);
          if (refSettings?.value?.enabled !== false && rewardDays > 0) {
            await supabase
              .from("referrals")
              .update({ reward_status: "credited", reward_days: rewardDays })
              .eq("id", refRow.id);
            await supabase.from("subscriptions").insert({
              user_telegram_id: refRow.referrer_telegram_id,
              plan_id: reqRow.plan_id,
              status: "active",
              starts_at: now.toISOString(),
              expires_at: new Date(now.getTime() + rewardDays * 86_400_000).toISOString(),
              granted_by: ownerId,
            });
          }
        }
      }

      await supabase.from("activity_logs").insert({
        actor_telegram_id: ownerId,
        action: `payment_${decision}`,
        target_type: "payment_request",
        target_id: paymentId,
        metadata: { user_telegram_id: reqRow.user_telegram_id, review_notes: reviewNotes },
      });

      return jsonResponse({ payment_request: updated, subscription });
    }

    return jsonResponse({ error: "Unsupported payment action" }, 400);
  } catch (err) {
    const message = err instanceof Error ? err.message : "Payment workflow error";
    return jsonResponse({ error: message }, 400);
  }
});
