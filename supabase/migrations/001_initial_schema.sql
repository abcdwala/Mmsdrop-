-- DropMms Pro — Production PostgreSQL Schema & Row Level Security
-- Target: Supabase PostgreSQL 15+
-- Owner Telegram ID: 8254788387

create extension if not exists "pgcrypto";

-- ============================================================================
-- 1. PROFILES & ROLES
-- ============================================================================

create table if not exists public.profiles (
  telegram_id bigint primary key,
  username text,
  first_name text not null default '',
  last_name text,
  photo_url text,
  is_blocked boolean not null default false,
  block_reason text,
  age_verified_at timestamptz,
  consent_accepted_at timestamptz,
  referral_code text unique not null,
  referred_by bigint references public.profiles(telegram_id) on delete set null,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_profiles_referral_code on public.profiles(referral_code);
create index if not exists idx_profiles_is_blocked on public.profiles(is_blocked);

create table if not exists public.admin_roles (
  telegram_id bigint primary key,
  role text not null check (role in ('owner', 'moderator')),
  granted_by bigint,
  created_at timestamptz not null default now()
);

-- Seed designated Owner Telegram ID (real owner role configuration, no fake profile data)
insert into public.admin_roles (telegram_id, role, granted_by)
values (8254788387, 'owner', 8254788387)
on conflict (telegram_id) do update set role = 'owner';

-- ============================================================================
-- 2. CATEGORIES & CONTENT
-- ============================================================================

create table if not exists public.categories (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text unique not null,
  description text not null default '',
  icon_name text not null default 'folder',
  sort_order int not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_categories_active_sort on public.categories(is_active, sort_order);

create table if not exists public.content (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 2 and 160),
  description text not null default '',
  category_id uuid references public.categories(id) on delete set null,
  thumbnail_path text not null default '',
  media_path text not null default '',
  media_type text not null default 'video' check (media_type in ('video', 'audio', 'image', 'bundle')),
  duration_seconds int not null default 0 check (duration_seconds >= 0),
  is_featured boolean not null default false,
  is_premium boolean not null default false,
  is_published boolean not null default false,
  rights_verified boolean not null default false,
  consent_documented boolean not null default false,
  view_count bigint not null default 0 check (view_count >= 0),
  created_by bigint not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint chk_publish_requires_compliance check (
    not is_published or (rights_verified and consent_documented)
  )
);

create index if not exists idx_content_published_created on public.content(is_published, created_at desc);
create index if not exists idx_content_category on public.content(category_id, is_published);
create index if not exists idx_content_featured on public.content(is_featured, is_published);
create index if not exists idx_content_trending on public.content(view_count desc) where is_published = true;

-- ============================================================================
-- 3. USER ENGAGEMENT: BOOKMARKS & VIEWING HISTORY
-- ============================================================================

create table if not exists public.bookmarks (
  id uuid primary key default gen_random_uuid(),
  user_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  content_id uuid not null references public.content(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (user_telegram_id, content_id)
);

create index if not exists idx_bookmarks_user on public.bookmarks(user_telegram_id, created_at desc);

create table if not exists public.viewing_history (
  id uuid primary key default gen_random_uuid(),
  user_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  content_id uuid not null references public.content(id) on delete cascade,
  last_position_seconds int not null default 0 check (last_position_seconds >= 0),
  viewed_at timestamptz not null default now(),
  unique (user_telegram_id, content_id)
);

create index if not exists idx_viewing_history_user on public.viewing_history(user_telegram_id, viewed_at desc);

-- ============================================================================
-- 4. PREMIUM PLANS, PAYMENTS, SUBSCRIPTIONS, REFERRALS
-- ============================================================================

create table if not exists public.premium_plans (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 2 and 80),
  description text not null default '',
  price_amount numeric(12, 2) not null check (price_amount >= 0),
  currency text not null default 'USD' check (char_length(currency) between 2 and 10),
  duration_days int not null check (duration_days > 0),
  features jsonb not null default '[]'::jsonb,
  is_active boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.payment_requests (
  id uuid primary key default gen_random_uuid(),
  user_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  plan_id uuid not null references public.premium_plans(id) on delete restrict,
  amount numeric(12, 2) not null check (amount >= 0),
  currency text not null default 'USD',
  payment_method text not null check (char_length(payment_method) between 2 and 80),
  transaction_reference text not null check (char_length(transaction_reference) between 3 and 160),
  proof_path text not null default '',
  notes text not null default '',
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  reviewed_by bigint,
  review_notes text,
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists idx_payment_requests_user on public.payment_requests(user_telegram_id, created_at desc);
create index if not exists idx_payment_requests_status on public.payment_requests(status, created_at desc);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  plan_id uuid references public.premium_plans(id) on delete set null,
  payment_request_id uuid references public.payment_requests(id) on delete set null,
  status text not null default 'active' check (status in ('active', 'expired', 'revoked')),
  starts_at timestamptz not null default now(),
  expires_at timestamptz not null,
  granted_by bigint,
  created_at timestamptz not null default now()
);

create index if not exists idx_subscriptions_user_active on public.subscriptions(user_telegram_id, status, expires_at desc);

create table if not exists public.referrals (
  id uuid primary key default gen_random_uuid(),
  referrer_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  referred_telegram_id bigint not null unique references public.profiles(telegram_id) on delete cascade,
  referral_code text not null,
  reward_status text not null default 'pending' check (reward_status in ('pending', 'credited', 'revoked')),
  reward_days int not null default 0 check (reward_days >= 0),
  created_at timestamptz not null default now(),
  constraint chk_no_self_referral check (referrer_telegram_id <> referred_telegram_id)
);

create index if not exists idx_referrals_referrer on public.referrals(referrer_telegram_id, created_at desc);

-- ============================================================================
-- 5. ANNOUNCEMENTS, SUPPORT, SETTINGS, AUDIT LOGS, REPORTS
-- ============================================================================

create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 2 and 140),
  body text not null,
  severity text not null default 'info' check (severity in ('info', 'promo', 'warning')),
  is_active boolean not null default true,
  starts_at timestamptz not null default now(),
  ends_at timestamptz,
  created_by bigint not null,
  created_at timestamptz not null default now()
);

