(() => {
  const OWNER_TELEGRAM_ID = 8254788387;
  const cfg = window.DROPMMS_CONFIG || {};
  const SUPABASE_URL = (cfg.SUPABASE_URL || "").replace(/\/+$/, "");
  const SUPABASE_ANON_KEY = cfg.SUPABASE_ANON_KEY || "";
  const BOT_USERNAME = (cfg.TELEGRAM_BOT_USERNAME || "").replace(/^@/, "");

  const tg = window.Telegram && window.Telegram.WebApp ? window.Telegram.WebApp : null;

  const state = {
    configured: Boolean(SUPABASE_URL && SUPABASE_ANON_KEY),
    accessToken: null,
    profile: null,
    isOwner: false,
    activeSubscription: null,
    tab: "home",
    detailContentId: null,
    detailFallbackItem: null,
    searchQuery: "",
    selectedCategoryId: "",
    sortBy: "latest",
    page: 0,
    categories: [],
    contentList: [],
    announcements: [],
    plans: [],
    payments: [],
    bookmarks: [],
    history: [],
    referrals: [],
    supportTickets: [],
    settings: {},
    adminTab: "stats",
    adminLoading: false,
    adminData: {
      stats: null,
      users: [],
      content: [],
      categories: [],
      plans: [],
      payments: [],
      subscriptions: [],
      reports: [],
      support: [],
      logs: [],
    },
  };

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function showToast(msg) {
    const t = document.getElementById("toast");
    t.textContent = msg;
    t.classList.remove("hidden");
    clearTimeout(showToast._timer);
    showToast._timer = setTimeout(() => t.classList.add("hidden"), 3400);
  }

  function isSafeHttpUrl(url) {
    try {
      const u = new URL(url);
      return u.protocol === "https:";
    } catch {
      return false;
    }
  }

  function isSubscriptionActive(sub) {
    if (!sub || sub.status !== "active") return false;
    if (!sub.expires_at) return true;
    return Date.parse(sub.expires_at) > Date.now();
  }

  async function restFetch(path, options = {}) {
    if (!state.configured) throw new Error("Supabase configuration missing (SUPABASE_URL / SUPABASE_ANON_KEY).");
    const headers = {
      apikey: SUPABASE_ANON_KEY,
      "Content-Type": "application/json",
      ...(state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {}),
      ...(options.headers || {}),
    };
    const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...options, headers });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error((data && (data.message || data.error)) || `REST error ${res.status}`);
    return data;
  }

  async function fnFetch(fnName, payload = {}) {
    if (!state.configured) throw new Error("Supabase configuration missing.");
    const headers = {
      apikey: SUPABASE_ANON_KEY,
      "Content-Type": "application/json",
      ...(state.accessToken
        ? { Authorization: `Bearer ${state.accessToken}`, "x-dropmms-session": state.accessToken }
        : { Authorization: `Bearer ${SUPABASE_ANON_KEY}` }),
    };
    const res = await fetch(`${SUPABASE_URL}/functions/v1/${fnName}`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `Function ${fnName} failed (${res.status})`);
    return data;
  }

  if (tg && tg.BackButton) {
    tg.BackButton.onClick(() => {
      if (state.detailContentId) {
        state.detailContentId = null;
        state.detailFallbackItem = null;
        render();
      }
    });
  }

  async function authenticateTelegram() {
    const subEl = document.getElementById("auth-subtitle");
    if (!state.configured) {
      subEl.textContent = "Backend configuration required";
      renderConfigBanner();
      render();
      return;
    }

    const initData = tg && tg.initData ? tg.initData : "";
    if (!initData) {
      subEl.textContent = "Open inside Telegram Mini App to authenticate";
      render();
      return;
    }

    try {
      const startParam = tg?.initDataUnsafe?.start_param || "";
      const res = await fnFetch("telegram-auth", { initData, startParam });
      state.accessToken = res.access_token;
      state.profile = res.profile;
      state.isOwner = Boolean(res.is_owner && res.profile?.telegram_id === OWNER_TELEGRAM_ID);
      state.activeSubscription = isSubscriptionActive(res.active_subscription) ? res.active_subscription : null;
      subEl.textContent = res.profile?.username ? `@${res.profile.username}` : `TG #${res.profile?.telegram_id}`;
      if (state.isOwner) {
        document.getElementById("nav-admin-btn").classList.remove("hidden");
        const badge = document.getElementById("role-badge");
        badge.textContent = "OWNER";
        badge.classList.remove("hidden");
      }
      await loadInitialData();
    } catch (e) {
      subEl.textContent = "Authentication error";
      showToast(e.message);
    }
    render();
  }

  async function loadInitialData() {
    if (!state.configured || !state.accessToken) return;
    const [cats, anns, plans, settingsRows] = await Promise.all([
      restFetch("categories?is_active=eq.true&order=sort_order.asc").catch(() => []),
      restFetch("announcements?is_active=eq.true&order=created_at.desc").catch(() => []),
      restFetch("premium_plans?is_active=eq.true&order=sort_order.asc").catch(() => []),
      restFetch("app_settings?select=key,value").catch(() => []),
    ]);
    state.categories = cats || [];
    state.announcements = anns || [];
    state.plans = plans || [];
    state.settings = Object.fromEntries((settingsRows || []).map((r) => [r.key, r.value]));
    await loadCatalog();
    await loadUserCollections();
  }

  async function loadCatalog() {
    if (!state.configured || !state.accessToken) return;
    const order = state.sortBy === "popular" ? "view_count.desc" : "created_at.desc";
    const limit = 12;
    const offset = state.page * limit;
    let query = `content?is_published=eq.true&order=${order}&limit=${limit}&offset=${offset}`;
    if (state.selectedCategoryId) query += `&category_id=eq.${encodeURIComponent(state.selectedCategoryId)}`;
    const cleanQuery = state.searchQuery.trim().replace(/[,()*]/g, " ").trim();
    if (cleanQuery) {
      const q = encodeURIComponent(`*${cleanQuery}*`);
      query += `&or=(title.ilike.${q},description.ilike.${q})`;
    }
    state.contentList = (await restFetch(query).catch(() => [])) || [];
  }

  async function loadUserCollections() {
    if (!state.configured || !state.accessToken || !state.profile) return;
    const tid = state.profile.telegram_id;
    const [bms, hist, pays, subs, refs, tickets] = await Promise.all([
      restFetch(`bookmarks?user_telegram_id=eq.${tid}&select=*,content(*)&order=created_at.desc`).catch(() => []),
      restFetch(`viewing_history?user_telegram_id=eq.${tid}&select=*,content(*)&order=viewed_at.desc`).catch(() => []),
      restFetch(`payment_requests?user_telegram_id=eq.${tid}&select=*,premium_plans(name)&order=created_at.desc`).catch(() => []),
      restFetch(`subscriptions?user_telegram_id=eq.${tid}&select=*,premium_plans(name)&order=expires_at.desc`).catch(() => []),
      restFetch(`referrals?referrer_telegram_id=eq.${tid}&order=created_at.desc`).catch(() => []),
      restFetch(`support_requests?user_telegram_id=eq.${tid}&order=created_at.desc`).catch(() => []),
    ]);
    state.bookmarks = bms || [];
    state.history = hist || [];
    state.payments = pays || [];
    state.activeSubscription = (subs || []).find(isSubscriptionActive) || null;
    state.referrals = refs || [];
    state.supportTickets = tickets || [];
  }

  function renderConfigBanner() {
    const banner = document.getElementById("config-banner");
    if (state.configured) {
      banner.classList.add("hidden");
      return;
    }
    banner.textContent = "Configuration required: Set SUPABASE_URL, SUPABASE_ANON_KEY, and TELEGRAM_BOT_USERNAME in webapp/config.js and verify Edge Function secrets.";
    banner.classList.remove("hidden");
  }

  function renderAnnouncements() {
    const slot = document.getElementById("announcement-slot");
    slot.textContent = "";
    if (!state.announcements.length) return;
    state.announcements.forEach((a) => {
      const box = el("div", "alert-card");
      box.appendChild(el("strong", null, a.title));
      box.appendChild(el("div", null, a.body));
      slot.appendChild(box);
    });
  }

  function syncTelegramChrome() {
    if (!tg) return;
    tg.ready();
    tg.expand();
    if (tg.BackButton) {
      if (state.detailContentId) tg.BackButton.show();
      else tg.BackButton.hide();
    }
  }

  function openContentDetail(id) {
    state.detailContentId = id;
    state.detailFallbackItem = null;
    render();
  }

  function findContentById(id) {
    return (
      state.contentList.find((c) => c.id === id) ||
      state.bookmarks.find((b) => b.content_id === id && b.content)?.content ||
      state.history.find((h) => h.content_id === id && h.content)?.content ||
      (state.detailFallbackItem && state.detailFallbackItem.id === id ? state.detailFallbackItem : null)
    );
  }

  function renderComplianceGate(root) {
    const card = el("div", "card hero-card col");
    card.appendChild(el("h2", null, "18+ Age & Rights Compliance Gate"));
    card.appendChild(el("p", "muted", "DropMms Pro distributes only authorized, consensually produced content. Confirm you are 18+ and accept our consent and DMCA/removal policy to continue."));
    const btn = el("button", "btn", "Verify 18+ & Accept Compliance Terms");
    btn.addEventListener("click", async () => {
      try {
        const res = await fnFetch("user-actions", {
          action: "confirm_compliance",
          age_confirmed: true,
          consent_confirmed: true,
        });
        state.profile = res.profile;
        await loadInitialData();
        render();
      } catch (e) {
        showToast(e.message);
      }
    });
    card.appendChild(btn);
    root.appendChild(card);
  }

  function renderContentCard(item) {
    const card = el("div", "content-card");
    if (item.thumbnail_path && isSafeHttpUrl(item.thumbnail_path)) {
      const img = el("img", "content-thumb");
      img.src = item.thumbnail_path;
      img.alt = item.title || "Content thumbnail";
      card.appendChild(img);
    }
    const body = el("div", "content-body");
    const row = el("div", "row");
    row.appendChild(el("span", "pill", item.is_premium ? "PREMIUM" : "OPEN"));
    row.appendChild(el("span", "muted", `${item.view_count || 0} views`));
    body.appendChild(row);
    body.appendChild(el("strong", null, item.title));
    body.appendChild(el("div", "muted", (item.description || "").slice(0, 90)));
    card.appendChild(body);
    card.addEventListener("click", () => openContentDetail(item.id));
    return card;
  }

  function renderHome(root) {
    const hero = el("div", "card hero-card col");
    hero.appendChild(el("h1", null, "DropMms Pro"));
    hero.appendChild(el("p", "muted", state.accessToken ? "Live PostgreSQL catalog synchronized via Telegram session." : "Connect Supabase and launch from Telegram Mini App to load live content."));
    const searchInput = el("input", "input");
    searchInput.placeholder = "Search authorized catalog...";
    searchInput.value = state.searchQuery;
    searchInput.addEventListener("keydown", async (ev) => {
      if (ev.key === "Enter") {
        state.searchQuery = searchInput.value;
        state.tab = "explore";
        state.page = 0;
        await loadCatalog();
        render();
      }
    });
    hero.appendChild(searchInput);
    root.appendChild(hero);

    if (state.categories.length) {
      const chips = el("div", "chip-row");
      state.categories.forEach((c) => {
        const chip = el("button", "chip", c.name);
        chip.addEventListener("click", async () => {
          state.selectedCategoryId = c.id;
          state.tab = "explore";
          state.page = 0;
          await loadCatalog();
          render();
        });
        chips.appendChild(chip);
      });
      root.appendChild(chips);
    }

    const featured = state.contentList.filter((x) => x.is_featured);
    if (featured.length) {
      root.appendChild(el("h3", null, "Featured"));
      const grid = el("div", "grid-2");
      featured.forEach((item) => grid.appendChild(renderContentCard(item)));
      root.appendChild(grid);
    }

    root.appendChild(el("h3", null, "Latest & Trending"));
    if (!state.contentList.length) {
      root.appendChild(el("div", "card muted", state.accessToken ? "No published items in database yet." : "Waiting for authenticated Telegram session and Supabase connection."));
    } else {
      const grid = el("div", "grid-2");
      state.contentList.forEach((item) => grid.appendChild(renderContentCard(item)));
      root.appendChild(grid);
    }
  }

  function renderExplore(root) {
    const filterCard = el("div", "card col");
    const search = el("input", "input");
    search.placeholder = "Search database...";
    search.value = state.searchQuery;
    const sortSel = el("select", "select");
    [["latest", "Latest"], ["popular", "Most Popular"]].forEach(([v, label]) => {
      const opt = el("option", null, label);
      opt.value = v;
      if (state.sortBy === v) opt.selected = true;
      sortSel.appendChild(opt);
    });
    const applyBtn = el("button", "btn", "Apply Search & Sort");
    applyBtn.addEventListener("click", async () => {
      state.searchQuery = search.value;
      state.sortBy = sortSel.value;
      state.page = 0;
      await loadCatalog();
      render();
    });
    filterCard.append(search, sortSel, applyBtn);
    root.appendChild(filterCard);

    if (state.categories.length) {
      const chips = el("div", "chip-row");
      const allChip = el("button", `chip${state.selectedCategoryId === "" ? " active" : ""}`, "All");
      allChip.addEventListener("click", async () => {
        state.selectedCategoryId = "";
        state.page = 0;
        await loadCatalog();
        render();
      });
      chips.appendChild(allChip);
      state.categories.forEach((c) => {
        const chip = el("button", `chip${state.selectedCategoryId === c.id ? " active" : ""}`, c.name);
        chip.addEventListener("click", async () => {
          state.selectedCategoryId = c.id;
          state.page = 0;
          await loadCatalog();
          render();
        });
        chips.appendChild(chip);
      });
      root.appendChild(chips);
    }

    const grid = el("div", "grid-2");
    state.contentList.forEach((item) => grid.appendChild(renderContentCard(item)));
    root.appendChild(state.contentList.length ? grid : el("div", "card muted", "No matching database records."));

    const pager = el("div", "row");
    const prev = el("button", "btn btn-secondary", "Previous");
    prev.disabled = state.page === 0;
    prev.addEventListener("click", async () => { state.page = Math.max(0, state.page - 1); await loadCatalog(); render(); });
    const next = el("button", "btn btn-secondary", "Next");
    next.disabled = state.contentList.length < 12;
    next.addEventListener("click", async () => { state.page += 1; await loadCatalog(); render(); });
    pager.append(prev, el("span", "muted", `Page ${state.page + 1}`), next);
    root.appendChild(pager);
  }

  function renderDetails(root) {
    const id = state.detailContentId;
    const item = findContentById(id);
    if (!item) {
      const loadingCard = el("div", "card col");
      loadingCard.appendChild(el("div", "muted", "Loading content details from database..."));
      root.appendChild(loadingCard);
      restFetch(`content?id=eq.${encodeURIComponent(id)}&limit=1`)
        .then((rows) => {
          if (rows && rows[0]) {
            state.detailFallbackItem = rows[0];
          } else {
            state.detailContentId = null;
            showToast("Content item unavailable.");
          }
          render();
        })
        .catch(() => {
          state.detailContentId = null;
          render();
        });
      return;
    }

    const card = el("div", "card col");
    const backBtn = el("button", "btn btn-secondary", "Back to Catalog");
    backBtn.addEventListener("click", () => {
      state.detailContentId = null;
      state.detailFallbackItem = null;
      render();
    });
    card.appendChild(backBtn);

    if (item.thumbnail_path && isSafeHttpUrl(item.thumbnail_path)) {
      const thumb = el("img", "content-thumb");
      thumb.src = item.thumbnail_path;
      thumb.alt = item.title || "Thumbnail";
      card.appendChild(thumb);
    }

    const metaRow = el("div", "row");
    metaRow.appendChild(el("span", "pill", item.is_premium ? "PREMIUM" : "OPEN ACCESS"));
    const cat = state.categories.find((c) => c.id === item.category_id);
    if (cat) metaRow.appendChild(el("span", "pill", cat.name));
    card.appendChild(metaRow);

    card.appendChild(el("h2", null, item.title));
    card.appendChild(el("p", "muted", item.description || "No description provided."));

    const playerBox = el("div", "col");
    const playBtn = el("button", "btn", "Verify Authorization & Play");
    playBtn.addEventListener("click", async () => {
      try {
        const res = await fnFetch("content-access", { contentId: item.id });
        playerBox.textContent = "";
        if (!isSafeHttpUrl(res.playback_url)) throw new Error("Invalid signed playback URL");
        if (res.media_type === "image") {
          const img = el("img", "content-thumb");
          img.src = res.playback_url;
          img.alt = item.title || "Authorized media";
          playerBox.appendChild(img);
        } else {
          const media = el(res.media_type === "audio" ? "audio" : "video");
          media.controls = true;
          media.src = res.playback_url;
          playerBox.appendChild(media);
        }
        await loadUserCollections();
      } catch (e) {
        showToast(e.message);
      }
    });
    playerBox.appendChild(playBtn);
    card.appendChild(playerBox);

    const isSaved = state.bookmarks.some((b) => b.content_id === item.id);
    const actions = el("div", "row");
    const bmBtn = el("button", "btn btn-secondary", isSaved ? "Saved" : "Bookmark");
    bmBtn.addEventListener("click", async () => {
      try {
        const r = await fnFetch("user-actions", { action: "toggle_bookmark", content_id: item.id });
        showToast(r.bookmarked ? "Saved to bookmarks" : "Removed from bookmarks");
        await loadUserCollections();
        render();
      } catch (e) { showToast(e.message); }
    });
    const repBtn = el("button", "btn btn-danger", "Report / Request Removal");
    repBtn.addEventListener("click", async () => {
      const details = window.prompt("Describe the rights, consent, or policy issue (min 5 chars):");
      if (!details) return;
      try {
        await fnFetch("user-actions", {
          action: "submit_report",
          content_id: item.id,
          reason: "copyright_dmca",
          details,
          removal_requested: true,
        });
        showToast("Moderation & removal request submitted.");
      } catch (e) { showToast(e.message); }
    });
    actions.append(bmBtn, repBtn);
    card.appendChild(actions);
    root.appendChild(card);

    const related = state.contentList.filter((c) => c.id !== item.id && c.category_id === item.category_id).slice(0, 4);
    if (related.length) {
      root.appendChild(el("h3", null, "Related Content"));
      const grid = el("div", "grid-2");
      related.forEach((rel) => grid.appendChild(renderContentCard(rel)));
      root.appendChild(grid);
    }
  }

  function renderPremium(root) {
    const instr = state.settings.payment_instructions || {};
    const instrCard = el("div", "card col");
    instrCard.appendChild(el("h2", null, instr.title || "Payment Instructions"));
    instrCard.appendChild(el("p", "muted", instr.instructions || "Owner has not published payment instructions yet."));
    root.appendChild(instrCard);

    root.appendChild(el("h3", null, "Membership Plans"));
    if (!state.plans.length) {
      root.appendChild(el("div", "card muted", "No active membership plans configured in database."));
    } else {
      state.plans.forEach((plan) => {
        const pCard = el("div", "card col");
        pCard.appendChild(el("strong", null, `${plan.name} — ${plan.price_amount} ${plan.currency} / ${plan.duration_days}d`));
        pCard.appendChild(el("div", "muted", plan.description));
        const methodInput = el("input", "input");
        methodInput.placeholder = "Payment method used";
        const txInput = el("input", "input");
        txInput.placeholder = "Transaction reference / hash";
        const subBtn = el("button", "btn", "Submit Payment Verification");
        subBtn.addEventListener("click", async () => {
          try {
            await fnFetch("payment-workflow", {
              action: "submit_payment",
              plan_id: plan.id,
              payment_method: methodInput.value,
              transaction_reference: txInput.value,
            });
            showToast("Payment submitted as pending.");
            await loadUserCollections();
            render();
          } catch (e) { showToast(e.message); }
        });
        pCard.append(methodInput, txInput, subBtn);
        root.appendChild(pCard);
      });
    }

    root.appendChild(el("h3", null, "Your Payment Requests"));
    if (!state.payments.length) {
      root.appendChild(el("div", "card muted", "No payment requests submitted yet."));
    } else {
      state.payments.forEach((p) => {
        const row = el("div", "card col");
        row.appendChild(el("strong", null, `${p.amount} ${p.currency} • ${p.status.toUpperCase()}`));
        row.appendChild(el("div", "muted", `Method: ${p.payment_method} • Ref: ${p.transaction_reference}`));
        if (p.review_notes) row.appendChild(el("div", "muted", `Owner note: ${p.review_notes}`));
        root.appendChild(row);
      });
    }
  }

  function renderProfile(root) {
    const pCard = el("div", "card col");
    if (!state.profile) {
      pCard.appendChild(el("h2", null, "Telegram Authentication Required"));
      pCard.appendChild(el("p", "muted", "Open this Mini App inside Telegram so Telegram.WebApp.initData can be verified server-side."));
      root.appendChild(pCard);
      return;
    }
    pCard.appendChild(el("h2", null, `${state.profile.first_name || "Member"} ${state.profile.last_name || ""}`.trim()));
    pCard.appendChild(el("div", "muted", `Telegram ID: ${state.profile.telegram_id} • Code: ${state.profile.referral_code} • Referrals: ${state.referrals.length}`));
    pCard.appendChild(el("div", "pill", state.activeSubscription ? `Premium Active until ${state.activeSubscription.expires_at.slice(0, 10)}` : "Standard Tier"));
    if (BOT_USERNAME) {
      pCard.appendChild(el("div", "muted", `Referral link: https://t.me/${BOT_USERNAME}?startapp=ref_${state.profile.referral_code}`));
    }
    if (tg && tg.initData) {
      const appBtn = el("a", "btn btn-secondary", "Sync Session to Android App");
      appBtn.href = `dropmms://auth?initData=${encodeURIComponent(tg.initData)}`;
      pCard.appendChild(appBtn);
    }
    root.appendChild(pCard);

    root.appendChild(el("h3", null, `Saved Bookmarks (${state.bookmarks.length})`));
    if (!state.bookmarks.length) {
      root.appendChild(el("div", "card muted", "No saved bookmarks yet."));
    } else {
      const grid = el("div", "grid-2");
      state.bookmarks.forEach((b) => { if (b.content) grid.appendChild(renderContentCard(b.content)); });
      root.appendChild(grid);
    }

    root.appendChild(el("h3", null, `Viewing History (${state.history.length})`));
    if (!state.history.length) {
      root.appendChild(el("div", "card muted", "No authorized playback history yet."));
    } else {
      const grid = el("div", "grid-2");
      state.history.slice(0, 8).forEach((h) => { if (h.content) grid.appendChild(renderContentCard(h.content)); });
      root.appendChild(grid);
    }

    const supCard = el("div", "card col");
    supCard.appendChild(el("h3", null, "Support & Removal Requests"));
    const subj = el("input", "input"); subj.placeholder = "Subject";
    const msg = el("textarea", "textarea"); msg.placeholder = "Describe your question or removal request";
    const sendBtn = el("button", "btn", "Submit Support Request");
    sendBtn.addEventListener("click", async () => {
      try {
        await fnFetch("user-actions", { action: "submit_support", subject: subj.value, message: msg.value });
        showToast("Support ticket created.");
        await loadUserCollections();
        render();
      } catch (e) { showToast(e.message); }
    });
    supCard.append(subj, msg, sendBtn);
    root.appendChild(supCard);

    state.supportTickets.forEach((t) => {
      const tc = el("div", "card col");
      tc.appendChild(el("strong", null, `${t.subject} • ${t.status.toUpperCase()}`));
      tc.appendChild(el("div", "muted", t.message));
      if (t.admin_reply) tc.appendChild(el("div", "pill", `Owner reply: ${t.admin_reply}`));
      root.appendChild(tc);
    });
  }

  async function refreshAdminDashboard() {
    if (state.adminLoading) return;
    state.adminLoading = true;
    try {
      const [statsRes, usersRes, contentRes, catRes, planRes, paysRes, subsRes, repsRes, supRes, logsRes] = await Promise.all([
        fnFetch("admin-api", { action: "dashboard_stats" }),
        fnFetch("admin-api", { action: "list_users" }),
        restFetch("content?order=created_at.desc&limit=100"),
        restFetch("categories?order=sort_order.asc"),
        restFetch("premium_plans?order=sort_order.asc"),
        fnFetch("admin-api", { action: "list_payments" }),
        fnFetch("admin-api", { action: "list_subscriptions" }),
        fnFetch("admin-api", { action: "list_reports" }),
        fnFetch("admin-api", { action: "list_support" }),
        fnFetch("admin-api", { action: "list_activity_logs" }),
      ]);
      state.adminData = {
        stats: statsRes.stats,
        users: usersRes.users || [],
        content: contentRes || [],
        categories: catRes || [],
        plans: planRes || [],
        payments: paysRes.payments || [],
        subscriptions: subsRes.subscriptions || [],
        reports: repsRes.reports || [],
        support: supRes.support_requests || [],
        logs: logsRes.logs || [],
      };
    } finally {
      state.adminLoading = false;
    }
  }

  async function renderAdmin(root) {
    if (!state.isOwner) {
      root.appendChild(el("div", "card muted", "Forbidden: Owner authorization (8254788387) required."));
      return;
    }
    if (!state.adminData.stats) {
      root.appendChild(el("div", "card muted", "Loading live Owner dashboard from Supabase..."));
      try {
        await refreshAdminDashboard();
        render();
      } catch (e) {
        showToast(e.message);
        root.textContent = "";
        root.appendChild(el("div", "card muted", `Owner dashboard error: ${e.message}`));
      }
      return;
    }

    const tabs = [
      ["stats", "Stats"],
      ["payments", "Payments"],
      ["content", "Content"],
      ["catalog", "Categories & Plans"],
      ["users", "Users & Reports"],
      ["settings", "Settings & Logs"],
    ];
    const tabBar = el("div", "chip-row");
    tabs.forEach(([key, label]) => {
      const chip = el("button", `chip${state.adminTab === key ? " active" : ""}`, label);
      chip.addEventListener("click", () => { state.adminTab = key; render(); });
      tabBar.appendChild(chip);
    });
    const reloadChip = el("button", "chip", "Refresh");
    reloadChip.addEventListener("click", async () => {
      state.adminData.stats = null;
      await loadInitialData();
      render();
    });
    tabBar.appendChild(reloadChip);
    root.appendChild(tabBar);

    const d = state.adminData;
    if (state.adminTab === "stats") {
      const s = d.stats;
      const statsCard = el("div", "card col");
      statsCard.appendChild(el("h2", null, "Owner Dashboard (Live DB)"));
      statsCard.appendChild(el("div", "muted", `Users: ${s.total_users} (${s.blocked_users} blocked)`));
      statsCard.appendChild(el("div", "muted", `Content: ${s.published_content}/${s.total_content} published`));
      statsCard.appendChild(el("div", "muted", `Pending Payments: ${s.pending_payments} • Active Subscriptions: ${s.active_subscriptions}`));
      statsCard.appendChild(el("div", "muted", `Open Reports: ${s.open_reports} • Open Support: ${s.open_support}`));
      root.appendChild(statsCard);
    } else if (state.adminTab === "payments") {
      const grantCard = el("div", "card col");
      grantCard.appendChild(el("h3", null, "Grant Membership"));
      const tidIn = el("input", "input"); tidIn.placeholder = "Telegram ID";
      const daysIn = el("input", "input"); daysIn.value = "30"; daysIn.placeholder = "Days";
      const grantBtn = el("button", "btn", "Grant Active Membership");
      grantBtn.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", { action: "grant_subscription", user_telegram_id: Number(tidIn.value), duration_days: Number(daysIn.value) || 30 });
          showToast("Membership granted.");
          state.adminData.stats = null;
          render();
        } catch (e) { showToast(e.message); }
      });
      grantCard.append(tidIn, daysIn, grantBtn);
      root.appendChild(grantCard);

      d.payments.forEach((p) => {
        const pc = el("div", "card col");
        pc.appendChild(el("strong", null, `TG #${p.user_telegram_id} — ${p.amount} ${p.currency} [${p.status.toUpperCase()}]`));
        pc.appendChild(el("div", "muted", `Method: ${p.payment_method} • Ref: ${p.transaction_reference}`));
        if (p.status === "pending") {
          const row = el("div", "row");
          const ok = el("button", "btn", "Approve");
          ok.addEventListener("click", async () => {
            try {
              await fnFetch("payment-workflow", { action: "review_payment", payment_id: p.id, decision: "approved", review_notes: "Verified by Owner" });
              state.adminData.stats = null;
              render();
            } catch (e) { showToast(e.message); }
          });
          const no = el("button", "btn btn-danger", "Reject");
          no.addEventListener("click", async () => {
            try {
              await fnFetch("payment-workflow", { action: "review_payment", payment_id: p.id, decision: "rejected", review_notes: "Verification failed" });
              state.adminData.stats = null;
              render();
            } catch (e) { showToast(e.message); }
          });
          row.append(ok, no);
          pc.appendChild(row);
        }
        root.appendChild(pc);
      });
    } else if (state.adminTab === "content") {
      const form = el("div", "card col");
      form.appendChild(el("h3", null, "Publish Content"));
      const tIn = el("input", "input"); tIn.placeholder = "Title";
      const dIn = el("input", "input"); dIn.placeholder = "Description";
      const mIn = el("input", "input"); mIn.placeholder = "Storage Path or HTTPS Media URL";
      const thIn = el("input", "input"); thIn.placeholder = "HTTPS Thumbnail URL (optional)";
      const premChk = el("input"); premChk.type = "checkbox";
      const rightChk = el("input"); rightChk.type = "checkbox";
      const consChk = el("input"); consChk.type = "checkbox";
      const flags = el("div", "row");
      const l1 = el("label", "muted"); l1.append(premChk, document.createTextNode(" Premium "));
      const l2 = el("label", "muted"); l2.append(rightChk, document.createTextNode(" Rights Verified "));
      const l3 = el("label", "muted"); l3.append(consChk, document.createTextNode(" Consent Documented"));
      flags.append(l1, l2, l3);
      const saveBtn = el("button", "btn", "Publish Content");
      saveBtn.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", {
            action: "upsert_content",
            content: {
              title: tIn.value,
              description: dIn.value,
              media_path: mIn.value,
              thumbnail_path: thIn.value,
              is_premium: premChk.checked,
              is_published: rightChk.checked && consChk.checked,
              rights_verified: rightChk.checked,
              consent_documented: consChk.checked,
            },
          });
          showToast("Content saved.");
          state.adminData.stats = null;
          await loadCatalog();
          render();
        } catch (e) { showToast(e.message); }
      });
      form.append(tIn, dIn, mIn, thIn, flags, saveBtn);
      root.appendChild(form);

      d.content.forEach((c) => {
        const cc = el("div", "card row");
        const info = el("div", "col");
        info.appendChild(el("strong", null, c.title));
        info.appendChild(el("div", "muted", `${c.is_published ? "Published" : "Draft"} • ${c.is_premium ? "Premium" : "Open"}`));
        const del = el("button", "btn btn-danger", "Delete");
        del.addEventListener("click", async () => {
          try {
            await fnFetch("admin-api", { action: "delete_content", id: c.id });
            state.adminData.stats = null;
            await loadCatalog();
            render();
          } catch (e) { showToast(e.message); }
        });
        cc.append(info, del);
        root.appendChild(cc);
      });
    } else if (state.adminTab === "catalog") {
      const catForm = el("div", "card col");
      catForm.appendChild(el("h3", null, "Create Category"));
      const cn = el("input", "input"); cn.placeholder = "Category Name";
      const cd = el("input", "input"); cd.placeholder = "Description";
      const cb = el("button", "btn", "Save Category");
      cb.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", { action: "upsert_category", category: { name: cn.value, description: cd.value } });
          showToast("Category saved.");
          state.adminData.stats = null;
          await loadInitialData();
          render();
        } catch (e) { showToast(e.message); }
      });
      catForm.append(cn, cd, cb);
      root.appendChild(catForm);

      const planForm = el("div", "card col");
      planForm.appendChild(el("h3", null, "Create Premium Plan"));
      const pn = el("input", "input"); pn.placeholder = "Plan Name";
      const pp = el("input", "input"); pp.value = "9.99"; pp.placeholder = "Price";
      const pd = el("input", "input"); pd.value = "30"; pd.placeholder = "Days";
      const pb = el("button", "btn", "Save Plan");
      pb.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", {
            action: "upsert_plan",
            plan: { name: pn.value, price_amount: Number(pp.value), currency: "USD", duration_days: Number(pd.value) || 30 },
          });
          showToast("Plan saved.");
          state.adminData.stats = null;
          await loadInitialData();
          render();
        } catch (e) { showToast(e.message); }
      });
      planForm.append(pn, pp, pd, pb);
      root.appendChild(planForm);

      d.plans.forEach((p) => {
        const pc = el("div", "card row");
        pc.appendChild(el("strong", null, `${p.name} — ${p.price_amount} ${p.currency} / ${p.duration_days}d`));
        const del = el("button", "btn btn-danger", "Delete");
        del.addEventListener("click", async () => {
          try {
            await fnFetch("admin-api", { action: "delete_plan", id: p.id });
            state.adminData.stats = null;
            await loadInitialData();
            render();
          } catch (e) { showToast(e.message); }
        });
        pc.appendChild(del);
        root.appendChild(pc);
      });
    } else if (state.adminTab === "users") {
      d.users.forEach((u) => {
        const uc = el("div", "card row");
        const info = el("div", "col");
        info.appendChild(el("strong", null, `${u.first_name || "Member"} (@${u.username || "n/a"})`));
        info.appendChild(el("div", "muted", `TG #${u.telegram_id} • ${u.is_blocked ? "BLOCKED" : "ACTIVE"}`));
        uc.appendChild(info);
        if (u.telegram_id !== OWNER_TELEGRAM_ID) {
          const btn = el("button", u.is_blocked ? "btn btn-secondary" : "btn btn-danger", u.is_blocked ? "Unblock" : "Block");
          btn.addEventListener("click", async () => {
            try {
              await fnFetch("admin-api", { action: "set_user_blocked", telegram_id: u.telegram_id, is_blocked: !u.is_blocked, reason: "Owner moderation action" });
              state.adminData.stats = null;
              render();
            } catch (e) { showToast(e.message); }
          });
          uc.appendChild(btn);
        }
        root.appendChild(uc);
      });

      d.reports.forEach((rep) => {
        const rc = el("div", "card col");
        rc.appendChild(el("strong", null, `${rep.content?.title || rep.content_id} • ${rep.reason}`));
        rc.appendChild(el("div", "muted", `${rep.details} [${rep.status.toUpperCase()}]`));
        if (rep.status === "pending") {
          const row = el("div", "row");
          const rm = el("button", "btn btn-danger", "Remove Content");
          rm.addEventListener("click", async () => {
            try {
              await fnFetch("admin-api", { action: "resolve_report", report_id: rep.id, status: "resolved_removed", resolution_notes: "Unpublished by Owner" });
              state.adminData.stats = null;
              render();
            } catch (e) { showToast(e.message); }
          });
          const dis = el("button", "btn btn-secondary", "Dismiss");
          dis.addEventListener("click", async () => {
            try {
              await fnFetch("admin-api", { action: "resolve_report", report_id: rep.id, status: "dismissed", resolution_notes: "Verified authorized" });
              state.adminData.stats = null;
              render();
            } catch (e) { showToast(e.message); }
          });
          row.append(rm, dis);
          rc.appendChild(row);
        }
        root.appendChild(rc);
      });
    } else if (state.adminTab === "settings") {
      const setCard = el("div", "card col");
      setCard.appendChild(el("h3", null, "Payment Instructions & Announcements"));
      const payCfg = state.settings.payment_instructions || {};
      const it = el("input", "input"); it.value = payCfg.title || "Payment Instructions";
      const ib = el("textarea", "textarea"); ib.value = payCfg.instructions || ""; ib.placeholder = "Payment instructions for users";
      const savePay = el("button", "btn", "Save Payment Instructions");
      savePay.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", { action: "update_setting", key: "payment_instructions", value: { title: it.value, instructions: ib.value } });
          showToast("Payment instructions saved.");
          await loadInitialData();
          render();
        } catch (e) { showToast(e.message); }
      });
      const at = el("input", "input"); at.placeholder = "Announcement Title";
      const ab = el("input", "input"); ab.placeholder = "Announcement Message";
      const postAnn = el("button", "btn btn-secondary", "Publish Announcement");
      postAnn.addEventListener("click", async () => {
        try {
          await fnFetch("admin-api", { action: "upsert_announcement", announcement: { title: at.value, body: ab.value, severity: "info", is_active: true } });
          showToast("Announcement published.");
          await loadInitialData();
          render();
        } catch (e) { showToast(e.message); }
      });
      setCard.append(it, ib, savePay, at, ab, postAnn);
      root.appendChild(setCard);

      d.support.forEach((t) => {
        const tc = el("div", "card col");
        tc.appendChild(el("strong", null, `TG #${t.user_telegram_id} • ${t.subject} [${t.status}]`));
        tc.appendChild(el("div", "muted", t.message));
        const rIn = el("input", "input"); rIn.value = t.admin_reply || ""; rIn.placeholder = "Owner reply...";
        const rBtn = el("button", "btn", "Reply & Resolve");
        rBtn.addEventListener("click", async () => {
          try {
            await fnFetch("admin-api", { action: "reply_support", support_id: t.id, status: "resolved", admin_reply: rIn.value });
            state.adminData.stats = null;
            render();
          } catch (e) { showToast(e.message); }
        });
        tc.append(rIn, rBtn);
        root.appendChild(tc);
      });

      d.logs.slice(0, 20).forEach((l) => {
        const lc = el("div", "card col");
        lc.appendChild(el("strong", null, `${l.action} • ${l.target_type}`));
        lc.appendChild(el("div", "muted", `Actor: ${l.actor_telegram_id} • ${String(l.created_at || "").slice(0, 19)}`));
        root.appendChild(lc);
      });
    }
  }

  function render() {
    syncTelegramChrome();
    renderAnnouncements();
    const root = document.getElementById("view-root");
    root.textContent = "";

    document.querySelectorAll(".nav-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.tab === state.tab);
    });

    if (state.profile && (!state.profile.age_verified_at || !state.profile.consent_accepted_at)) {
      renderComplianceGate(root);
      return;
    }

    if (state.detailContentId) {
      renderDetails(root);
      return;
    }

    if (state.tab === "home") renderHome(root);
    else if (state.tab === "explore") renderExplore(root);
    else if (state.tab === "premium") renderPremium(root);
    else if (state.tab === "profile") renderProfile(root);
    else if (state.tab === "admin") renderAdmin(root).catch((e) => showToast(e.message));
  }

  document.querySelectorAll(".nav-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.detailContentId = null;
      state.detailFallbackItem = null;
      state.tab = btn.dataset.tab;
      render();
    });
  });

  authenticateTelegram();
})();
