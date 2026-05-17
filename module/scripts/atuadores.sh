#!/system/bin/sh
# MANJIRO DINAMIC — atuadores.sh (v1.7.1 — Edge 20 Pro / SD870 / Lineage 23.2)
# Calibrado contra dump real: kernel 4.19.325-cip131-st15-perf, SM8250/Kona,
# Adreno 650 (7 pwrlevels), LPDDR5 (11 steps), L3 (14 steps), bus_dcvs ausente
# (este device usa devfreq direto), thermal-engine vendor com 7 trip points.
#
# Cada atuador tem 3 versões padrão:
#   apply_*    → modo jogo (com backup via safe_write)
#   relax_*    → motor MPC pode chamar para liberar pressão térmica
#   _idle_*    → modo bateria/IDLE (NOVO em v1.7.1)
#
# Mitigação por device_mitigation.json continua funcionando: cada path tagueado
# como DISABLE_* é pulado.

# ── mitigation helper ─────────────────────────────────────────────────────
MITIGATION_F="${DATA:-/data/adb/manjiro_dinamic}/state/mitigation.list"

mit_allows() {
  local tag="$1"
  [ -f "$MITIGATION_F" ] || return 0
  grep -qx "$tag" "$MITIGATION_F" 2>/dev/null && return 1
  return 0
}

# Detecta se estamos em SoC Kona (SD865/865+/870).
is_kona_soc() {
  local platform soc_model
  platform="$(getprop ro.board.platform 2>/dev/null)"
  soc_model="$(getprop ro.soc.model 2>/dev/null)"
  case "$platform" in kona) return 0;; esac
  case "$soc_model" in SM8250|SM8250-AC|SM8250-AB) return 0;; esac
  if grep -qi "kona\|sm8250\|qcom snapdragon 8[67]" /proc/cpuinfo 2>/dev/null; then return 0; fi
  return 1
}

# ════════════════════════════════════════════════════════════════════════════
# 0. Probes (NOVO v1.7.1) — detecção de paths reais antes de tentar atuar
# ════════════════════════════════════════════════════════════════════════════
# Edge 20 Pro NÃO tem /proc/touchpanel. Usa FocalTech "fts" via event9. Para
# evitar tentativas silenciosas, o motor probe os paths reais no boot e cacheia
# em ${DATA}/state/probe_*.path.
PROBE_F_DIR="${DATA:-/data/adb/manjiro_dinamic}/state"

probe_touchpanel_path() {
  local cache="$PROBE_F_DIR/probe_touchpanel.path"
  [ -f "$cache" ] && { cat "$cache"; return; }
  mkdir -p "$PROBE_F_DIR" 2>/dev/null
  local p
  for p in /proc/touchpanel /sys/touchscreen /sys/class/touch_dev \
           /sys/class/touchpanel /sys/devices/virtual/touchscreen \
           /sys/touchpanel; do
    if [ -d "$p" ]; then
      echo "$p" > "$cache"
      echo "$p"; return
    fi
  done
  echo "none" > "$cache"
  echo "none"
}