create table if not exists public.support_requests (
  id uuid primary key default gen_random_uuid(),
  user_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  subject text not null check (char_length(subject) between 3 and 140),
  message text not null check (char_length(message) between 5 and 2000),
  category text not null default 'general' check (category in ('general', 'billing', 'technical', 'rights_removal', 'account')),
  status text not null default 'open' check (status in ('open', 'in_progress', 'resolved', 'closed')),
  admin_reply text,
  replied_by bigint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_support_requests_user on public.support_requests(user_telegram_id, created_at desc);
create index if not exists idx_support_requests_status on public.support_requests(status, created_at desc);

create table if not exists public.app_settings (
  key text primary key,
  value jsonb not null,
  description text not null default '',
  updated_by bigint,
  updated_at timestamptz not null default now()
);

-- Initial empty/policy configuration keys (no fake users or fake content)
insert into public.app_settings (key, value, description)
values
  ('payment_instructions', '{"title":"Configure Payment Methods","instructions":"Owner has not configured live payment instructions yet.","methods":[],"support_handle":""}'::jsonb, 'Owner-configured payment methods and verification instructions'),
  ('referral_settings', '{"enabled":true,"reward_days_referrer":3,"reward_days_referred":1,"min_approved_payments":1}'::jsonb, 'Referral program configuration'),
  ('compliance_policy', '{"minimum_age":18,"require_age_gate":true,"require_consent_ack":true,"removal_sla_hours":24,"rights_contact_email":""}'::jsonb, 'Age verification, rights, and removal SLA settings'),
  ('maintenance_mode', '{"enabled":false,"message":""}'::jsonb, 'Maintenance banner toggle')
on conflict (key) do nothing;

create table if not exists public.activity_logs (
  id uuid primary key default gen_random_uuid(),
  actor_telegram_id bigint not null,
  action text not null,
  target_type text not null,
  target_id text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_activity_logs_created on public.activity_logs(created_at desc);
create index if not exists idx_activity_logs_actor on public.activity_logs(actor_telegram_id, action, created_at desc);

create table if not exists public.content_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_telegram_id bigint not null references public.profiles(telegram_id) on delete cascade,
  content_id uuid not null references public.content(id) on delete cascade,
  reason text not null check (reason in ('copyright_dmca', 'non_consensual', 'underage_safety', 'spam_abuse', 'other')),
  details text not null check (char_length(details) between 5 and 2000),
  removal_requested boolean not null default false,
  status text not null default 'pending' check (status in ('pending', 'reviewing', 'resolved_removed', 'dismissed')),
  reviewed_by bigint,
  resolution_notes text,
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);

create index if not exists idx_content_reports_status on public.content_reports(status, created_at desc);

-- ============================================================================
-- 6. HELPER FUNCTIONS & TRIGGERS
-- ============================================================================

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated on public.profiles;
create trigger trg_profiles_updated before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists trg_categories_updated on public.categories;
create trigger trg_categories_updated before update on public.categories
for each row execute function public.set_updated_at();

drop trigger if exists trg_content_updated on public.content;
create trigger trg_content_updated before update on public.content
for each row execute function public.set_updated_at();

drop trigger if exists trg_plans_updated on public.premium_plans;
create trigger trg_plans_updated before update on public.premium_plans
for each row execute function public.set_updated_at();

drop trigger if exists trg_support_updated on public.support_requests;
create trigger trg_support_updated before update on public.support_requests
for each row execute function public.set_updated_at();

create or replace function public.request_telegram_id()
returns bigint
language sql
stable
as $$
  select nullif(auth.jwt() ->> 'telegram_id', '')::bigint;
$$;

create or replace function public.is_owner_telegram_user()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select (
    public.request_telegram_id() = 8254788387
    and exists (
      select 1 from public.admin_roles ar
      where ar.telegram_id = 8254788387 and ar.role = 'owner'
    )
  );
$$;

create or replace function public.is_active_verified_user()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles p
    where p.telegram_id = public.request_telegram_id()
      and p.is_blocked = false
      and p.age_verified_at is not null
      and p.consent_accepted_at is not null
  );
