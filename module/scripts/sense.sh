#!/system/bin/sh
# MANJIRO DINAMIC — sense.sh
# State estimation: EMA, slope, CUSUM change-point, MPC horizon predictions,
# bottleneck classifier with persistence.
#
# Ported from system/bin/manjiro_sense.sh and re-anchored on the SoC thermal
# zones (NOT battery temp). Battery temp is exported as a separate signal but
# never influences tactical control.
#
# Source order: common.sh → math870.sh → sense.sh

# ── EMA ─────────────────────────────────────────────────────────────────────
sense_ema_update() {
  local key="$1" new_val="$2" alpha_inv="${3:-4}"
  local old updated
  old="$(cat "$RUNDIR/ema_$key" 2>/dev/null)"
  is_num "$old" || old="$new_val"
  is_num "$new_val" || return 0
  updated=$(( old + (new_val - old) / alpha_inv ))
  echo "$updated" > "$RUNDIR/ema_$key"
  echo "$updated"
}

# ── Slope (Δ per second) ───────────────────────────────────────────────────
sense_slope_update() {
  local key="$1" val="$2" interval="${3:-2}"
  local prev_val prev_ts now_ts dt slope
  prev_val="$(cat "$RUNDIR/slope_${key}_prev" 2>/dev/null)"
  prev_ts="$(cat "$RUNDIR/slope_${key}_ts" 2>/dev/null)"
  now_ts="$(now_s)"
  is_num "$prev_val" || prev_val="$val"
  is_num "$prev_ts"  || prev_ts="$now_ts"
  dt=$(( now_ts - prev_ts )); [ "$dt" -lt 1 ] && dt="$interval"
  slope=$(( (val - prev_val) / dt ))
  echo "$val"    > "$RUNDIR/slope_${key}_prev"
  echo "$now_ts" > "$RUNDIR/slope_${key}_ts"
  echo "$slope"  > "$RUNDIR/slope_${key}"
  echo "$slope"
}

# ── CUSUM change-point ─────────────────────────────────────────────────────
# Returns "1" if a step-change is detected, else "0".
sense_cusum() {
  local key="$1" val="$2" thresh="${3:-300}"
  local mean cu_pos cu_neg dev
  mean="$(cat "$RUNDIR/cusum_${key}_mean" 2>/dev/null)"
  cu_pos="$(cat "$RUNDIR/cusum_${key}_pos" 2>/dev/null)"
  cu_neg="$(cat "$RUNDIR/cusum_${key}_neg" 2>/dev/null)"
  is_num "$mean"   || mean="$val"
  is_num "$cu_pos" || cu_pos=0
  is_num "$cu_neg" || cu_neg=0
  dev=$(( val - mean ))
  cu_pos=$(( cu_pos + dev )); [ "$cu_pos" -lt 0 ] && cu_pos=0
  cu_neg=$(( cu_neg + dev )); [ "$cu_neg" -gt 0 ] && cu_neg=0
  # slowly drift mean toward val (EWMA, weight 1/16)
  mean=$(( mean + (val - mean) / 16 ))
  echo "$mean"   > "$RUNDIR/cusum_${key}_mean"
  echo "$cu_pos" > "$RUNDIR/cusum_${key}_pos"
  echo "$cu_neg" > "$RUNDIR/cusum_${key}_neg"
  if [ "$cu_pos" -gt "$thresh" ] || [ "$cu_neg" -lt "-$thresh" ]; then
    echo 0 > "$RUNDIR/cusum_${key}_pos"
    echo 0 > "$RUNDIR/cusum_${key}_neg"
    echo 1; return
  fi
  echo 0
}