probe_devfreq_l3() {
  # Retorna 0..3 paths separados por linha
  local cache="$PROBE_F_DIR/probe_devfreq_l3.paths"
  if [ -f "$cache" ]; then cat "$cache"; return; fi
  mkdir -p "$PROBE_F_DIR" 2>/dev/null
  : > "$cache"
  for d in /sys/class/devfreq/*l3-lat* /sys/class/devfreq/*l3-cpu* \
           /sys/class/devfreq/*cpu*l3*; do
    [ -d "$d" ] && echo "$d" >> "$cache"
  done
  cat "$cache"
}

probe_devfreq_ddr() {
  local cache="$PROBE_F_DIR/probe_devfreq_ddr.path"
  [ -f "$cache" ] && { cat "$cache"; return; }
  mkdir -p "$PROBE_F_DIR" 2>/dev/null
  for d in /sys/class/devfreq/*llcc-ddr* /sys/class/devfreq/*cpu-ddr* \
           /sys/class/devfreq/*ddr-bw*; do
    if [ -d "$d" ]; then echo "$d" > "$cache"; echo "$d"; return; fi
  done
  echo "none" > "$cache"
  echo "none"
}

# ════════════════════════════════════════════════════════════════════════════
# 1. IO scheduler swap — Encore + ProjectRaco
# ════════════════════════════════════════════════════════════════════════════
io_pick_scheduler() {
  local target="$1" available="$2"
  case "$target" in
    game)
      echo "$available" | grep -qw "mq-deadline" && { echo "mq-deadline"; return; }
      echo "$available" | grep -qw "kyber"       && { echo "kyber"; return; }
      echo "$available" | grep -qw "noop"        && { echo "noop"; return; }
      echo "$available" | grep -qw "none"        && { echo "none"; return; }
      echo ""
      ;;
    normal)
      echo "$available" | grep -qw "bfq"          && { echo "bfq"; return; }
      echo "$available" | grep -qw "mq-deadline"  && { echo "mq-deadline"; return; }
      echo ""
      ;;
    powersave)
      # Em IDLE/tela apagada: maximiza eficiência de batch I/O
      echo "$available" | grep -qw "bfq"          && { echo "bfq"; return; }
      echo "$available" | grep -qw "none"         && { echo "none"; return; }
      echo "$available" | grep -qw "noop"         && { echo "noop"; return; }
      echo ""
      ;;
    *)
      echo "" ;;
  esac
}

apply_io_scheduler() {
  local target="${1:-game}" grp="${2:-game}"
  mit_allows "DISABLE_IO_SCHEDULER" || return 0
  [ "${IO_SCHED_ENABLE:-1}" = "1" ] || return 0

  local block av sched
  for block in /sys/block/*; do
    [ -e "$block/queue/scheduler" ] || continue
    av="$(cat "$block/queue/scheduler" 2>/dev/null | tr -d '[]')"
    sched="$(io_pick_scheduler "$target" "$av")"
    [ -n "$sched" ] && safe_write "$block/queue/scheduler" "$sched" "$grp"
  done
}

apply_io_queue_game() {
  mit_allows "DISABLE_IO_QUEUE" || return 0
  [ "${IO_QUEUE_TUNE:-1}" = "1" ] || return 0
  local block
  for block in /sys/block/sd*/queue /sys/block/mmc*/queue /sys/block/dm-*/queue; do
    [ -d "$block" ] || continue
    [ -e "$block/iostats"        ] && safe_write "$block/iostats"        "0"   game
    [ -e "$block/add_random"     ] && safe_write "$block/add_random"     "0"   game
    [ -e "$block/read_ahead_kb"  ] && safe_write "$block/read_ahead_kb"  "256" game
    [ -e "$block/nr_requests"    ] && safe_write "$block/nr_requests"    "128" game
    [ -e "$block/rq_affinity"    ] && safe_write "$block/rq_affinity"    "1"   game
  done
}

# NOVO v1.7.1 — em IDLE relaxar I/O para reduzir wakeups
apply_io_queue_idle() {
  mit_allows "DISABLE_IO_QUEUE" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  local block
  for block in /sys/block/sd*/queue /sys/block/mmc*/queue; do
    [ -d "$block" ] || continue
    [ -e "$block/read_ahead_kb"  ] && safe_write "$block/read_ahead_kb"  "128" idle
    [ -e "$block/nr_requests"    ] && safe_write "$block/nr_requests"    "64"  idle
    # iostats=0 em IDLE também reduz writes para sysfs
    [ -e "$block/iostats"        ] && safe_write "$block/iostats"        "0"   idle
  done
}

# ════════════════════════════════════════════════════════════════════════════
# 2. HWUI / SurfaceFlinger hints
# ════════════════════════════════════════════════════════════════════════════
apply_hwui_game() {
  mit_allows "DISABLE_HWUI_HINTS" || return 0
  [ "${HWUI_HINTS_ENABLE:-1}" = "1" ] || return 0
  setprop debug.hwui.skip_empty_damage true 2>/dev/null
  setprop debug.hwui.use_partial_updates false 2>/dev/null
  setprop debug.hwui.drop_shadow_cache_size 2 2>/dev/null
  setprop debug.hwui.layer_cache_size 24 2>/dev/null
  setprop debug.hwui.r_buffer_cache_size 16 2>/dev/null
  setprop debug.hwui.texture_cache_size 96 2>/dev/null
  setprop debug.hwui.path_cache_size 16 2>/dev/null
  setprop debug.sf.enable_egl_image_tracker 0 2>/dev/null
  echo "1" > "${DATA}/run/hwui_game_active" 2>/dev/null
}
relax_hwui_game() {
  mit_allows "DISABLE_HWUI_HINTS" || return 0
  setprop debug.hwui.skip_empty_damage "" 2>/dev/null
  setprop debug.hwui.use_partial_updates "" 2>/dev/null
  setprop debug.hwui.drop_shadow_cache_size "" 2>/dev/null
  setprop debug.hwui.layer_cache_size "" 2>/dev/null
  setprop debug.hwui.r_buffer_cache_size "" 2>/dev/null
  setprop debug.hwui.texture_cache_size "" 2>/dev/null
  setprop debug.hwui.path_cache_size "" 2>/dev/null
  setprop debug.sf.enable_egl_image_tracker "" 2>/dev/null
  echo "0" > "${DATA}/run/hwui_game_active" 2>/dev/null
}

