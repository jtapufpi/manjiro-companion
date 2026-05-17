#!/system/bin/sh
# MANJIRO DINAMIC — math870.sh
#
# Snapdragon 870 / Adreno 650 / 12GB LPDDR5 math module.
# Calibrated for Motorola Edge 20 Pro (codename pstar / xt2153-1).
#
# Topology recap:
#   • cluster little (silver) : policy0, 4× Cortex-A55 @ up to 1.804 GHz
#   • cluster gold            : policy4, 3× Cortex-A77 @ up to 2.419 GHz
#   • cluster prime           : policy7, 1× Cortex-A77 @ up to 3.200 GHz
#   • GPU Adreno 650          : KGSL devfreq 257–670 MHz
#   • LPDDR5 12GB             : 6400 MT/s, dual-channel
#
# This file is intentionally pure-POSIX and side-effect-free (defines functions
# only). Source it after scripts/common.sh and scripts/io.sh.

# ── Constants (Hz × 1000 i.e. kHz, as Linux cpufreq reports) ────────────────
# v1.7.1: tabelas CALIBRADAS contra dump real do Edge 20 Pro (kernel
# 4.19.325-cip131-st15-perf). Os valores eram aproximados em v1.7.0 e
# divergiam em até 8 steps das OPPs reais do device.
SD870_SILVER_MAX_KHZ=1804800
SD870_GOLD_MAX_KHZ=2419200
SD870_PRIME_MAX_KHZ=3187200
SD870_GPU_MAX_HZ=670000000

SD870_SILVER_MIN_KHZ=300000
SD870_GOLD_MIN_KHZ=710400
SD870_PRIME_MIN_KHZ=844800
SD870_GPU_MIN_HZ=305000000

# Discrete freq tables (descending). EXATAS do dump.
SD870_SILVER_TABLE="1804800 1708800 1612800 1516800 1420800 1344000 1248000 1171200 1075200 979200 883200 787200 691200 614400 518400 403200 300000"
SD870_GOLD_TABLE="2419200 2342400 2246400 2150400 2054400 1958400 1862400 1766400 1670400 1574400 1478400 1382400 1286400 1171200 1056000 940800 825600 710400"
SD870_PRIME_TABLE="3187200 2841600 2745600 2649600 2553600 2457600 2361600 2265600 2169600 2073600 1977600 1862400 1747200 1632000 1516800 1401600 1305600 1190400 1075200 960000 844800"
SD870_GPU_TABLE_HZ="670000000 587000000 525000000 490000000 441600000 400000000 305000000"

# ── Cluster-aware floor caps (per the user's spec) ──────────────────────────
# Prime is throttled last and only down to ~78% so latency stays usable.
sd870_floor_pct() {
  case "$1" in
    prime)  echo "${PID_MIN_CPU_PCT:-78}" ;;
    gold)   echo 80 ;;
    little) echo 60 ;;
    gpu)    echo "${PID_MIN_GPU_PCT:-72}" ;;
    *)      echo 50 ;;
  esac
}

# ── Closest-OPP snap (returns a freq that actually exists on the SoC) ──────
sd870_snap_freq() {
  local cluster="$1" target="$2" list best=0 x
  case "$cluster" in
    little) list="$SD870_SILVER_TABLE" ;;
    gold)   list="$SD870_GOLD_TABLE"   ;;
    prime)  list="$SD870_PRIME_TABLE"  ;;
    gpu)    list="$SD870_GPU_TABLE_HZ" ;;
    *)      list="$2" ;;
  esac
  for x in $list; do
    is_num "$x" || continue
    [ "$x" -le "$target" ] && [ "$x" -gt "$best" ] && best="$x"
  done
  [ "$best" -gt 0 ] && echo "$best" || echo "$target"
}

# ── Percentage → freq for SD870 specifically ────────────────────────────────
sd870_pct_to_freq() {
  local cluster="$1" pct="$2" max
  case "$cluster" in
    little) max="$SD870_SILVER_MAX_KHZ" ;;
    gold)   max="$SD870_GOLD_MAX_KHZ"   ;;
    prime)  max="$SD870_PRIME_MAX_KHZ"  ;;
    gpu)    max="$SD870_GPU_MAX_HZ"     ;;
    *)      max=0 ;;
  esac
  sd870_snap_freq "$cluster" $(( max * pct / 100 ))
}

# ── Adaptive cooling demand → micro-step size ───────────────────────────────
# Returns the % to shave off in one step, given (cluster, demand 0..100, current_cap).
# Idea: large demand and high cap → big step; small demand or near floor → 1-2%.
sd870_cap_step() {
  local cluster="$1" demand="$2" cur_cap="$3"
  local floor; floor="$(sd870_floor_pct "$cluster")"
  local headroom=$(( cur_cap - floor ))
  [ "$headroom" -le 0 ] && { echo 0; return; }

  local step=2
  [ "$demand" -ge 40 ] && step=3
  [ "$demand" -ge 60 ] && step=5
  [ "$demand" -ge 80 ] && step=8
  # Prime is more latency-critical: cap step size.
  [ "$cluster" = "prime" ] && [ "$step" -gt 4 ] && step=4
  # Don't undershoot the floor.
  [ "$step" -gt "$headroom" ] && step="$headroom"
  echo "$step"
}

