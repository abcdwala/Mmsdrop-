# DropMms Pro — Deployment & Configuration Guide

This project ships with **zero mock data** and **zero hardcoded credentials**. Follow these steps to connect your real Supabase backend and Telegram Bot.

## 1. Create Supabase Project & Apply Database Schema
1. Create a Supabase project at https://supabase.com/dashboard.
2. Open the **SQL Editor** and run `supabase/migrations/001_initial_schema.sql` to create all 15 PostgreSQL tables, RLS policies, Owner role (`8254788387`), triggers, and Storage buckets (`thumbnails`, `media-authorized`, `payment-proofs`).

## 2. Configure Server-Side Secrets & Deploy Edge Functions (Browser-Only / Mobile-Friendly)
Supabase Edge Functions automatically inject `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`, and block adding custom secrets starting with `SUPABASE_`.

1. In **Supabase Dashboard → Edge Functions → Secrets**, add:
   - `TELEGRAM_BOT_TOKEN` = your `@BotFather` bot token
   - `OWNER_TELEGRAM_ID` = `8254788387`
   - `DROPMMS_JWT_SECRET` = your Supabase **Legacy JWT Secret** (found under **Project Settings → JWT Keys / API → JWT Settings**)
2. **Option A — Deploy directly in Supabase Dashboard (No Terminal)**:
   - Open **Edge Functions → Deploy a new function → Via Editor**.
   - Deploy each of the 5 self-contained files (`telegram-auth`, `content-access`, `payment-workflow`, `admin-api`, `user-actions`) from `supabase/functions/<name>/index.ts`.
   - Disable **Verify JWT with legacy secret** (gateway JWT check) on each of the 5 functions because they verify Telegram `initData` HMAC signatures and `x-dropmms-session` HS256 JWTs inside each function.
3. **Option B — One-Tap GitHub Actions (No Terminal)**:
   - Push this project to GitHub, add `SUPABASE_ACCESS_TOKEN` and `SUPABASE_PROJECT_REF` in **GitHub Repo → Settings → Secrets and variables → Actions**, then run **Deploy Supabase Edge Functions** from the **Actions** tab.

## 3. Configure Android App Secrets (AI Studio Secrets Panel)
Open **Settings -> Secrets** in AI Studio and set:
- `SUPABASE_URL`: `https://<REF>.supabase.co`
- `SUPABASE_ANON_KEY`: Your Supabase **anon / public** key
- `TELEGRAM_BOT_USERNAME`: Your Telegram Bot username (without `@`)

**Never** place `SUPABASE_SERVICE_ROLE_KEY`, `DROPMMS_JWT_SECRET`, or `TELEGRAM_BOT_TOKEN` in Android Secrets or `.env`.

## 4. Deploy the Telegram Mini App Web Client (`webapp/`)
1. Copy `webapp/config.example.js` to `webapp/config.js` and fill `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `TELEGRAM_BOT_USERNAME`.
2. Host `webapp/` on any HTTPS static host (Vercel, Cloudflare Pages, Netlify).
3. In `@BotFather`, set the Web App / Menu Button URL to your HTTPS deployment URL.
4. When Telegram user `8254788387` opens the Mini App (or authenticates via Telegram `initData`), the server verifies the HMAC signature, confirms the `owner` role in `admin_roles`, and unlocks the Owner Admin Panel.
