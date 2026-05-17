#!/system/bin/sh
# policy — bottleneck classification, action selection, actuation budget

BUDGET_FILE="${DATA}/run/actuation_budget"
BUDGET_MAX="${ACTUATION_BUDGET_PER_MIN:-20}"
BUDGET_TS="${DATA}/run/budget_reset_ts"

budget_reset_if_needed() {
  local now; now="$(now_s)"
  local last; last="$(cat "$BUDGET_TS" 2>/dev/null)"; is_num "$last" || last=0
  if [ $(( now - last )) -ge 60 ]; then
    echo "$BUDGET_MAX" > "$BUDGET_FILE"
    echo "$now"        > "$BUDGET_TS"
  fi
}
budget_remaining() { cat "$BUDGET_FILE" 2>/dev/null || echo "$BUDGET_MAX"; }
budget_consume() {
  local cost="$1"
  local rem; rem="$(budget_remaining)"
  rem=$(( rem - cost ))
  [ "$rem" -lt 0 ] && rem=0
  echo "$rem" > "$BUDGET_FILE"
}
budget_ok() { local need="${1:-1}"; [ "$(budget_remaining)" -ge "$need" ]; }

classify_bottleneck() {
  local gpu q_cpu q_mem q_io temp_pred5 target
  gpu="$1"; q_cpu="$2"; q_mem="$3"; q_io="$4"; temp_pred5="$5"
  # v1.7.1: default 800 (80°C) — era 450 que era valor de bateria, bug.
  target="${THERMAL_TARGET_C10:-800}"

  local GPU_LOW="${GPU_BUSY_LOW:-62}" GPU_HIGH="${GPU_BUSY_HIGH:-88}"
  local CPU_HIGH="${CPU_PSI_HIGH_CPCT:-60}" MEM_HIGH="${MEM_PSI_HIGH_CPCT:-2500}" IO_HIGH="${IO_PSI_HIGH_CPCT:-2500}"

  # thermal first
  [ "$temp_pred5" -gt "$target" ] && echo "thermal_bound" && return

  # gpu
  if [ "$gpu" -ge "$GPU_HIGH" ] && [ "$q_cpu" -lt "$CPU_HIGH" ]; then
    echo "gpu_bound"; return
  fi

  # memory/io
  if [ "$q_mem" -gt "$MEM_HIGH" ] || [ "$q_io" -gt "$IO_HIGH" ]; then
    echo "mem_io_bound"; return
  fi

  # cpu
  if [ "$q_cpu" -gt "$CPU_HIGH" ] && [ "$gpu" -lt "$GPU_LOW" ]; then
    echo "cpu_bound"; return
  fi

  echo "none"
}

# Action costs
action_cost_budget() {
  case "$1" in
    uclamp_write)    echo 1;;
    eas_write)       echo 1;;
    schedutil_write) echo 1;;
    kgsl_tl)         echo 1;;
    devfreq_gov)     echo 3;;
    cpu_gpu_cap)     echo 4;;
    prime_microstep) echo 5;;
    rollback)        echo 10;;
    *)               echo 1;;
  esac
}

# Select action index (0-10) given state
select_action_level() {
  local cooling_demand="$1" bottleneck="$2" q_mem="$3" q_io="$4" gpu="$5"
  # q_mem/q_io in centipercent
  local MEM_HIGH="${MEM_PSI_HIGH_CPCT:-2500}" IO_HIGH="${IO_PSI_HIGH_CPCT:-2500}"

  [ "$cooling_demand" -le 0 ] && echo "hold" && return

  # If mem/io bound — never cut bus
  if [ "$bottleneck" = "mem_io_bound" ]; then
    # Reduce GPU if possible, else Little/Gold, preserve bus
    [ "$cooling_demand" -gt 60 ] && { echo "reduce_gold_micro"; return; }
    [ "$cooling_demand" -gt 30 ] && { echo "reduce_gpu_micro";  return; }
    echo "clamp_background"; return
  fi

  # GPU bound + temp safe — don't reduce GPU
  if [ "$bottleneck" = "gpu_bound" ]; then
    [ "$cooling_demand" -gt 50 ] && { echo "clamp_background"; return; }
    echo "hold"; return
  fi

  # Thermal bound — hierarchy
  if [ "$cooling_demand" -ge 80 ]; then echo "reduce_prime_micro"
  elif [ "$cooling_demand" -ge 60 ]; then echo "reduce_gold_micro"
  elif [ "$cooling_demand" -ge 40 ]; then echo "reduce_gpu_micro"
  elif [ "$cooling_demand" -ge 25 ]; then echo "kgsl_tl_tighten"
  elif [ "$cooling_demand" -ge 15 ]; then echo "uclamp_bg_clamp"
  elif [ "$cooling_demand" -ge 5  ]; then echo "clamp_background"
  else echo "hold"; fi
}

load_game_profile() {
  local pkg="$1" games_f="${DATA}/config/games.json"
  [ -f "$games_f" ] || { echo "unknown|normal|balanced|800|standard"; return; }
  # Naive field extract (no jq)
  local block; block="$(awk "/$pkg/{found=1} found{print} /\}/{if(found)exit}" "$games_f" 2>/dev/null)"
  local class weight target thermal profile
  class="$(echo "$block"  | grep '"class"'   | sed 's/.*: *"\([^"]*\)".*/\1/'   | head -1)"
  weight="$(echo "$block" | grep '"weight"'  | sed 's/.*: *"\([^"]*\)".*/\1/'   | head -1)"
  target="$(echo "$block" | grep '"target"'  | sed 's/.*: *"\([^"]*\)".*/\1/'   | head -1)"
  thermal="$(echo "$block" | grep '"thermal_target_c10"' | sed 's/.*: *\([0-9]*\).*/\1/' | head -1)"
  profile="$(echo "$block" | grep '"profile"' | sed 's/.*: *"\([^"]*\)".*/\1/' | head -1)"
  is_num "$thermal" || thermal=800
  echo "${class:-unknown}|${weight:-standard}|${target:-balanced}|$thermal|${profile:-standard}"
}
