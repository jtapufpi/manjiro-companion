#!/system/bin/sh
# MANJIRO DINAMIC — control.sh
# Advanced control core: cost function, Control Barrier Function (CBF), Lyapunov
# guard, oscillation tracker, Pareto selector and per-package action history.
#
# Ported from system/bin/manjiro_control and re-anchored on SoC die-temp (NOT
# battery). Battery is checked only through sense_battery_emergency() which is
# handled by the daemon's outer SAFE-state machine, never inside this engine.
#
# Source order: common.sh → math870.sh → sense.sh → io.sh → control.sh

# ── Zone constants ──────────────────────────────────────────────────────────
ctl_zone_name() {
  local t="$1"
  is_num "$t" || { echo "free"; return; }
  [ "$t" -ge "${ZONE_CRITICAL_C10:-900}" ] && echo "critical" && return
  [ "$t" -ge "${ZONE_HARD_C10:-850}"     ] && echo "hard"     && return
  [ "$t" -ge "${ZONE_SOFT_C10:-830}"     ] && echo "soft"     && return
  [ "$t" -ge "${ZONE_NORMAL_C10:-800}"   ] && echo "normal"   && return
  echo "free"
}

# ── Cost function ───────────────────────────────────────────────────────────
# Lower is better. Each component is scaled 0..100-ish so the weights interpret
# obviously. cost(action) = Wt*thermal + Wp*pressure + Wd*power + Wl*latency
#                          + Wc*change_cost + Ws*oscillation
ctl_action_cost() {
  local action="$1"
  local soc gpu q_cpu q_mem q_io slope pred5 bottleneck osc last_action
  soc="$(cat        "$RUNDIR/est_soc_temp"    2>/dev/null)"; is_num "$soc" || soc=400
  gpu="$(cat        "$RUNDIR/est_gpu_busy"    2>/dev/null)"; is_num "$gpu" || gpu=0
  q_cpu="$(cat      "$RUNDIR/queue_cpu"       2>/dev/null)"; is_num "$q_cpu" || q_cpu=0
  q_mem="$(cat      "$RUNDIR/queue_mem"       2>/dev/null)"; is_num "$q_mem" || q_mem=0
  q_io="$(cat       "$RUNDIR/queue_io"        2>/dev/null)"; is_num "$q_io"  || q_io=0
  slope="$(cat      "$RUNDIR/slope_temp"      2>/dev/null)"; is_num "$slope" || slope=0
  pred5="$(cat      "$RUNDIR/temp_pred_5s"    2>/dev/null)"; is_num "$pred5" || pred5="$soc"
  bottleneck="$(cat "$RUNDIR/bottleneck_state" 2>/dev/null)"; [ -z "$bottleneck" ] && bottleneck="none"
  osc="$(cat        "$RUNDIR/oscillation_count" 2>/dev/null)"; is_num "$osc" || osc=0
  last_action="$(cat "$RUNDIR/last_action"    2>/dev/null)"; [ -z "$last_action" ] && last_action="hold"

  local Wt="${W_THERMAL:-30}" Wp="${W_PRESSURE:-20}" Wd="${W_POWER:-10}"
  local Wl="${W_LATENCY:-25}" Wc="${W_CHANGE:-10}"  Ws="${W_STABLE:-5}"

  # thermal component — how this action moves us relative to target.
  local thermal_c=0 target="${SOC_TARGET_C10:-800}"
  case "$action" in
    hold)
      thermal_c=$(( (pred5 - target) / 5 ))
      [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
    clamp_background|uclamp_bg_clamp)
      thermal_c=$(( (pred5 - target) / 8 ))
      [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
    kgsl_tl_tighten|reduce_gpu_micro|tighten_gpu_margin)
      thermal_c=$(( (pred5 - target) / 12 ))
      [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
    reduce_gold_micro|tighten_cpu_margin)
      thermal_c=$(( (pred5 - target) / 16 ))
      [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
    reduce_prime_micro)
      thermal_c=$(( (pred5 - target) / 22 ))
      [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
    safe_rollback)
      thermal_c=0
      ;;
    *)
      thermal_c=$(( (pred5 - target) / 10 )); [ "$thermal_c" -lt 0 ] && thermal_c=0
      ;;
  esac
  [ "$thermal_c" -gt 100 ] && thermal_c=100

  # latency component — penalises actions that cut user-visible perf.
  local latency_c=0
  case "$action" in
    hold|relax_background|release_gpu_margin) latency_c=0 ;;
    clamp_background|uclamp_bg_clamp)         latency_c=5 ;;
    kgsl_tl_tighten)                          latency_c=10 ;;
    reduce_gpu_micro|tighten_gpu_margin)      latency_c=20 ;;
    reduce_gold_micro|tighten_cpu_margin)     latency_c=35 ;;
    reduce_prime_micro)                       latency_c=55 ;;
    safe_rollback)                            latency_c=90 ;;
  esac
  # If we are GPU-bound, reducing GPU is worse for latency than otherwise.
  case "$bottleneck" in
    gpu_bound)
      case "$action" in reduce_gpu_micro|tighten_gpu_margin) latency_c=$(( latency_c + 20 ));; esac
      ;;
    cpu_bound)
      case "$action" in reduce_prime_micro|reduce_gold_micro) latency_c=$(( latency_c + 15 ));; esac
      ;;
  esac

  # pressure component — relieves queues
  local pressure_c=$(( (q_cpu + q_mem + q_io) / 200 ))
  [ "$pressure_c" -gt 100 ] && pressure_c=100

  # power component — favour actions that reduce energy when not gaining perf.
  local power_c=0
  case "$action" in
    clamp_background|reduce_gpu_micro|reduce_gold_micro|reduce_prime_micro|kgsl_tl_tighten)
      power_c=0 ;;
    hold) power_c=15 ;;
    relax_background|release_gpu_margin) power_c=25 ;;
  esac

  # change-cost — discourage frequent switching.
  local change_c=0
  [ "$action" != "$last_action" ] && change_c=20

  # oscillation
  local osc_c=$(( osc * 8 )); [ "$osc_c" -gt 100 ] && osc_c=100

  local total=$(( Wt*thermal_c + Wp*pressure_c + Wd*power_c + Wl*latency_c + Wc*change_c + Ws*osc_c ))
  echo "$total"
}