# ── Main sense step ────────────────────────────────────────────────────────
# Inputs come from AS_* globals set by manjirod->read_state().
# Outputs are written to $RUNDIR/est_* and $RUNDIR/queue_* for control.sh.
sense_run() {
  local interval="${1:-2}"
  local raw_soc raw_bat raw_gpu raw_ma raw_cpsi raw_mpsi raw_ipsi

  # SoC die-temp is the TACTICAL signal (NOT battery). soc_thermal_c10() prefers
  # CPU/GPU/AOSS zones; falls back to max_thermal_c10() on unknown devices.
  if [ -n "${AS_MAX_TEMP:-}" ]; then
    raw_soc="$AS_MAX_TEMP"
  elif type soc_thermal_c10 >/dev/null 2>&1; then
    raw_soc="$(soc_thermal_c10)"
  else
    raw_soc="$(max_thermal_c10)"
  fi
  raw_bat="${AS_BAT_TEMP:-$(battery_temp_c10)}"
  raw_gpu="${AS_GPU:-$(gpu_busy)}"
  raw_ma="${AS_CURRENT:-$(battery_ma)}"
  raw_cpsi="${AS_CPU_PSI:-0}"
  raw_mpsi="${AS_MEM_PSI:-0}"
  raw_ipsi="${AS_IO_PSI:-0}"

  # EMA-smoothed
  local soc_s bat_s gpu_s ma_s cpsi_s mpsi_s ipsi_s
  soc_s="$(sense_ema_update  soc_temp "$raw_soc" "${EMA_ALPHA_INV_TEMP:-4}")"
  bat_s="$(sense_ema_update  bat_temp "$raw_bat" "${EMA_ALPHA_INV_TEMP:-4}")"
  gpu_s="$(sense_ema_update  gpu_busy "$raw_gpu" "${EMA_ALPHA_INV_GPU:-4}")"
  ma_s="$(sense_ema_update   current  "$raw_ma"  "${EMA_ALPHA_INV_CURRENT:-6}")"
  cpsi_s="$(sense_ema_update cpu_psi  "$raw_cpsi" "${EMA_ALPHA_INV_PSI:-3}")"
  mpsi_s="$(sense_ema_update mem_psi  "$raw_mpsi" "${EMA_ALPHA_INV_PSI:-3}")"
  ipsi_s="$(sense_ema_update io_psi   "$raw_ipsi" "${EMA_ALPHA_INV_PSI:-3}")"

  # Slopes
  local slope_temp slope_gpu
  slope_temp="$(sense_slope_update temp "$soc_s" "$interval")"
  slope_gpu="$(sense_slope_update  gpu  "$gpu_s" "$interval")"

  # CUSUM
  local cp_temp cp_gpu
  cp_temp="$(sense_cusum temp "$soc_s" "${CUSUM_THRESHOLD_TEMP:-300}")"
  cp_gpu="$(sense_cusum  gpu  "$gpu_s" "${CUSUM_THRESHOLD_GPU:-2000}")"
  if [ "$cp_temp" = "1" ]; then
    echo "$raw_soc" > "$RUNDIR/ema_soc_temp"; logi "sense:cp_temp_reset"
  fi
  if [ "$cp_gpu" = "1" ]; then
    echo "$raw_gpu" > "$RUNDIR/ema_gpu_busy"; logi "sense:cp_gpu_reset"
  fi

  # MPC horizon prediction — physics-based, not linear extrapolation.
  local prime_act gold_act little_act pred5 pred10
  prime_act="$(sd870_cluster_activity prime)"
  gold_act="$(sd870_cluster_activity  gold)"
  little_act="$(sd870_cluster_activity little)"
  pred5="$(sd870_mpc_predict  "$soc_s" "${MPC_HORIZON_SEC:-5}"  "$prime_act" "$gold_act" "$gpu_s")"
  pred10="$(sd870_mpc_predict "$soc_s" $(( ${MPC_HORIZON_SEC:-5} * 2 )) "$prime_act" "$gold_act" "$gpu_s")"

  # Export to RUNDIR for control core
  echo "$soc_s"      > "$RUNDIR/est_soc_temp"
  echo "$bat_s"      > "$RUNDIR/est_bat_temp"
  echo "$gpu_s"      > "$RUNDIR/est_gpu_busy"
  echo "$ma_s"       > "$RUNDIR/est_current"
  echo "$cpsi_s"     > "$RUNDIR/queue_cpu"
  echo "$mpsi_s"     > "$RUNDIR/queue_mem"
  echo "$ipsi_s"     > "$RUNDIR/queue_io"
  echo "$slope_temp" > "$RUNDIR/slope_temp"
  echo "$slope_gpu"  > "$RUNDIR/slope_gpu"
  echo "$pred5"      > "$RUNDIR/temp_pred_5s"
  echo "$pred10"     > "$RUNDIR/temp_pred_10s"
  echo "$prime_act"  > "$RUNDIR/cluster_prime_pct"
  echo "$gold_act"   > "$RUNDIR/cluster_gold_pct"
  echo "$little_act" > "$RUNDIR/cluster_little_pct"
}