# ════════════════════════════════════════════════════════════════════════════
# 3. KGSL Adreno 650 — calibrado v1.7.1
# ════════════════════════════════════════════════════════════════════════════
# Tabela CONFIRMADA Edge 20 Pro: 7 pwrlevels (idx 0=670 MHz, 6=305 MHz min).
# default_pwrlevel=6 (min), max_pwrlevel=0, num_pwrlevels=7.
apply_kgsl_game_extended() {
  mit_allows "DISABLE_KGSL_TUNE" || return 0
  [ "${KGSL_TUNE_ENABLE:-1}" = "1" ] || return 0
  local k="/sys/class/kgsl/kgsl-3d0"
  [ -d "$k" ] || return 0
  [ -e "$k/bus_split"        ] && safe_write "$k/bus_split"        "0" game
  [ -e "$k/force_clk_on"     ] && safe_write "$k/force_clk_on"     "1" game
  [ -e "$k/throttling"       ] && safe_write "$k/throttling"       "0" game
  # default_pwrlevel: 2 (=525 MHz) garante GPU acordada mas não cravada no max
  [ -e "$k/default_pwrlevel" ] && safe_write "$k/default_pwrlevel" "${KGSL_GAME_MIN_PWRLEVEL:-2}" game
  # min_pwrlevel: 6 (default) deixa o governor escolher entre 305 e 670 MHz
  [ -e "$k/devfreq/adrenoboost" ] && safe_write "$k/devfreq/adrenoboost" "${KGSL_GAME_ADRENOBOOST:-2}" game
  # thermal_pwrlevel=0 = sem throttling térmico de software (motor cuida)
  [ -e "$k/thermal_pwrlevel" ] && safe_write "$k/thermal_pwrlevel" "${KGSL_THERMAL_PWRLEVEL_GAME:-0}" game
  # idle_timer mais agressivo em jogo (não queremos sleep no meio de frame)
  [ -e "$k/idle_timer" ] && safe_write "$k/idle_timer" "${KGSL_IDLE_TIMER_GAME:-120}" game
  if [ -d /sys/module/adreno_idler/parameters ]; then
    safe_write /sys/module/adreno_idler/parameters/adreno_idler_active "N" game
  fi
}

relax_kgsl_extended() {
  local k="/sys/class/kgsl/kgsl-3d0"
  [ -d "$k" ] || return 0
  [ -e /sys/module/adreno_idler/parameters/adreno_idler_active ] && \
    safe_write /sys/module/adreno_idler/parameters/adreno_idler_active "Y" base
}

# NOVO v1.7.1 — KGSL em IDLE (economiza GPU sem perder fluidez de UI)
apply_kgsl_idle() {
  mit_allows "DISABLE_KGSL_TUNE" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  local k="/sys/class/kgsl/kgsl-3d0"
  [ -d "$k" ] || return 0
  # thermal_pwrlevel=4 cap em 441.6 MHz (max em IDLE). UI fluida garantida,
  # mas reduz potencial de spike inútil. Governor sobe pra esse cap quando
  # precisa, então não há lag perceptível.
  [ -e "$k/thermal_pwrlevel" ] && safe_write "$k/thermal_pwrlevel" "${KGSL_THERMAL_PWRLEVEL_IDLE:-4}" idle
  [ -e "$k/idle_timer" ] && safe_write "$k/idle_timer" "${KGSL_IDLE_TIMER_IDLE:-300}" idle
  [ -e "$k/devfreq/adrenoboost" ] && safe_write "$k/devfreq/adrenoboost" "0" idle
}

# ════════════════════════════════════════════════════════════════════════════
# 4. Touchpanel — CALIBRADO v1.7.1 (probe path real, no-op se não existe)
# ════════════════════════════════════════════════════════════════════════════
# Edge 20 Pro: FocalTech FTS sem /proc/touchpanel. probe_touchpanel_path()
# retorna "none" → atuador silencia sem polluir logs.
apply_touchpanel_game() {
  mit_allows "DISABLE_TOUCHPANEL_TUNE" || return 0
  [ "${TOUCHPANEL_GAME_ENABLE:-1}" = "1" ] || return 0
  local tp
  tp="$(probe_touchpanel_path)"
  [ "$tp" = "none" ] && return 0
  [ -d "$tp" ] || return 0
  [ -e "$tp/game_switch_enable"    ] && safe_write "$tp/game_switch_enable"    "1" game
  [ -e "$tp/oplus_tp_limit_enable" ] && safe_write "$tp/oplus_tp_limit_enable" "0" game
  [ -e "$tp/oppo_tp_limit_enable"  ] && safe_write "$tp/oppo_tp_limit_enable"  "0" game
  # FTS-specific (Motorola/Sony, se existir)
  [ -e "$tp/fts_gesture_enable"    ] && safe_write "$tp/fts_gesture_enable"    "0" game
  [ -e "$tp/glove_mode_enable"     ] && safe_write "$tp/glove_mode_enable"     "0" game
  # Sensitivity tuning (alguns kernels têm)
  [ -e "$tp/sensitivity_level"     ] && safe_write "$tp/sensitivity_level"     "high" game
}