# ── Control Barrier Function ────────────────────────────────────────────────
# Hard "this action is unsafe right now" gate. Returns 0 if safe, 1 if blocked.
ctl_cbf_check() {
  local action="$1"
  local pred5 fail_cnt budget
  pred5="$(cat      "$RUNDIR/temp_pred_5s"          2>/dev/null)"; is_num "$pred5" || pred5=400
  fail_cnt="$(cat   "$RUNDIR/tx_fail_count"         2>/dev/null)"; is_num "$fail_cnt" || fail_cnt=0
  budget="$(budget_remaining)"

  # If we are predicted to exceed the hard SoC limit, don't relax.
  local T_LIMIT="${SOC_HARD_LIMIT_C10:-850}"
  if [ "$pred5" -ge "$T_LIMIT" ]; then
    case "$action" in
      relax_background|release_gpu_margin|release_cpu_margin)
        logi "cbf:block pred5=$pred5 action=$action"
        return 1 ;;
    esac
  fi

  # If write failures piled up, only conservative (hold/safe) is allowed.
  if [ "$fail_cnt" -ge 3 ]; then
    case "$action" in
      reduce_prime_micro|reduce_gold_micro|reduce_gpu_micro) return 1 ;;
    esac
  fi

  # Budget exhausted → only hold/safe_rollback.
  if [ "$budget" -le 0 ]; then
    case "$action" in
      hold|safe_rollback) ;;
      *) return 1 ;;
    esac
  fi
  return 0
}