# ── Bottleneck classifier with persistence ──────────────────────────────────
sense_classify_bottleneck() {
  local soc gpu q_cpu q_mem q_io ma slope target
  soc="$(cat   "$RUNDIR/est_soc_temp" 2>/dev/null)"; is_num "$soc"   || soc=400
  gpu="$(cat   "$RUNDIR/est_gpu_busy" 2>/dev/null)"; is_num "$gpu"   || gpu=0
  q_cpu="$(cat "$RUNDIR/queue_cpu"    2>/dev/null)"; is_num "$q_cpu" || q_cpu=0
  q_mem="$(cat "$RUNDIR/queue_mem"    2>/dev/null)"; is_num "$q_mem" || q_mem=0
  q_io="$(cat  "$RUNDIR/queue_io"     2>/dev/null)"; is_num "$q_io"  || q_io=0
  ma="$(cat    "$RUNDIR/est_current"  2>/dev/null)"; is_num "$ma"    || ma=0
  slope="$(cat "$RUNDIR/slope_temp"   2>/dev/null)"; is_num "$slope" || slope=0
  target="${SOC_TARGET_C10:-800}"

  local cpub=0 gpub=0 thermb=0 memb=0 iob=0 waste=0

  # cpu_bound
  [ "$q_cpu" -gt 200  ] && cpub=$(( cpub + 35 ))
  [ "$q_cpu" -gt 800  ] && cpub=$(( cpub + 30 ))
  [ "$gpu"   -lt 40   ] && cpub=$(( cpub + 20 ))
  [ "$soc"   -lt "$target" ] && cpub=$(( cpub + 15 ))

  # gpu_bound
  [ "$gpu"   -gt 75 ] && gpub=$(( gpub + 45 ))
  [ "$gpu"   -gt 90 ] && gpub=$(( gpub + 25 ))
  [ "$q_cpu" -lt 200 ] && gpub=$(( gpub + 20 ))
  [ "$soc"   -lt "$target" ] && gpub=$(( gpub + 10 ))

  # thermal_bound (SoC, not battery)
  local over=$(( soc - target ))
  [ "$over" -gt 0  ] && thermb=$(( over / 2 ))
  [ "$over" -gt 50 ] && thermb=100
  [ "$slope" -gt 3 ] && thermb=$(( thermb + 20 ))
  [ "$thermb" -gt 100 ] && thermb=100

  # memory_bound
  [ "$q_mem" -gt 300  ] && memb=$(( memb + 40 ))
  [ "$q_mem" -gt 1500 ] && memb=$(( memb + 40 ))

  # io_bound
  [ "$q_io"  -gt 300  ] && iob=$(( iob + 40 ))
  [ "$q_io"  -gt 1500 ] && iob=$(( iob + 40 ))

  # power_waste — burning energy while idle
  [ "$q_cpu" -lt 50 ] && [ "$gpu" -lt 20 ] && [ "$ma" -gt 500 ] && \
    [ "$slope" -gt 1 ] && waste=60

  for s in cpub gpub thermb memb iob waste; do
    eval "[ \$$s -gt 100 ] && $s=100"
  done

  echo "$cpub"   > "$RUNDIR/score_cpu_bound"
  echo "$gpub"   > "$RUNDIR/score_gpu_bound"
  echo "$thermb" > "$RUNDIR/score_thermal_bound"
  echo "$memb"   > "$RUNDIR/score_memory_bound"
  echo "$iob"    > "$RUNDIR/score_io_bound"
  echo "$waste"  > "$RUNDIR/score_power_waste"

  # Pick dominant + require persistence
  local dom="none" max_s=0 pair n v
  for pair in "cpu_bound:$cpub" "gpu_bound:$gpub" "thermal_bound:$thermb" \
              "memory_bound:$memb" "io_bound:$iob" "power_waste:$waste"; do
    n="${pair%%:*}"; v="${pair#*:}"
    [ "$v" -gt "$max_s" ] && [ "$v" -gt 30 ] && { max_s="$v"; dom="$n"; }
  done

  local prev_dom cand_cnt
  prev_dom="$(cat "$RUNDIR/bottleneck_candidate" 2>/dev/null)"
  cand_cnt="$(cat "$RUNDIR/bottleneck_cand_cnt" 2>/dev/null)"
  is_num "$cand_cnt" || cand_cnt=0
  if [ "$dom" = "$prev_dom" ]; then
    cand_cnt=$(( cand_cnt + 1 ))
  else
    cand_cnt=0
    echo "$dom" > "$RUNDIR/bottleneck_candidate"
  fi
  echo "$cand_cnt" > "$RUNDIR/bottleneck_cand_cnt"

  if [ "$cand_cnt" -ge 2 ]; then
    echo "$dom"   > "$RUNDIR/bottleneck_state"
    echo "$max_s" > "$RUNDIR/bottleneck_score"
  fi
  echo "$dom" > "$RUNDIR/bottleneck_live"
  echo "$dom"
}

# ── Battery emergency check (separate from tactical loop) ───────────────────
# Returns 1 if battery temp crossed the emergency line and SAFE must engage.
sense_battery_emergency() {
  local bat="${AS_BAT_TEMP:-0}"
  local emerg="${BATTERY_EMERGENCY_C10:-450}"
  [ "$bat" -ge "$emerg" ]
}