# ── MPC thermal coefficients (linearised heat-equation, SD870 die) ──────────
# Rise rate: gold-bound ≈ 1.5°C/s under sustained load, prime-bound ≈ 2.3°C/s.
# Decay: ≈ 0.35°C/s with vapour chamber at 25°C ambient.
# Encoded as c10 per second.
SD870_HEAT_GAIN_LITTLE=2
SD870_HEAT_GAIN_GOLD=15
SD870_HEAT_GAIN_PRIME=23
SD870_HEAT_GAIN_GPU=18
SD870_HEAT_DECAY_C10_S=4

# Predict temp_c10 N seconds ahead given current cluster activity & gpu busy.
# Inputs: cur_temp_c10 horizon_sec prime_pct gold_pct gpu_pct
# All pct = 0..100. Result in c10.
sd870_mpc_predict() {
  local cur="$1" horizon="$2" prime="$3" gold="$4" gpu="$5"
  is_num "$cur" || cur=400
  is_num "$horizon" || horizon=5
  is_num "$prime" || prime=0
  is_num "$gold"  || gold=0
  is_num "$gpu"   || gpu=0

  # Aggregate gain (c10/s) weighted by activity fraction.
  local gain=$(( SD870_HEAT_GAIN_PRIME * prime / 100 \
              + SD870_HEAT_GAIN_GOLD  * gold  / 100 \
              + SD870_HEAT_GAIN_GPU   * gpu   / 100 ))
  # Subtract passive dissipation (proportional to (temp - ambient_25°C)).
  local above_ambient=$(( cur - 250 )); [ "$above_ambient" -lt 0 ] && above_ambient=0
  local decay=$(( SD870_HEAT_DECAY_C10_S * above_ambient / 500 ))
  local net=$(( gain - decay ))

  local pred=$(( cur + net * horizon ))
  [ "$pred" -lt 250 ] && pred=250
  [ "$pred" -gt 1100 ] && pred=1100
  echo "$pred"
}

# ── Optimal cooling-demand split across resources ───────────────────────────
# Given total demand 0..100 and the per-cluster activity, returns "gpu gold prime"
# pct-cap reductions that sum to total_demand and prefer GPU+gold before prime.
sd870_demand_split() {
  local demand="$1" prime_pct="$2" gold_pct="$3" gpu_pct="$4"
  is_num "$demand"  || demand=0
  is_num "$prime_pct" || prime_pct=0
  is_num "$gold_pct"  || gold_pct=0
  is_num "$gpu_pct"   || gpu_pct=0

  local d_gpu=0 d_gold=0 d_prime=0 remain="$demand"

  # 1) GPU first if hot (only saves heat when actually busy)
  if [ "$gpu_pct" -gt 40 ]; then
    d_gpu=$(( remain * 50 / 100 )); [ "$d_gpu" -gt 12 ] && d_gpu=12
    remain=$(( remain - d_gpu ))
  fi
  # 2) Gold cluster
  [ "$remain" -gt 0 ] && {
    d_gold=$(( remain * 60 / 100 )); [ "$d_gold" -gt 10 ] && d_gold=10
    remain=$(( remain - d_gold ))
  }
  # 3) Prime cluster (last resort)
  [ "$remain" -gt 0 ] && {
    d_prime="$remain"
    [ "$d_prime" -gt 6 ] && d_prime=6
  }
  echo "$d_gpu $d_gold $d_prime"
}

# ── Edge 20 Pro / 12GB LPDDR5 memory tuner ─────────────────────────────────
# watermark_scale_factor and swappiness recommendations per memory pressure.
# Inputs: mem_psi_c100 (centipercent)
# Outputs: "swappiness watermark"
sd870_mem_targets() {
  local q="$1"
  is_num "$q" || q=0
  local sw=0 wm=200
  if [ "$q" -lt 200 ]; then
    sw=0;   wm=200
  elif [ "$q" -lt 800 ]; then
    sw=10;  wm=150
  elif [ "$q" -lt 2500 ]; then
    sw=20;  wm=120
  else
    sw=40;  wm=100
  fi
  echo "$sw $wm"
}

# ── KGSL / Adreno 650 — clean cap mapping ───────────────────────────────────
sd870_gpu_cap_freq_hz() {
  local pct="$1"
  sd870_snap_freq gpu $(( SD870_GPU_MAX_HZ * pct / 100 ))
}

# ── Cluster activity estimator (used by MPC) ────────────────────────────────
# Reads cpu_load_avg per policy from the kernel, returns 0..100.
sd870_cluster_activity() {
  local cluster="$1" pol cur_khz max_khz pct=0
  case "$cluster" in
    little) pol=/sys/devices/system/cpu/cpufreq/policy0; max_khz="$SD870_SILVER_MAX_KHZ" ;;
    gold)   pol=/sys/devices/system/cpu/cpufreq/policy4; max_khz="$SD870_GOLD_MAX_KHZ"   ;;
    prime)  pol=/sys/devices/system/cpu/cpufreq/policy7; max_khz="$SD870_PRIME_MAX_KHZ"  ;;
    *)      echo 0; return ;;
  esac
  [ -r "$pol/scaling_cur_freq" ] || { echo 0; return; }
  cur_khz="$(head -n 1 "$pol/scaling_cur_freq" 2>/dev/null)"
  is_num "$cur_khz" || { echo 0; return; }
  pct=$(( cur_khz * 100 / max_khz ))
  [ "$pct" -gt 100 ] && pct=100
  echo "$pct"
}
