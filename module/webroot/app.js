/* Manjiro Dinamic — UI controller (Moto Gametime style) */
(function(){
  'use strict';

  // ── DOM cache ─────────────────────────────────────────────────────────
  const $ = (id) => document.getElementById(id);
  const ui = {
    statePill: $('state-pill'),
    stateLabel: $('state-label'),
    heroTemp: $('hero-temp'),
    heroZone: $('hero-zone'),
    heroBat: $('hero-bat'),
    heroPkg: $('hero-pkg'),
    heroMode: $('hero-mode'),
    heroBox: document.querySelector('.hero'),

    barPrime: $('bar-prime'),   barPrimePct: $('bar-prime-pct'),
    barGold:  $('bar-gold'),    barGoldPct:  $('bar-gold-pct'),
    barGpu:   $('bar-gpu'),     barGpuPct:   $('bar-gpu-pct'),

    actionTag: $('action-tag'),
    actionStable: $('action-stable'),
    actuatorsList: $('actuators-list'),

    trendNow: $('trend-now'),
    trend5s:  $('trend-5s'),
    trend10s: $('trend-10s'),
    trendBarFill: $('trend-bar-fill'),

    gamesList: $('games-list'),
    gamesCount: $('games-count'),
    gamesFilter: $('games-filter'),
    gamesAddCurrent: $('games-add-current'),

    btnLogsToggle: $('btn-logs-toggle'),
    logbox: $('logbox'),
  };

  // ── Helpers ───────────────────────────────────────────────────────────
  const c10toC = (c10) => (c10 == null || isNaN(c10) || c10 === 0) ? null : (c10 / 10);
  const fmtTemp = (c) => (c == null ? '--' : c.toFixed(1));
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

  function shortPkg(pkg) {
    if (!pkg) return '—';
    if (pkg.length <= 28) return pkg;
    const parts = pkg.split('.');
    if (parts.length >= 3) return parts.slice(-3).join('.');
    return pkg;
  }

  // Mapping of FSM state → human-friendly label & class
  const STATE_MAP = {
    BOOTSTRAP: { label: 'Inicializando', cls: 'state-idle' },
    IDLE:      { label: 'Em repouso',     cls: 'state-idle' },
    ACTIVE:    { label: 'Em uso',         cls: 'state-active' },
    ENGAGED:   { label: 'Em jogo',        cls: 'state-engaged' },
    COOLDOWN:  { label: 'Resfriando',     cls: 'state-cooldown' },
    SAFE:      { label: 'Emergência',     cls: 'state-safe' },
  };

  // Mapping of internal action names → friendly Portuguese tags
  const ACTION_MAP = {
    hold:                 { txt: 'estável',                cls: '' },
    clamp_background:     { txt: 'limitando segundo plano', cls: 'tag-active' },
    relax_background:     { txt: 'liberando segundo plano', cls: '' },
    kgsl_tl_tighten:      { txt: 'gpu — ajuste fino',       cls: 'tag-active' },
    reduce_gpu_micro:     { txt: 'gpu — reduzindo',         cls: 'tag-warn' },
    reduce_gold_micro:    { txt: 'cpu — reduzindo',         cls: 'tag-warn' },
    reduce_prime_micro:   { txt: 'cpu principal — segurando', cls: 'tag-warn' },
    tighten_cpu_margin:   { txt: 'cpu — protegendo',        cls: 'tag-warn' },
    safe_rollback:        { txt: 'rollback de segurança',   cls: 'tag-crit' },
    release_caps:         { txt: 'liberando limites',       cls: '' },
  };

  // Lyapunov V → stability buckets
  function stabilityFromV(v) {
    if (v == null || isNaN(v)) return { txt: '—', cls: 's-ok' };
    if (v <= 8)  return { txt: 'ótima',    cls: 's-good' };
    if (v <= 25) return { txt: 'normal',   cls: 's-ok' };
    if (v <= 60) return { txt: 'limitada', cls: 's-poor' };
    return                { txt: 'instável', cls: 's-poor' };
  }

  // Heat tint buckets
  function heatClass(c) {
    if (c == null) return '';
    if (c < 38) return 'heat-cold';
    if (c < 65) return 'heat-warm';
    if (c < 80) return 'heat-warm';
    if (c < 85) return 'heat-hot';
    return 'heat-crit';
  }

  // Trend bar marker — map 30°C..90°C to 0..100%
  function trendPos(c) {
    if (c == null) return 50;
    return clamp(((c - 30) / (90 - 30)) * 100, 0, 100);
  }

  // ── Status polling ────────────────────────────────────────────────────
  let lastState = null;
  const bridge = (typeof window !== 'undefined' && window.ManjiroBridge) ? window.ManjiroBridge : null;
  const diag = { lastSource: '-', lastOk: false, lastErr: '' };

  async function fetchStatus() {
    // Path 1 — if we're inside a root manager's WebView, the JS bridge is
    // present; prefer it (CGI will fail anyway because the viewer doesn't
    // execute shell scripts).
    if (bridge && bridge.available()) {
      try {
        const s = await bridge.readJson(bridge.DATA + '/state/status.json');
        diag.lastSource = 'bridge:' + bridge.kind();
        diag.lastOk = true; diag.lastErr = '';
        render(s);
        return;
      } catch (e) {
        diag.lastSource = 'bridge:' + bridge.kind();
        diag.lastOk = false; diag.lastErr = String(e && e.message || e);
      }
    }
    // Path 2 — CGI (works when opened via http://127.0.0.1:8080).
    try {
      const r = await fetch('cgi-bin/status.sh', { cache: 'no-store' });
      if (!r.ok) throw new Error('http ' + r.status);
      const s = await r.json();
      diag.lastSource = 'cgi'; diag.lastOk = true; diag.lastErr = '';
      render(s);
      return;
    } catch (e) {
      if (diag.lastSource === '-' || diag.lastSource === 'cgi') {
        diag.lastSource = 'cgi'; diag.lastErr = String(e && e.message || e);
      }
    }
    diag.lastOk = false;
    ui.stateLabel.textContent = 'Sem conexão';
    ui.statePill.className = 'brand-state state-safe';
  }

  function render(s) {
    // ── FSM state ──
    const st = s.mode || s.fsm_state || 'IDLE';
    const m = STATE_MAP[st] || { label: st, cls: 'state-idle' };
    ui.stateLabel.textContent = m.label;
    ui.statePill.className = 'brand-state ' + m.cls;

    // ── Hero ──
    const socC = c10toC(s.soc_temp_c10 || s.temp_c10);
    const batC = c10toC(s.battery_temp_c10);
    ui.heroTemp.textContent = fmtTemp(socC);
    ui.heroBat.textContent  = fmtTemp(batC);
    ui.heroPkg.textContent  = shortPkg(s.foreground_package);
    ui.heroMode.textContent = m.label;

    // Heat tint
    ui.heroBox.classList.remove('heat-cold','heat-warm','heat-hot','heat-crit');
    const hc = heatClass(socC);
    if (hc) ui.heroBox.classList.add(hc);

    // Battery emergency override on label
    if (s.safe_mode === true || s.safe_mode === 'true') {
      ui.stateLabel.textContent = 'Emergência';
      ui.statePill.className = 'brand-state state-safe';
    }

    // ── Performance bars ──
    const cp = s.cap_prime_pct ?? 100, cg = s.cap_gold_pct ?? 100, cgp = s.cap_gpu_pct ?? 100;
    ui.barPrime.style.width = cp + '%';
    ui.barGold.style.width  = cg + '%';
    ui.barGpu.style.width   = cgp + '%';
    ui.barPrimePct.textContent = cp + '%';
    ui.barGoldPct.textContent  = cg + '%';
    ui.barGpuPct.textContent   = cgp + '%';

    // ── Action tag ──
    const act = s.last_action || 'hold';
    const a = ACTION_MAP[act] || { txt: act, cls: '' };
    ui.actionTag.textContent = a.txt;
    ui.actionTag.className = 'action-tag ' + (a.cls || '');

    // ── Stability (Lyapunov V → bucket) ──
    const stab = stabilityFromV(s.lyapunov_v);
    ui.actionStable.textContent = stab.txt;
    ui.actionStable.className   = 'action-stable-val ' + stab.cls;

    // ── Active actuators (only when ENGAGED) ──
    const ACTUATOR_NAMES = {
      io_sched:   'IO scheduler',
      hwui:       'HWUI hints',
      kgsl_ext:   'GPU avançado',
      touch:      'Touchpanel',
      tcp:        'Latencia rede',
      sched_lib:  'Engine hint',
      bus_floor:  'L3/DDR floor',
      sched_kona: 'Sched Kona',
      cpuset_ext: 'Cpuset+stune',
      thread_pin: 'Pin Prime',
      vmtouch:    'Page pin RAM',
      // v1.7.1
      powersave:  'Economia',
      batsaver:   'Bat saver',
      charge_th:  'Carga thermal',
    };
    if (ui.actuatorsList) {
      const atu = Array.isArray(s.active_actuators) ? s.active_actuators : [];
      if (atu.length === 0) {
        ui.actuatorsList.innerHTML = '<span class="actuator-empty">—</span>';
      } else {
        ui.actuatorsList.innerHTML = atu
          .map(k => `<span class="actuator-chip">${ACTUATOR_NAMES[k] || k}</span>`)
          .join('');
      }
    }

    // ── Trend ──
    const p5 = c10toC(s.temp_pred_5s_c10);
    const p10 = c10toC(s.temp_pred_10s_c10);
    ui.trendNow.textContent = fmtTemp(socC);
    ui.trend5s.textContent  = fmtTemp(p5);
    ui.trend10s.textContent = fmtTemp(p10);
    const marker = trendPos(p10 != null ? p10 : socC);
    ui.trendBarFill.style.left = marker + '%';

    lastState = s;
  }

  // ── Games list ────────────────────────────────────────────────────────
  let gamesCache = null;
  let gamesFilter = '';
  async function fetchGames() {
    if (bridge && bridge.available()) {
      try {
        const j = await bridge.readJson(bridge.DATA + '/config/games.json');
        gamesCache = j;
        renderGames();
        return;
      } catch (_) { /* fall through to CGI */ }
    }
    try {
      const r = await fetch('cgi-bin/games.sh', { cache: 'no-store' });
      if (!r.ok) throw new Error('http ' + r.status);
      const j = await r.json();
      gamesCache = j;
      renderGames();
      return;
    } catch (_) { /* fall through */ }
    ui.gamesList.innerHTML = '<div class="games-empty">não foi possível carregar a lista</div>';
  }

  function renderGames() {
    if (!gamesCache) return;
    // games.json is a flat object of pkg → meta. We accept both formats.
    const games = (gamesCache && gamesCache.games && typeof gamesCache.games === 'object')
      ? gamesCache.games : gamesCache;
    const keys = Object.keys(games).filter(k => k && games[k] && typeof games[k] === 'object').sort();
    ui.gamesCount.textContent = keys.length + ' apps';

    const q = gamesFilter.trim().toLowerCase();
    const filtered = q ? keys.filter(k => k.toLowerCase().indexOf(q) !== -1) : keys;

    if (filtered.length === 0) {
      ui.gamesList.innerHTML = '<div class="games-empty">nenhum resultado</div>';
      return;
    }

    // Cap to 200 rows for UI speed
    const max = 200;
    const slice = filtered.slice(0, max);
    const rows = slice.map(k => {
      const v = games[k] || {};
      const cls = (v.class || 'casual').replace(/[^a-z0-9_]/gi, '');
      return `<div class="game-row"><span class="game-pkg">${k}</span><span class="game-class gc-${cls}">${v.class || 'casual'}</span></div>`;
    });
    if (filtered.length > max) {
      rows.push(`<div class="games-empty">… +${filtered.length - max} ocultos · refine a busca</div>`);
    }
    ui.gamesList.innerHTML = rows.join('');
  }

  ui.gamesFilter.addEventListener('input', (e) => {
    gamesFilter = e.target.value || '';
    renderGames();
  });
  ui.gamesAddCurrent.addEventListener('click', async () => {
    if (!lastState || !lastState.foreground_package) return;
    const pkg = lastState.foreground_package;
    let ok = false;
    if (bridge && bridge.available()) {
      try { await bridge.addGame(pkg); ok = true; } catch (_) {}
    }
    if (!ok) {
      try {
        const r = await fetch('cgi-bin/games.sh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'action=add&pkg=' + encodeURIComponent(pkg),
        });
        ok = r.ok;
      } catch (_) {}
    }
    if (ok) {
      if (bridge && bridge.available()) bridge.toast('Adicionado: ' + pkg);
      ui.gamesAddCurrent.textContent = 'adicionado';
      setTimeout(() => { ui.gamesAddCurrent.textContent = '+ Atual'; }, 1500);
      fetchGames();
    }
  });

  // ── Controls ──────────────────────────────────────────────────────────
  document.querySelectorAll('[data-cmd]').forEach(btn => {
    btn.addEventListener('click', async () => {
      btn.classList.add('is-busy');
      let sent = false;
      if (bridge && bridge.available()) {
        try { await bridge.writeCmd(btn.dataset.cmd); sent = true; } catch (_) {}
      }
      if (!sent) {
        try {
          const r = await fetch('cgi-bin/cmd.sh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'cmd=' + encodeURIComponent(btn.dataset.cmd),
          });
          sent = r.ok;
        } catch (_) {}
      }
      if (sent && bridge && bridge.available()) bridge.toast('Comando enviado');
      setTimeout(() => btn.classList.remove('is-busy'), 600);
      setTimeout(fetchStatus, 300);
    });
  });

  function buildDiagHeader() {
    if (!bridge) return '[diag] bridge module not loaded\n';
    const d = bridge.describe();
    return '[diag] bridge=' + d.api +
           '  fileApi=' + (d.fileApi ? 'yes' : 'no') +
           '  globals=[' + d.globals.join(',') + ']' +
           '\n[diag] last=' + diag.lastSource + ' ok=' + diag.lastOk +
           (diag.lastErr ? '  err=' + diag.lastErr : '') +
           '\n\n';
  }

  ui.btnLogsToggle.addEventListener('click', async () => {
    const visible = !ui.logbox.classList.contains('hidden');
    if (visible) { ui.logbox.classList.add('hidden'); return; }
    ui.logbox.classList.remove('hidden');
    ui.logbox.textContent = buildDiagHeader() + '(carregando eventos…)';
    let txt = '';
    if (bridge && bridge.available()) {
      try { txt = await bridge.readText(bridge.DATA + '/logs/main.log', 80); } catch (_) {}
    }
    if (!txt) {
      try {
        const r = await fetch('cgi-bin/logs.sh?tail=80', { cache: 'no-store' });
        txt = await r.text();
      } catch (_) {}
    }
    ui.logbox.textContent = buildDiagHeader() + (txt || '(sem eventos)');
  });

  // ── Boot ──────────────────────────────────────────────────────────────
  fetchStatus();
  fetchGames();
  setInterval(fetchStatus, 2500);
  setInterval(fetchGames, 60000);

})();