# ── Auto-detect heuristic ────────────────────────────────────────────────────
# Observe an unknown foreground package. If it sustains game-like load (GPU + CPU
# pressure with screen on) for AUTODETECT_HOLD_S seconds, promote it to the
# auto-detected list (state/auto_detected.json). Future foreground events for
# the same package will then short-circuit is_game() in manjirod.
autodetect_observe() {
  local pkg="$1"
  [ -n "$pkg" ] || return 1
  # Bail out if disabled.
  [ "${AUTODETECT_ENABLE:-1}" = "1" ] || return 1
  # Bail out for known-not-game packages (system UI, launchers, settings, …).
  case "$pkg" in
    com.android.systemui|com.android.launcher*|com.motorola.launcher*|\
    com.google.android.apps.nexuslauncher|com.android.settings|\
    com.android.vending|com.google.android.gms|com.android.providers.*|\
    android|com.android.shell|com.topjohnwu.magisk|me.weishu.kernelsu|\
    me.bmax.apatch|com.rifsxd.ksunext)
      return 1 ;;
  esac

  local sf="$STATEDIR/auto_detected.json"
  local now ts_first cnt
  now="$(now_s)"

  # If already auto-detected, nothing to do.
  if [ -f "$sf" ] && grep -q "\"$pkg\"" "$sf" 2>/dev/null; then
    return 0
  fi

  local gpu_busy cpu_psi mem_psi
  gpu_busy="${AS_GPU:-0}"
  cpu_psi="${AS_CPU_PSI:-0}"
  mem_psi="${AS_MEM_PSI:-0}"
  local thr_gpu="${AUTODETECT_GPU_PCT:-30}"
  local thr_cpu="${AUTODETECT_CPU_PSI:-100}"
  local hold_s="${AUTODETECT_HOLD_S:-15}"

  # Heuristic gate: GPU busy + CPU pressure simultaneously is the strongest
  # signal of a game (versus chat apps, browsers, video players).
  local game_like=0
  if [ "$gpu_busy" -ge "$thr_gpu" ] && [ "$cpu_psi" -ge "$thr_cpu" ]; then
    game_like=1
  fi

  local f="$RUNDIR/autodetect_${pkg}"
  if [ "$game_like" = "1" ]; then
    if [ -f "$f" ]; then
      ts_first="$(awk -F'|' 'NR==1{print $1}' "$f" 2>/dev/null)"; is_num "$ts_first" || ts_first="$now"
      cnt="$(awk -F'|' 'NR==1{print $2}' "$f" 2>/dev/null)"; is_num "$cnt" || cnt=0
      cnt=$(( cnt + 1 ))
    else
      ts_first="$now"; cnt=1
    fi
    echo "${ts_first}|${cnt}" > "$f"

    # Promote when the package has been game-like for >= hold_s seconds
    # AND we've accumulated at least 3 positive observations.
    local age=$(( now - ts_first ))
    if [ "$age" -ge "$hold_s" ] && [ "$cnt" -ge 3 ]; then
      autodetect_promote "$pkg"
      rm -f "$f" 2>/dev/null
    fi
  else
    # Reset the counter if we see a non-game-like sample (avoids promotion of
    # short bursty workloads like Drive sync or dex2oat).
    rm -f "$f" 2>/dev/null
  fi
  return 0
}

autodetect_promote() {
  local pkg="$1"
  [ -n "$pkg" ] || return 1
  local sf="$STATEDIR/auto_detected.json"
  local games="$CONFDIR/games.json"

  # Maintain a small JSON list at state/auto_detected.json keyed by package.
  if [ ! -f "$sf" ]; then echo '{}' > "$sf"; fi
  local ts; ts="$(now_s)"

  # Append into auto_detected.json (manual JSON, no jq).
  local tmp="$sf.tmp"
  # Drop the trailing '}' and re-emit with the new entry.
  local body; body="$(sed 's/}$//' "$sf" 2>/dev/null)"
  case "$body" in
    *\{*) printf '%s,\n  "%s": {"ts": %s, "source": "heuristic"}\n}\n' "$body" "$pkg" "$ts" > "$tmp" ;;
    *)    printf '{\n  "%s": {"ts": %s, "source": "heuristic"}\n}\n' "$pkg" "$ts" > "$tmp" ;;
  esac
  mv -f "$tmp" "$sf" 2>/dev/null

  # Also append a minimal entry into games.json so the existing is_game() grep
  # finds it on the next cycle. Keep the entry conservative (standard profile).
  if [ -f "$games" ] && ! grep -q "\"$pkg\"" "$games" 2>/dev/null; then
    local tmpg="$games.tmp"
    sed 's/}\s*$/,/' "$games" 2>/dev/null > "$tmpg"
    cat >> "$tmpg" <<EOF
  "$pkg": {"enabled": true, "class": "auto_detected", "weight": "standard", "target": "balanced", "thermal_target_c10": 800, "profile": "standard", "auto": true}
}
EOF
    mv -f "$tmpg" "$games" 2>/dev/null
  fi
  logi "autodetect:promote pkg=$pkg" 2>/dev/null
  return 0
}