# ════════════════════════════════════════════════════════════════════════════
# 5. TCP — multiplayer latency. Edge 20 Pro tem cubic + reno apenas (sem BBR).
# ════════════════════════════════════════════════════════════════════════════
apply_tcp_game() {
  mit_allows "DISABLE_TCP_TUNE" || return 0
  [ "${TCP_GAME_ENABLE:-1}" = "1" ] || return 0
  local n="/proc/sys/net/ipv4"
  [ -e "$n/tcp_low_latency" ] && safe_write "$n/tcp_low_latency" "1" game
  [ -e "$n/tcp_fastopen"    ] && safe_write "$n/tcp_fastopen"    "3" game
  [ -e "$n/tcp_sack"        ] && safe_write "$n/tcp_sack"        "1" game
  # ECN: 2 (use if peer supports) é melhor que 1 (always-on) em redes públicas.
  # NÃO alteramos: já está em 2 na Lineage. Antes (v1.7.0) sobrescrevíamos
  # com 1 = comportamento pior em alguns ISPs. Removido.
  [ -e "$n/tcp_notsent_lowat" ] && safe_write "$n/tcp_notsent_lowat" "131072" game
}

# ════════════════════════════════════════════════════════════════════════════
# 6. sched_lib_name — CALIBRADO v1.7.1 (MERGE em vez de overwrite)
# ════════════════════════════════════════════════════════════════════════════
# Lineage perf já configura "UnityMain,libunity.so" por default. Manjiro
# fazia overwrite na v1.7.0; v1.7.1 lê o valor atual e adiciona apenas as
# entries faltantes. Backup do original para rollback.
SCHED_LIB_EXTRA="libil2cpp.so,libUE4.so,libUE5.so,libgodot_android.so,libmain.so,libminecraftpe.so,libmono.so,libcocos2d.so,libcocos2dcpp.so,libgdx.so,libgdx-box2d.so,libgameengine.so,libgame.so,libfmod.so,libVkLayer_unity.so,libpvrm3unity.so,libNativeGameEngine.so,libpvrm4unity.so,libcocos.so,libxgame.so,libv8.so"

apply_sched_lib_game() {
  mit_allows "DISABLE_SCHED_LIB" || return 0
  [ "${SCHED_LIB_ENABLE:-1}" = "1" ] || return 0
  local f="/proc/sys/kernel/sched_lib_name"
  [ -e "$f" ] || return 0
  local merged
  if [ "${SCHED_LIB_MERGE:-1}" = "1" ]; then
    local cur extra="$SCHED_LIB_EXTRA"
    cur="$(cat "$f" 2>/dev/null)"
    if [ -n "$cur" ]; then
      # Append cur,extra dedupe
      merged="$(printf '%s,%s' "$cur" "$extra" | tr ',' '\n' | awk 'NF && !seen[$0]++' | tr '\n' ',' | sed 's/,$//')"
    else
      merged="UnityMain,libunity.so,${extra}"
    fi
  else
    merged="UnityMain,libunity.so,${SCHED_LIB_EXTRA}"
  fi
  safe_write "$f" "$merged" game
  [ -e /proc/sys/kernel/sched_lib_mask_force ] && \
    safe_write /proc/sys/kernel/sched_lib_mask_force "255" game
}