# ── Lyapunov function V (lower is better) ──────────────────────────────────
# V = (T-target)² + α·queue² + β·osc²   — used only as a tie-breaker / log.
ctl_lyapunov_v() {
  local soc q_cpu q_mem osc target terr perr v
  soc="$(cat   "$RUNDIR/est_soc_temp" 2>/dev/null)"; is_num "$soc" || soc=400
  q_cpu="$(cat "$RUNDIR/queue_cpu"    2>/dev/null)"; is_num "$q_cpu" || q_cpu=0
  q_mem="$(cat "$RUNDIR/queue_mem"    2>/dev/null)"; is_num "$q_mem" || q_mem=0
  osc="$(cat   "$RUNDIR/oscillation_count" 2>/dev/null)"; is_num "$osc" || osc=0
  target="${SOC_TARGET_C10:-800}"
  terr=$(( soc - target )); [ "$terr" -lt 0 ] && terr=$(( -terr ))
  perr=$(( (q_cpu + q_mem) / 100 ))
  v=$(( terr * terr / 10 + perr * perr / 5 + osc * osc * 3 ))
  echo "$v" > "$RUNDIR/lyapunov_v"
  echo "$v"
}

# ── Oscillation tracker ─────────────────────────────────────────────────────
ctl_track_oscillation() {
  local prev cur osc
  prev="$(cat "$RUNDIR/last_action" 2>/dev/null)"; [ -z "$prev" ] && prev="hold"
  cur="$1"
  osc="$(cat "$RUNDIR/oscillation_count" 2>/dev/null)"; is_num "$osc" || osc=0

  case "$prev/$cur" in
    reduce_gpu_micro/kgsl_tl_tighten|kgsl_tl_tighten/reduce_gpu_micro|\
    reduce_gold_micro/reduce_gpu_micro|reduce_gpu_micro/reduce_gold_micro|\
    clamp_background/relax_background|relax_background/clamp_background)
      osc=$(( osc + 1 )) ;;
    *)
      osc=$(( osc > 0 ? osc - 1 : 0 )) ;;
  esac
  echo "$osc" > "$RUNDIR/oscillation_count"
}

# ── Pareto candidate selection ──────────────────────────────────────────────
# Given a zone + bottleneck, returns the candidate action list.
ctl_candidates() {
  local zone="$1" bottleneck="$2" mode="$3" slope="$4"
  is_num "$slope" || slope=0
  case "$zone" in
    free)
      case "$bottleneck" in
        cpu_bound)    echo "hold release_cpu_margin relax_background" ;;
        gpu_bound)    echo "hold release_gpu_margin" ;;
        memory_bound) echo "hold relax_background" ;;
        io_bound)     echo "hold" ;;
        power_waste)  echo "hold clamp_background kgsl_tl_tighten" ;;
        *)            echo "hold clamp_background" ;;
      esac ;;
    normal)
      if [ "$slope" -gt 3 ]; then
        echo "hold clamp_background reduce_gpu_micro"
      else
        echo "hold clamp_background release_gpu_margin"
      fi ;;
    soft)
      if [ "$mode" = "game" ]; then
        echo "clamp_background kgsl_tl_tighten reduce_gpu_micro reduce_gold_micro"
      else
        echo "clamp_background kgsl_tl_tighten reduce_gpu_micro reduce_gold_micro reduce_prime_micro"
      fi ;;
    hard)
      if [ "$mode" = "game" ]; then
        echo "clamp_background kgsl_tl_tighten reduce_gpu_micro reduce_gold_micro reduce_prime_micro"
      else
        echo "clamp_background kgsl_tl_tighten reduce_gpu_micro reduce_gold_micro reduce_prime_micro"
      fi ;;
    critical)
      echo "safe_rollback reduce_prime_micro reduce_gpu_micro" ;;
    *)
      echo "hold" ;;
  esac
}

