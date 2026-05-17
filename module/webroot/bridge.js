/* Manjiro Dinamic — root-manager bridge (universal).
 *
 * The CGI endpoints (cgi-bin/*.sh) only work when the UI is opened via the
 * built-in HTTP server at http://127.0.0.1:8080. Inside a root manager's
 * WebUI viewer (KSU / KSU Next / APatch / MMRL / WebUI X), only static
 * files are served — there is no CGI execution. Those viewers do inject a
 * JavaScript bridge that we can use to run shell commands as root.
 *
 * Detected globals (in priority order):
 *   ksu                         — KSU / KSU Next / APatch / KSU WebUI Standalone
 *   $manjiro_dinamic            — MMRL / WebUI X (module-namespaced)
 *   $EnFile / $packageManager   — WebUI X feature APIs
 *   mmrl                        — older MMRL builds
 *   KernelSU                    — legacy
 *
 * Detection is NOT cached — some viewers inject globals after first paint.
 */
(function (root) {
  'use strict';

  const MOD_ID = 'manjiro_dinamic';
  const DATA   = '/data/adb/manjiro_dinamic';

  function getApi() {
    try { if (typeof ksu === 'object' && ksu && typeof ksu.exec === 'function') return { kind: 'ksu', obj: ksu }; } catch (_) {}
    try {
      const g = root['$' + MOD_ID];
      if (g && typeof g.exec === 'function') return { kind: 'mmrl-$mod', obj: g };
    } catch (_) {}
    try { if (typeof mmrl === 'object' && mmrl && typeof mmrl.exec === 'function') return { kind: 'mmrl', obj: mmrl }; } catch (_) {}
    try { if (typeof KernelSU === 'object' && KernelSU && typeof KernelSU.exec === 'function') return { kind: 'KernelSU', obj: KernelSU }; } catch (_) {}
    return null;
  }

  function getFileApi() {
    // WebUI X file API ($EnFile.read / write / exists)
    try { if (typeof $EnFile === 'object' && $EnFile && typeof $EnFile.read === 'function') return $EnFile; } catch (_) {}
    return null;
  }

  function execShell(cmd) {
    return new Promise(function (resolve, reject) {
      const api = getApi();
      if (!api) return reject(new Error('no-bridge'));

      const cbName = '__mjr_cb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      let settled = false;
      const timer = setTimeout(function () {
        if (settled) return;
        settled = true;
        try { delete root[cbName]; } catch (_) {}
        reject(new Error('exec-timeout'));
      }, 6000);

      root[cbName] = function (errno, stdout, stderr) {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        try { delete root[cbName]; } catch (_) {}
        const code = parseInt(errno, 10);
        if (isNaN(code) || code === 0) resolve(stdout || '');
        else reject(new Error('errno=' + errno + ' ' + (stderr || '')));
      };

      // Try the standard KSU shape first: exec(cmd, options_json, callback_name).
      try { api.obj.exec(cmd, '{}', cbName); return; } catch (_) {}
      // Some MMRL shapes: exec(cmd, callback_name).
      try { api.obj.exec(cmd, cbName); return; } catch (_) {}
      // Final fallback: synchronous variant returning stdout directly.
      try {
        const out = api.obj.exec(cmd);
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        try { delete root[cbName]; } catch (_) {}
        resolve(typeof out === 'string' ? out : (out && out.stdout) ? out.stdout : '');
      } catch (e) {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        try { delete root[cbName]; } catch (_) {}
        reject(e);
      }
    });
  }

  function shellEscape(s) {
    return "'" + String(s).replace(/'/g, "'\\''") + "'";
  }

  function readText(absPath, tailLines) {
    // Prefer WebUI X $EnFile API when available (cheaper, no shell hop).
    const f = getFileApi();
    if (f && !tailLines) {
      try {
        const out = f.read(absPath);
        if (typeof out === 'string') return Promise.resolve(out);
      } catch (_) {}
    }
    const cmd = (tailLines && tailLines > 0)
      ? 'tail -n ' + (tailLines | 0) + ' ' + shellEscape(absPath) + ' 2>/dev/null'
      : 'cat ' + shellEscape(absPath) + ' 2>/dev/null';
    return execShell(cmd);
  }

  function readJson(absPath) {
    return readText(absPath).then(function (txt) {
      const trimmed = (txt || '').trim();
      if (!trimmed) throw new Error('empty');
      return JSON.parse(trimmed);
    });
  }

  function writeCmd(cmd) {
    const safe = String(cmd).replace(/[^a-z0-9_:=. -]/gi, '');
    return execShell("mkdir -p " + shellEscape(DATA + '/state') + " && echo " + shellEscape(safe) + " > " + shellEscape(DATA + '/state/control.cmd'));
  }

  function addGame(pkg) {
    if (!/^[a-zA-Z0-9._]+$/.test(pkg) || pkg.indexOf('..') !== -1) {
      return Promise.reject(new Error('invalid-pkg'));
    }
    const games = DATA + '/config/games.json';
    const entry = '  "' + pkg + '": {"enabled": true, "class": "user_added", "weight": "standard", "target": "balanced", "thermal_target_c10": 800, "profile": "standard"}\n}';
    const script =
      'if [ -f ' + shellEscape(games) + ' ] && ! grep -q "\\"' + pkg + '\\"" ' + shellEscape(games) + '; then ' +
      'sed "s/}[[:space:]]*$/,/" ' + shellEscape(games) + ' > ' + shellEscape(games) + '.tmp && ' +
      'printf %s ' + shellEscape(entry) + ' >> ' + shellEscape(games) + '.tmp && ' +
      'mv -f ' + shellEscape(games) + '.tmp ' + shellEscape(games) + ' && ' +
      'echo reload > ' + shellEscape(DATA + '/state/control.cmd') + '; ' +
      'fi';
    return execShell(script);
  }

  function toast(msg) {
    try {
      if (typeof ksu === 'object' && ksu && typeof ksu.toast === 'function') { ksu.toast(String(msg)); return; }
      const g = root['$' + MOD_ID];
      if (g && typeof g.toast === 'function') { g.toast(String(msg)); return; }
    } catch (_) {}
  }

  // ── Live diagnostics for the UI footer ────────────────────────────────
  function describe() {
    const api = getApi();
    const f   = getFileApi();
    const knownGlobals = ['ksu', '$' + MOD_ID, '$EnFile', '$packageManager', 'mmrl', 'KernelSU'];
    const present = knownGlobals.filter(function (n) {
      try { return typeof root[n] !== 'undefined' && root[n] !== null; } catch (_) { return false; }
    });
    return {
      api:    api ? api.kind : 'none',
      fileApi: !!f,
      globals: present,
    };
  }

  root.ManjiroBridge = {
    available: function () { return getApi() !== null; },
    kind: function () { const a = getApi(); return a ? a.kind : null; },
    describe: describe,
    exec: execShell,
    readJson: readJson,
    readText: readText,
    writeCmd: writeCmd,
    addGame: addGame,
    toast: toast,
    MOD_ID: MOD_ID,
    DATA: DATA,
  };
})(window);