$$;

-- ============================================================================
-- 7. ROW LEVEL SECURITY (RLS) ON ALL TABLES
-- ============================================================================

alter table public.profiles enable row level security;
alter table public.admin_roles enable row level security;
alter table public.categories enable row level security;
alter table public.content enable row level security;
alter table public.bookmarks enable row level security;
alter table public.viewing_history enable row level security;
alter table public.premium_plans enable row level security;
alter table public.payment_requests enable row level security;
alter table public.subscriptions enable row level security;
alter table public.referrals enable row level security;
alter table public.announcements enable row level security;
alter table public.support_requests enable row level security;
alter table public.app_settings enable row level security;
alter table public.activity_logs enable row level security;
alter table public.content_reports enable row level security;

-- Profiles
drop policy if exists "profiles_select_self_or_owner" on public.profiles;
create policy "profiles_select_self_or_owner" on public.profiles
for select using (
  telegram_id = public.request_telegram_id() or public.is_owner_telegram_user()
);

drop policy if exists "profiles_update_owner_only" on public.profiles;
create policy "profiles_update_owner_only" on public.profiles
for update using (public.is_owner_telegram_user());

-- Admin roles (Owner only)
drop policy if exists "admin_roles_owner_only" on public.admin_roles;
create policy "admin_roles_owner_only" on public.admin_roles
for all using (public.is_owner_telegram_user());

-- Categories
drop policy if exists "categories_select_active" on public.categories;
create policy "categories_select_active" on public.categories
for select using (
  (is_active = true and public.request_telegram_id() is not null) or public.is_owner_telegram_user()
);

drop policy if exists "categories_owner_write" on public.categories;
create policy "categories_owner_write" on public.categories
for all using (public.is_owner_telegram_user());

-- Content (metadata visible only to age-verified non-blocked users; raw playback requires signed URL from content-access function)
drop policy if exists "content_select_verified" on public.content;
create policy "content_select_verified" on public.content
for select using (
  (
    is_published = true
    and rights_verified = true
    and consent_documented = true
    and public.is_active_verified_user()
  )
  or public.is_owner_telegram_user()
);

drop policy if exists "content_owner_write" on public.content;
create policy "content_owner_write" on public.content
for all using (public.is_owner_telegram_user());

-- Bookmarks
drop policy if exists "bookmarks_user_isolation" on public.bookmarks;
create policy "bookmarks_user_isolation" on public.bookmarks
for all using (
  user_telegram_id = public.request_telegram_id()
  and exists (select 1 from public.profiles p where p.telegram_id = public.request_telegram_id() and p.is_blocked = false)
)
with check (
  user_telegram_id = public.request_telegram_id()
);

