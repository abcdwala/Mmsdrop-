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

async function requireOwner(req: Request) {
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
  if (claims.telegram_id !== OWNER_TELEGRAM_ID) throw new Error("Forbidden: Owner authorization required");

  const supabase = createClient(url, serviceKey, { auth: { persistSession: false } });
  const { data: profile, error } = await supabase
    .from("profiles")
    .select("*")
    .eq("telegram_id", claims.telegram_id)
    .single();
  if (error || !profile) throw new Error("Profile not found");
  if (profile.is_blocked) throw new Error("Account is blocked");

  const { data: roleRow } = await supabase
    .from("admin_roles")
    .select("role")
    .eq("telegram_id", OWNER_TELEGRAM_ID)
    .eq("role", "owner")
    .maybeSingle();
  if (!roleRow) throw new Error("Forbidden: Owner role not active in admin_roles");

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
    const { supabase, telegramId: ownerId } = await requireOwner(req);
    const body = await req.json();
    const action = String(body.action ?? "");

    async function audit(targetType: string, targetId: string | null, metadata: Record<string, unknown> = {}) {
      await supabase.from("activity_logs").insert({
        actor_telegram_id: ownerId,
        action: `admin_${action}`,
        target_type: targetType,
        target_id: targetId,
        metadata,
      });
    }

    switch (action) {
      case "dashboard_stats": {
        const [
          usersRes,
          blockedRes,
          contentRes,
          publishedRes,
          pendingPaymentsRes,
          activeSubsRes,
          openReportsRes,
          openSupportRes,
        ] = await Promise.all([
          supabase.from("profiles").select("telegram_id", { count: "exact", head: true }),
          supabase.from("profiles").select("telegram_id", { count: "exact", head: true }).eq("is_blocked", true),
          supabase.from("content").select("id", { count: "exact", head: true }),
          supabase.from("content").select("id", { count: "exact", head: true }).eq("is_published", true),
          supabase.from("payment_requests").select("id", { count: "exact", head: true }).eq("status", "pending"),
          supabase.from("subscriptions").select("id", { count: "exact", head: true })
            .eq("status", "active").gt("expires_at", new Date().toISOString()),
          supabase.from("content_reports").select("id", { count: "exact", head: true }).eq("status", "pending"),
          supabase.from("support_requests").select("id", { count: "exact", head: true }).in("status", ["open", "in_progress"]),
        ]);
        return jsonResponse({
          stats: {
            total_users: usersRes.count ?? 0,
            blocked_users: blockedRes.count ?? 0,
            total_content: contentRes.count ?? 0,
            published_content: publishedRes.count ?? 0,
            pending_payments: pendingPaymentsRes.count ?? 0,
            active_subscriptions: activeSubsRes.count ?? 0,
            open_reports: openReportsRes.count ?? 0,
            open_support: openSupportRes.count ?? 0,
          },
        });
      }

      case "list_users": {
        const { data, error } = await supabase
          .from("profiles")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ users: data ?? [] });
      }

      case "set_user_blocked": {
        const targetTid = Number(body.telegram_id);
        const isBlocked = Boolean(body.is_blocked);
        const reason = String(body.reason ?? "").trim().slice(0, 300);
        if (!targetTid || targetTid === ownerId) return jsonResponse({ error: "Cannot block owner or invalid user" }, 400);
        const { data, error } = await supabase
          .from("profiles")
          .update({ is_blocked: isBlocked, block_reason: isBlocked ? reason || "Blocked by Owner" : null })
          .eq("telegram_id", targetTid)
          .select("*")
          .single();
        if (error) throw new Error(error.message);
        await audit("profile", String(targetTid), { is_blocked: isBlocked, reason });
        return jsonResponse({ user: data });
      }

      case "create_upload_url": {
        const bucket = body.bucket === "thumbnails" ? "thumbnails" : "media-authorized";
        const ext = String(body.file_ext ?? "bin").replace(/[^a-zA-Z0-9]/g, "").slice(0, 10) || "bin";
        const path = `${new Date().toISOString().slice(0, 10)}/${crypto.randomUUID()}.${ext}`;
        const { data, error } = await supabase.storage.from(bucket).createSignedUploadUrl(path);
        if (error || !data) throw new Error(error?.message ?? "Failed to sign upload URL");
        const publicUrl = bucket === "thumbnails"
          ? supabase.storage.from("thumbnails").getPublicUrl(path).data.publicUrl
          : path;
        return jsonResponse({ bucket, path, public_or_storage_path: publicUrl, signed_url: data.signedUrl, token: data.token });
      }

      case "upsert_content": {
        const c = body.content ?? {};
        const payload = {
          ...(c.id ? { id: c.id } : {}),
          title: String(c.title ?? "").trim(),
          description: String(c.description ?? "").trim(),
          category_id: c.category_id || null,
          thumbnail_path: String(c.thumbnail_path ?? "").trim(),
          media_path: String(c.media_path ?? "").trim(),
          media_type: ["video", "audio", "image", "bundle"].includes(c.media_type) ? c.media_type : "video",
          duration_seconds: Math.max(0, Number(c.duration_seconds ?? 0) || 0),
          is_featured: Boolean(c.is_featured),
          is_premium: Boolean(c.is_premium),
          is_published: Boolean(c.is_published),
          rights_verified: Boolean(c.rights_verified),
          consent_documented: Boolean(c.consent_documented),
          created_by: ownerId,
        };
        if (payload.is_published && (!payload.rights_verified || !payload.consent_documented)) {
          return jsonResponse({ error: "Cannot publish content until rights_verified and consent_documented are confirmed." }, 400);
        }
        const { data, error } = await supabase.from("content").upsert(payload).select("*").single();
        if (error) throw new Error(error.message);
        await audit("content", data.id, { title: data.title, is_published: data.is_published });
        return jsonResponse({ content: data });
      }

      case "delete_content": {
        const id = String(body.id ?? "");
        const { error } = await supabase.from("content").delete().eq("id", id);
        if (error) throw new Error(error.message);
        await audit("content", id);
        return jsonResponse({ deleted: true });
      }

      case "upsert_category": {
        const cat = body.category ?? {};
        const name = String(cat.name ?? "").trim();
        const slug = String(cat.slug ?? name.toLowerCase().replace(/[^a-z0-9]+/g, "-")).replace(/^-|-$/g, "");
        const payload = {
          ...(cat.id ? { id: cat.id } : {}),
          name,
          slug,
          description: String(cat.description ?? "").trim(),
          icon_name: String(cat.icon_name ?? "folder").trim(),
          sort_order: Number(cat.sort_order ?? 0) || 0,
          is_active: cat.is_active !== false,
        };
        const { data, error } = await supabase.from("categories").upsert(payload).select("*").single();
        if (error) throw new Error(error.message);
        await audit("category", data.id, { name: data.name });
        return jsonResponse({ category: data });
      }

      case "delete_category": {
        const id = String(body.id ?? "");
        const { error } = await supabase.from("categories").delete().eq("id", id);
        if (error) throw new Error(error.message);
        await audit("category", id);
        return jsonResponse({ deleted: true });
      }

      case "upsert_plan": {
        const p = body.plan ?? {};
        const payload = {
          ...(p.id ? { id: p.id } : {}),
          name: String(p.name ?? "").trim(),
          description: String(p.description ?? "").trim(),
          price_amount: Number(p.price_amount ?? 0),
          currency: String(p.currency ?? "USD").trim().toUpperCase(),
          duration_days: Math.max(1, Number(p.duration_days ?? 30)),
          features: Array.isArray(p.features) ? p.features : [],
          is_active: p.is_active !== false,
          sort_order: Number(p.sort_order ?? 0) || 0,
        };
        const { data, error } = await supabase.from("premium_plans").upsert(payload).select("*").single();
        if (error) throw new Error(error.message);
        await audit("premium_plan", data.id, { name: data.name });
        return jsonResponse({ plan: data });
      }

      case "delete_plan": {
        const id = String(body.id ?? "");
        const { error } = await supabase.from("premium_plans").delete().eq("id", id);
        if (error) throw new Error(error.message);
        await audit("premium_plan", id);
        return jsonResponse({ deleted: true });
      }

      case "grant_subscription": {
        const userTid = Number(body.user_telegram_id);
        const days = Math.max(1, Number(body.duration_days ?? 30));
        const planId = body.plan_id || null;
        const now = new Date();
        const expiresAt = new Date(now.getTime() + days * 86_400_000);
        const { data, error } = await supabase.from("subscriptions").insert({
          user_telegram_id: userTid,
          plan_id: planId,
          status: "active",
          starts_at: now.toISOString(),
          expires_at: expiresAt.toISOString(),
          granted_by: ownerId,
        }).select("*").single();
        if (error) throw new Error(error.message);
        await audit("subscription", data.id, { user_telegram_id: userTid, days });
        return jsonResponse({ subscription: data });
      }

      case "revoke_subscription": {
        const id = String(body.subscription_id ?? "");
        const { data, error } = await supabase
          .from("subscriptions")
          .update({ status: "revoked" })
          .eq("id", id)
          .select("*")
          .single();
        if (error) throw new Error(error.message);
        await audit("subscription", id);
        return jsonResponse({ subscription: data });
      }

      case "upsert_announcement": {
        const a = body.announcement ?? {};
        const payload = {
          ...(a.id ? { id: a.id } : {}),
          title: String(a.title ?? "").trim(),
          body: String(a.body ?? "").trim(),
          severity: ["info", "promo", "warning"].includes(a.severity) ? a.severity : "info",
          is_active: a.is_active !== false,
          created_by: ownerId,
        };
        const { data, error } = await supabase.from("announcements").upsert(payload).select("*").single();
        if (error) throw new Error(error.message);
        await audit("announcement", data.id, { title: data.title });
        return jsonResponse({ announcement: data });
      }

      case "delete_announcement": {
        const id = String(body.id ?? "");
        const { error } = await supabase.from("announcements").delete().eq("id", id);
        if (error) throw new Error(error.message);
        await audit("announcement", id);
        return jsonResponse({ deleted: true });
      }

      case "update_setting": {
        const key = String(body.key ?? "").trim();
        if (!key) return jsonResponse({ error: "Setting key required" }, 400);
        const { data, error } = await supabase
          .from("app_settings")
          .upsert({
            key,
            value: body.value ?? {},
            description: String(body.description ?? ""),
            updated_by: ownerId,
            updated_at: new Date().toISOString(),
          })
          .select("*")
          .single();
        if (error) throw new Error(error.message);
        await audit("app_setting", key);
        return jsonResponse({ setting: data });
      }

      case "resolve_report": {
        const id = String(body.report_id ?? "");
        const status = ["reviewing", "resolved_removed", "dismissed"].includes(body.status) ? body.status : "dismissed";
        const notes = String(body.resolution_notes ?? "").trim().slice(0, 500);
        const { data: report, error } = await supabase
          .from("content_reports")
          .update({ status, reviewed_by: ownerId, resolution_notes: notes || null, resolved_at: new Date().toISOString() })
          .eq("id", id)
          .select("*")
          .single();
        if (error) throw new Error(error.message);
        if (status === "resolved_removed" && report?.content_id) {
          await supabase.from("content").update({ is_published: false }).eq("id", report.content_id);
        }
        await audit("content_report", id, { status, notes });
        return jsonResponse({ report });
      }

      case "reply_support": {
        const id = String(body.support_id ?? "");
        const status = ["open", "in_progress", "resolved", "closed"].includes(body.status) ? body.status : "resolved";
        const reply = String(body.admin_reply ?? "").trim().slice(0, 2000);
        const { data, error } = await supabase
          .from("support_requests")
          .update({ status, admin_reply: reply, replied_by: ownerId })
          .eq("id", id)
          .select("*")
          .single();
        if (error) throw new Error(error.message);
        await audit("support_request", id, { status });
        return jsonResponse({ support_request: data });
      }

      case "list_payments": {
        const { data, error } = await supabase
          .from("payment_requests")
          .select("*, premium_plans(name, duration_days)")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ payments: data ?? [] });
      }

      case "list_subscriptions": {
        const { data, error } = await supabase
          .from("subscriptions")
          .select("*, premium_plans(name)")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ subscriptions: data ?? [] });
      }

      case "list_reports": {
        const { data, error } = await supabase
          .from("content_reports")
          .select("*, content(title)")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ reports: data ?? [] });
      }

      case "list_support": {
        const { data, error } = await supabase
          .from("support_requests")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ support_requests: data ?? [] });
      }

      case "list_activity_logs": {
        const { data, error } = await supabase
          .from("activity_logs")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw new Error(error.message);
        return jsonResponse({ logs: data ?? [] });
      }

      default:
        return jsonResponse({ error: `Unknown admin action: ${action}` }, 400);
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : "Admin action failed";
    return jsonResponse({ error: message }, message.startsWith("Forbidden") ? 403 : 400);
  }
});