# ── Per-pkg action history (Bayesian-ish reward) ────────────────────────────
ctl_pkg_history_record() {
  local pkg="$1" action="$2" reward="$3"
  [ -n "$pkg" ] || pkg="default"
  local f="$HISTDIR/pkg_${pkg}.tsv"
  mkdir -p "$HISTDIR" 2>/dev/null
  echo "$(now_s)	$action	$reward" >> "$f"
  # rotate large files
  local sz; sz="$(wc -c < "$f" 2>/dev/null)"; sz="${sz:-0}"
  [ "$sz" -gt 32768 ] && tail -n 200 "$f" > "${f}.tmp" 2>/dev/null && mv "${f}.tmp" "$f" 2>/dev/null
}

# Returns a small bias (-20..+20) on an action's cost based on past reward.
ctl_pkg_history_bias() {
  local pkg="$1" action="$2"
  [ -n "$pkg" ] || pkg="default"
  local f="$HISTDIR/pkg_${pkg}.tsv"
  [ -f "$f" ] || { echo 0; return; }
  local sum=0 n=0 act r
  # Last 50 entries
  while IFS='	' read -r _ act r; do
    [ "$act" = "$action" ] || continue
    is_num "$r" || continue
    sum=$(( sum + r )); n=$(( n + 1 ))
  done < "$f"
  [ "$n" -lt 3 ] && { echo 0; return; }
  local avg=$(( sum / n ))
  [ "$avg" -gt 20 ] && avg=20
  [ "$avg" -lt -20 ] && avg=-20
  # Negative reward → bias UP its cost (i.e. discourage).
  echo $(( -avg ))
}

# ── Pick the best action across the candidates ──────────────────────────────
ctl_select_action() {
  local mode="${1:-active}" pkg="${2:-}"
  local soc pred5 slope bottleneck zone
  soc="$(cat        "$RUNDIR/est_soc_temp"     2>/dev/null)"; is_num "$soc"   || soc=400
  pred5="$(cat      "$RUNDIR/temp_pred_5s"     2>/dev/null)"; is_num "$pred5" || pred5="$soc"
  slope="$(cat      "$RUNDIR/slope_temp"       2>/dev/null)"; is_num "$slope" || slope=0
  bottleneck="$(cat "$RUNDIR/bottleneck_state" 2>/dev/null)"; [ -z "$bottleneck" ] && bottleneck="none"
  zone="$(ctl_zone_name "$pred5")"
  echo "$zone" > "$RUNDIR/thermal_zone"

  local candidates
  candidates="$(ctl_candidates "$zone" "$bottleneck" "$mode" "$slope")"

  local best="hold" best_cost=999999999 a c bias
  for a in $candidates; do
    if ! ctl_cbf_check "$a"; then continue; fi
    c="$(ctl_action_cost "$a")"
    bias="$(ctl_pkg_history_bias "$pkg" "$a")"
    c=$(( c + bias * 100 ))
    if [ "$c" -lt "$best_cost" ]; then
      best_cost="$c"; best="$a"
    fi
  done

  echo "$best_cost" > "$RUNDIR/cost_selected"
  echo "$best"      > "$RUNDIR/action_candidate"
  echo "$best"
}

# ── Reward computation (post-application) ───────────────────────────────────
# Compares predicted temp vs measured temp + perf side-effects.
ctl_compute_reward() {
  local pre_temp post_temp pre_q_cpu post_q_cpu r=0
  pre_temp="$(cat   "$RUNDIR/pre_action_temp"   2>/dev/null)"
  post_temp="$(cat  "$RUNDIR/est_soc_temp"      2>/dev/null)"
  pre_q_cpu="$(cat  "$RUNDIR/pre_action_q_cpu"  2>/dev/null)"
  post_q_cpu="$(cat "$RUNDIR/queue_cpu"         2>/dev/null)"
  is_num "$pre_temp"  || pre_temp=0
  is_num "$post_temp" || post_temp=0
  is_num "$pre_q_cpu"  || pre_q_cpu=0
  is_num "$post_q_cpu" || post_q_cpu=0
  # cooled = good
  r=$(( (pre_temp - post_temp) / 5 ))
  # but if queue spiked, bad
  local dq=$(( post_q_cpu - pre_q_cpu ))
  [ "$dq" -gt 0 ] && r=$(( r - dq / 200 ))
  echo "$r"
}