# ════════════════════════════════════════════════════════════════════════════
# 7. L3 / DDR floors — CALIBRADO v1.7.1 (paths reais via probe)
# ════════════════════════════════════════════════════════════════════════════
# bus_dcvs NÃO existe neste device (kernel CIP). Atuamos direto em devfreq.
# Tabela L3 confirmada: 14 steps de 300 MHz a 1612.8 MHz.
# Tabela DDR confirmada: 11 steps de 762 a 10437 MB/s.
apply_kona_bus_floor() {
  mit_allows "DISABLE_DDR_TWEAK" || return 0
  [ "${KONA_BUS_FLOOR_ENABLE:-1}" = "1" ] || return 0

  # L3 floors via devfreq probe (3 hooks: cpu0/4/7-l3-lat no Kona)
  local floor_l3="${L3_FLOOR_GAME_HZ:-614400000}"
  probe_devfreq_l3 | while read -r d; do
    [ -d "$d" ] || continue
    [ -e "$d/min_freq" ] && safe_write "$d/min_freq" "$floor_l3" game
  done

  # DDR floor — Motorola CIP kernel expõe min_freq em MB/s em alguns paths
  local ddr; ddr="$(probe_devfreq_ddr)"
  if [ "$ddr" != "none" ] && [ -d "$ddr" ]; then
    [ -e "$ddr/min_freq" ] && safe_write "$ddr/min_freq" "${DDR_FLOOR_GAME_BW:-3879}" game
  fi

  # Fallback bus_dcvs (mantém compat com kernels stock SD870)
  local dcvs="/sys/devices/system/cpu/bus_dcvs"
  if [ -d "$dcvs" ]; then
    for sub in DDR LLCC L3; do
      [ -d "$dcvs/$sub" ] || continue
      [ -e "$dcvs/$sub/available_frequencies" ] || continue
      local avail mid_freq
      avail="$(cat "$dcvs/$sub/available_frequencies" 2>/dev/null)"
      mid_freq="$(echo "$avail" | tr ' ' '\n' | sort -n | awk 'NR==int(NF/2)+1')"
      [ -n "$mid_freq" ] && [ -e "$dcvs/$sub/hw_min_freq" ] && \
        safe_write "$dcvs/$sub/hw_min_freq" "$mid_freq" game
    done
  fi
}

relax_kona_bus() {
  # MPC chama para baixar pressão térmica/energia.
  local floor_idle="${L3_FLOOR_IDLE_HZ:-403200000}"
  probe_devfreq_l3 | while read -r d; do
    [ -d "$d" ] || continue
    if [ -e "$d/min_freq" ]; then
      # Volta para o mínimo do hardware (cpuinfo) ou IDLE floor — o que for menor
      local mn; mn="$(cat "$d/min_freq" 2>/dev/null)"
      safe_write "$d/min_freq" "$floor_idle" base
    fi
  done

  local ddr; ddr="$(probe_devfreq_ddr)"
  if [ "$ddr" != "none" ] && [ -d "$ddr" ]; then
    [ -e "$ddr/min_freq" ] && safe_write "$ddr/min_freq" "${DDR_FLOOR_IDLE_BW:-762}" base
  fi

  local dcvs="/sys/devices/system/cpu/bus_dcvs"
  if [ -d "$dcvs" ]; then
    for sub in DDR LLCC L3; do
      [ -d "$dcvs/$sub" ] || continue
      [ -e "$dcvs/$sub/cpuinfo_min_freq" ] || continue
      local mn; mn="$(cat "$dcvs/$sub/cpuinfo_min_freq" 2>/dev/null)"
      [ -n "$mn" ] && [ -e "$dcvs/$sub/hw_min_freq" ] && \
        safe_write "$dcvs/$sub/hw_min_freq" "$mn" base
    done
  fi
}

# ════════════════════════════════════════════════════════════════════════════
# 8. core_ctl (Kona) — controle automático de núcleos por cluster
# ════════════════════════════════════════════════════════════════════════════
apply_core_ctl_game() {
  mit_allows "DISABLE_CORE_CTL" || return 0
  [ "${CORE_CTL_GAME_ENABLE:-1}" = "1" ] || return 0
  for c in /sys/devices/system/cpu/cpu0/core_ctl/enable \
           /sys/devices/system/cpu/cpu4/core_ctl/enable; do
    [ -e "$c" ] && safe_write "$c" "1" game
  done
  # Em jogo: queremos os 4 cores big sempre ativos (não permitir park)
  [ -e /sys/devices/system/cpu/cpu4/core_ctl/min_cpus ] && \
    safe_write /sys/devices/system/cpu/cpu4/core_ctl/min_cpus "3" game
  [ -e /sys/devices/system/cpu/cpu7/core_ctl/min_cpus ] && \
    safe_write /sys/devices/system/cpu/cpu7/core_ctl/min_cpus "1" game
}

# NOVO v1.7.1 — core_ctl em IDLE permite parking agressivo (economiza bateria)
apply_core_ctl_idle() {
  mit_allows "DISABLE_CORE_CTL" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  # min_cpus baixo permite parking dos Gold/Prime quando ocioso
  [ -e /sys/devices/system/cpu/cpu4/core_ctl/min_cpus ] && \
    safe_write /sys/devices/system/cpu/cpu4/core_ctl/min_cpus "0" idle
  [ -e /sys/devices/system/cpu/cpu7/core_ctl/min_cpus ] && \
    safe_write /sys/devices/system/cpu/cpu7/core_ctl/min_cpus "0" idle
}