-- Viewing History
drop policy if exists "history_user_isolation" on public.viewing_history;
create policy "history_user_isolation" on public.viewing_history
for select using (user_telegram_id = public.request_telegram_id() or public.is_owner_telegram_user());

-- Premium Plans
drop policy if exists "plans_select_active" on public.premium_plans;
create policy "plans_select_active" on public.premium_plans
for select using (is_active = true or public.is_owner_telegram_user());

drop policy if exists "plans_owner_write" on public.premium_plans;
create policy "plans_owner_write" on public.premium_plans
for all using (public.is_owner_telegram_user());

-- Payment Requests
drop policy if exists "payments_select_own_or_owner" on public.payment_requests;
create policy "payments_select_own_or_owner" on public.payment_requests
for select using (
  user_telegram_id = public.request_telegram_id() or public.is_owner_telegram_user()
);

-- Subscriptions
drop policy if exists "subscriptions_select_own_or_owner" on public.subscriptions;
create policy "subscriptions_select_own_or_owner" on public.subscriptions
for select using (
  user_telegram_id = public.request_telegram_id() or public.is_owner_telegram_user()
);

-- Referrals
drop policy if exists "referrals_select_participant_or_owner" on public.referrals;
create policy "referrals_select_participant_or_owner" on public.referrals
for select using (
  referrer_telegram_id = public.request_telegram_id()
  or referred_telegram_id = public.request_telegram_id()
  or public.is_owner_telegram_user()
);

-- Announcements
drop policy if exists "announcements_select_active" on public.announcements;
create policy "announcements_select_active" on public.announcements
for select using (
  (is_active = true and starts_at <= now() and (ends_at is null or ends_at > now()))
  or public.is_owner_telegram_user()
);

drop policy if exists "announcements_owner_write" on public.announcements;
create policy "announcements_owner_write" on public.announcements
for all using (public.is_owner_telegram_user());

-- Support Requests
drop policy if exists "support_select_own_or_owner" on public.support_requests;
create policy "support_select_own_or_owner" on public.support_requests
for select using (
  user_telegram_id = public.request_telegram_id() or public.is_owner_telegram_user()
);

drop policy if exists "support_insert_own" on public.support_requests;
create policy "support_insert_own" on public.support_requests
for insert with check (
  user_telegram_id = public.request_telegram_id()
  and exists (select 1 from public.profiles p where p.telegram_id = public.request_telegram_id() and p.is_blocked = false)
);

-- App Settings
drop policy if exists "settings_select_public_keys" on public.app_settings;
create policy "settings_select_public_keys" on public.app_settings
for select using (
  key in ('payment_instructions', 'referral_settings', 'compliance_policy', 'maintenance_mode')
  or public.is_owner_telegram_user()
);

drop policy if exists "settings_owner_write" on public.app_settings;
create policy "settings_owner_write" on public.app_settings
for all using (public.is_owner_telegram_user());

-- Activity Logs (Owner only)
drop policy if exists "activity_logs_owner_only" on public.activity_logs;
create policy "activity_logs_owner_only" on public.activity_logs
for select using (public.is_owner_telegram_user());

-- Content Reports
drop policy if exists "reports_select_own_or_owner" on public.content_reports;
create policy "reports_select_own_or_owner" on public.content_reports
for select using (
  reporter_telegram_id = public.request_telegram_id() or public.is_owner_telegram_user()
);

drop policy if exists "reports_insert_own" on public.content_reports;
create policy "reports_insert_own" on public.content_reports
for insert with check (
  reporter_telegram_id = public.request_telegram_id()
  and exists (
    select 1 from public.profiles p
    where p.telegram_id = public.request_telegram_id()
      and p.is_blocked = false
  )
);

-- ============================================================================
-- 8. STORAGE BUCKETS (PRIVATE BY DEFAULT — SIGNED URLS ONLY)
-- ============================================================================

insert into storage.buckets (id, name, public)
values
  ('thumbnails', 'thumbnails', true),
  ('media-authorized', 'media-authorized', false),
  ('payment-proofs', 'payment-proofs', false)
on conflict (id) do nothing;