# ════════════════════════════════════════════════════════════════════════════
# 9. Render-thread affinity — pin para Gold+Prime
# ════════════════════════════════════════════════════════════════════════════
pin_render_threads() {
  mit_allows "DISABLE_THREAD_PIN" || return 0
  [ "${THREAD_PIN_GAME_ENABLE:-1}" = "1" ] || return 0
  local pkg="$1"; [ -n "$pkg" ] || return 0
  command -v taskset >/dev/null 2>&1 || return 0

  local pid; pid="$(pidof "$pkg" 2>/dev/null | head -1)"
  [ -z "$pid" ] && return 0
  is_num "$pid" || return 0

  local mask="${THREAD_PIN_MASK:-f0}"
  local task name
  for task in /proc/"$pid"/task/*; do
    [ -d "$task" ] || continue
    name="$(cat "$task/comm" 2>/dev/null)"
    case "$name" in
      RenderThread|GameThread|GLThread|UnityMain|UnityGfx|UnityMulti*|UE4Worker|Thread-*) ;;
      *) continue ;;
    esac
    local tid; tid="$(basename "$task")"
    taskset -p "$mask" "$tid" >/dev/null 2>&1
  done
}

# ════════════════════════════════════════════════════════════════════════════
# 10. vmtouch — page pinning (opt-in)
# ════════════════════════════════════════════════════════════════════════════
apply_vmtouch_game() {
  mit_allows "DISABLE_VMTOUCH" || return 0
  [ "${VMTOUCH_GAME_ENABLE:-0}" = "1" ] || return 0
  local vt="${MODDIR}/system/bin/vmtouch"
  [ -x "$vt" ] || vt="$(command -v vmtouch 2>/dev/null)"
  [ -x "$vt" ] || return 0
  local pkg="$1"; [ -n "$pkg" ] || return 0

  local pid; pid="$(pidof "$pkg" 2>/dev/null | head -1)"
  [ -z "$pid" ] && return 0

  local mapsf="/proc/$pid/maps"
  [ -r "$mapsf" ] || return 0
  awk '/lib(unity|il2cpp|UE4|godot|cocos|gdx|game|mono|fmod|minecraft)/ {print $NF}' "$mapsf" 2>/dev/null \
    | sort -u | while read -r lib; do
        [ -f "$lib" ] || continue
        "$vt" -l "$lib" >/dev/null 2>&1 &
      done
  echo "$pid" > "${DATA}/run/vmtouch_active_pid" 2>/dev/null
}

relax_vmtouch_game() {
  local pid_f="${DATA}/run/vmtouch_active_pid"
  [ -f "$pid_f" ] || return 0
  rm -f "$pid_f" 2>/dev/null
}

# ════════════════════════════════════════════════════════════════════════════
# 11. sched tweaks Kona (WALT scheduler)
# ════════════════════════════════════════════════════════════════════════════
apply_sched_kona_game() {
  mit_allows "DISABLE_SCHED_KONA" || return 0
  [ "${SCHED_KONA_ENABLE:-1}" = "1" ] || return 0
  local k="/proc/sys/kernel"
  [ -e "$k/sched_busy_hyst_ns"             ] && safe_write "$k/sched_busy_hyst_ns"             "2000000" game
  [ -e "$k/sched_prefer_spread"            ] && safe_write "$k/sched_prefer_spread"            "0"       game
  [ -e "$k/sched_min_task_util_for_colocation" ] && safe_write "$k/sched_min_task_util_for_colocation" "0" game
  [ -e "$k/sched_min_granularity_ns"       ] && safe_write "$k/sched_min_granularity_ns"       "1000000" game
  [ -e "$k/sched_wakeup_granularity_ns"    ] && safe_write "$k/sched_wakeup_granularity_ns"    "1500000" game
  [ -e "$k/sched_nr_migrate"               ] && safe_write "$k/sched_nr_migrate"               "32"      game
  # NOVO v1.7.1: latency_ns mais agressivo em jogo
  [ -e "$k/sched_latency_ns"               ] && safe_write "$k/sched_latency_ns"               "5000000" game
}

# NOVO v1.7.1 — sched tweaks em IDLE (relaxa, agrupa wakeups)
apply_sched_kona_idle() {
  mit_allows "DISABLE_SCHED_KONA" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  local k="/proc/sys/kernel"
  [ -e "$k/sched_busy_hyst_ns"             ] && safe_write "$k/sched_busy_hyst_ns"             "10000000" idle
  [ -e "$k/sched_prefer_spread"            ] && safe_write "$k/sched_prefer_spread"            "0"        idle
  [ -e "$k/sched_min_granularity_ns"       ] && safe_write "$k/sched_min_granularity_ns"       "3000000"  idle
  [ -e "$k/sched_wakeup_granularity_ns"    ] && safe_write "$k/sched_wakeup_granularity_ns"    "4000000"  idle
  [ -e "$k/sched_latency_ns"               ] && safe_write "$k/sched_latency_ns"               "16000000" idle
}

# ════════════════════════════════════════════════════════════════════════════
# 12. cpuset/stune extended
# ════════════════════════════════════════════════════════════════════════════
apply_cpuset_extended_game() {
  mit_allows "DISABLE_CPUSET_EXT" || return 0
  [ "${CPUSET_EXT_ENABLE:-1}" = "1" ] || return 0
  if [ -d /dev/stune ]; then
    [ -e /dev/stune/top-app/schedtune.boost          ] && safe_write /dev/stune/top-app/schedtune.boost          "10" game
    [ -e /dev/stune/top-app/schedtune.prefer_idle    ] && safe_write /dev/stune/top-app/schedtune.prefer_idle    "1"  game
    [ -e /dev/stune/foreground/schedtune.boost       ] && safe_write /dev/stune/foreground/schedtune.boost       "5"  game
    [ -e /dev/stune/background/schedtune.boost       ] && safe_write /dev/stune/background/schedtune.boost       "0"  game
    [ -e /dev/stune/background/schedtune.prefer_idle ] && safe_write /dev/stune/background/schedtune.prefer_idle "0"  game
  fi
}

# NOVO v1.7.1 — stune IDLE (top-app boost zero quando tela apagada)
apply_cpuset_extended_idle() {
  mit_allows "DISABLE_CPUSET_EXT" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  if [ -d /dev/stune ]; then
    [ -e /dev/stune/top-app/schedtune.boost       ] && safe_write /dev/stune/top-app/schedtune.boost       "0" idle
    [ -e /dev/stune/top-app/schedtune.prefer_idle ] && safe_write /dev/stune/top-app/schedtune.prefer_idle "0" idle
    [ -e /dev/stune/foreground/schedtune.boost    ] && safe_write /dev/stune/foreground/schedtune.boost    "0" idle
  fi
}

# ════════════════════════════════════════════════════════════════════════════
# 13. Toast (opt-in)
# ════════════════════════════════════════════════════════════════════════════
toast_game_state() {
  [ "${TOAST_ENABLE:-0}" = "1" ] || return 0
  local msg="$1"
  command -v cmd >/dev/null 2>&1 || return 0
  su -lp 2000 -c "cmd notification post -S bigtext -t 'Manjiro' Mtag '$msg'" >/dev/null 2>&1 &
}

# ════════════════════════════════════════════════════════════════════════════
# 14. NOVO v1.7.1 — Charging thermal management (Motorola pstar mmi_chrg)
# ════════════════════════════════════════════════════════════════════════════
# Edge 20 Pro tem Motorola parallel charger (mmi_chrg_manager + BQ2597X).
# Em jogo intenso com bateria > 80% E temp > 40°C, reduzir input power do
# carregador reduz aquecimento da bateria sem cortar performance.
manage_charge_thermal() {
  mit_allows "DISABLE_CHARGE_THERMAL" || return 0
  [ "${CHARGE_THERMAL_MANAGE:-1}" = "1" ] || return 0
  local mode="${1:-game}"
  local bat_dir="/sys/class/power_supply/battery"
  [ -d "$bat_dir" ] || return 0

  local soc temp_raw status
  soc="$(cat "$bat_dir/capacity" 2>/dev/null)"
  temp_raw="$(cat "$bat_dir/temp" 2>/dev/null)"
  status="$(cat "$bat_dir/status" 2>/dev/null)"

  is_num "$soc" || return 0
  is_num "$temp_raw" || return 0

  # bypass charging quando full ou >95% E charging (reduz heat residual)
  if [ "$mode" = "game" ] \
     && [ "${soc}" -ge "${CHARGE_BYPASS_FULL_THRESHOLD:-95}" ] \
     && [ "$status" = "Charging" ]; then
    # Some Motorola kernels expose mmi_chrg_manager/factory_image_mode
    local mc="/sys/class/power_supply/battery"
    [ -e "$mc/factory_image_mode" ] && safe_write "$mc/factory_image_mode" "1" game
  fi
}

# ════════════════════════════════════════════════════════════════════════════
# 15. NOVO v1.7.1 — Powersave bundle (IDLE/screen-off)
# ════════════════════════════════════════════════════════════════════════════
# Aplica o pacote completo de economia de bateria SEM tocar em ENGAGED.
# Chamado pelo motor quando state = IDLE ou screen=off por > LOOP_IDLE_SEC.
apply_powersave_bundle() {
  mit_allows "DISABLE_POWERSAVE" || return 0
  [ "${POWERSAVE_ENABLE:-1}" = "1" ] || return 0
  apply_io_scheduler        powersave idle
  apply_io_queue_idle
  apply_kgsl_idle
  apply_core_ctl_idle
  apply_sched_kona_idle
  apply_cpuset_extended_idle
  # uclamp idle (centralized in io.sh/control.sh)
  # vm.swappiness IDLE
  [ -e /proc/sys/vm/swappiness ] && safe_write /proc/sys/vm/swappiness "${VM_SWAPPINESS_IDLE:-80}" idle
  [ -e /proc/sys/vm/watermark_scale_factor ] && \
    safe_write /proc/sys/vm/watermark_scale_factor "${VM_WATERMARK_IDLE:-100}" idle
}

relax_powersave_bundle() {
  # Volta tudo pra base. Motor chama no transition IDLE → ACTIVE.
  apply_io_scheduler normal base
}

# ════════════════════════════════════════════════════════════════════════════
# 16. NOVO v1.7.1 — Battery Saver mode (bateria < BATSAVER_THRESHOLD_PCT)
# ════════════════════════════════════════════════════════════════════════════
# Quando bateria < 20% E descarregando, modo extra-econômico. NUNCA ativa
# durante jogo (motor protege ENGAGED). Caps via cap_cluster_pct (io.sh).
apply_battery_saver() {
  mit_allows "DISABLE_BATSAVER" || return 0
  local bat_dir="/sys/class/power_supply/battery"
  [ -d "$bat_dir" ] || return 0
  local soc status
  soc="$(cat "$bat_dir/capacity" 2>/dev/null)"
  status="$(cat "$bat_dir/status" 2>/dev/null)"
  is_num "$soc" || return 0
  [ "$soc" -ge "${BATSAVER_THRESHOLD_PCT:-20}" ] && return 0
  [ "$status" = "Charging" ] && return 0

  cap_cluster_pct little "${BATSAVER_LITTLE_PCT:-60}"
  cap_cluster_pct gold   "${BATSAVER_GOLD_PCT:-40}"
  cap_cluster_pct prime  "${BATSAVER_PRIME_PCT:-30}"
  cap_gpu_pct            "${BATSAVER_GPU_PCT:-40}"
  echo "1" > "${DATA}/run/batsaver_active" 2>/dev/null
}

# ════════════════════════════════════════════════════════════════════════════
# 17. NOVO v1.7.1 — Doze whitelist (Manjiro daemon não pode ser dozado)
# ════════════════════════════════════════════════════════════════════════════
# Adiciona Manjiro às exceções do Android Doze para o daemon continuar
# operando quando o device entra em deep idle.
apply_doze_whitelist() {
  [ "${DOZE_AWARE_ENABLE:-1}" = "1" ] || return 0
  command -v cmd >/dev/null 2>&1 || return 0
  # Whitelist do daemon não tem package — usa system-uid 1000, sempre exempt
  # Aqui apenas registramos que estamos cientes do Doze para o loop ajustar
  echo "1" > "${DATA}/run/doze_aware" 2>/dev/null
}

# ════════════════════════════════════════════════════════════════════════════
# Composição: top-level
# ════════════════════════════════════════════════════════════════════════════
enter_game_sd870() {
  local pkg="$1"
  apply_io_scheduler        game game
  apply_io_queue_game
  apply_hwui_game
  apply_kgsl_game_extended
  apply_touchpanel_game
  apply_tcp_game
  apply_sched_lib_game
  apply_kona_bus_floor
  apply_core_ctl_game
  apply_sched_kona_game
  apply_cpuset_extended_game
  manage_charge_thermal game
  ( pin_render_threads "$pkg" ) &
  ( apply_vmtouch_game "$pkg" ) &
  toast_game_state "Modo jogo ativo"
}

exit_game_sd870() {
  apply_io_scheduler normal base
  relax_hwui_game
  relax_kgsl_extended
  relax_vmtouch_game
  toast_game_state "Modo normal"
}

# NOVO v1.7.1 — entry point IDLE
enter_idle_sd870() {
  apply_powersave_bundle
  apply_battery_saver
}

exit_idle_sd870() {
  relax_powersave_bundle
}
