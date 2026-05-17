#!/system/bin/sh
# io — sysfs actuation: uclamp, EAS, schedutil, KGSL, devfreq, CPU/GPU cap

# ── Helpers ───────────────────────────────────────────────────────────────────
avail_freqs() { cat "$1/scaling_available_frequencies" 2>/dev/null; }
avail_govs()  { cat "$1/available_governors" 2>/dev/null || cat "$1/scaling_available_governors" 2>/dev/null; }

choose_floor_freq() {
  local list="$1" target="$2" best=0 x
  for x in $list; do
    is_num "$x" || continue
    [ "$x" -le "$target" ] && [ "$x" -gt "$best" ] && best="$x"
  done
  [ "$best" -gt 0 ] && echo "$best" || echo "$target"
}

max_freq_from_list() {
  local max=0 x
  for x in $1; do is_num "$x" && [ "$x" -gt "$max" ] && max="$x"; done
  echo "$max"
}

# ── uclamp ────────────────────────────────────────────────────────────────────
_uclamp_val() {
  local node="$1" pct="$2" cur
  [ -e "$node" ] || { echo "$pct"; return; }
  cur="$(head -n 1 "$node" 2>/dev/null)"; cur="${cur%%.*}"
  is_num "$cur" && [ "$cur" -gt 100 ] 2>/dev/null && echo $(( pct * 1024 / 100 )) || echo "$pct"
}
write_uclamp() {
  local node="$1" pct="$2" group="${3:-game}"
  [ -e "$node" ] || return 1
  local val; val="$(_uclamp_val "$node" "$pct")"
  safe_write "$node" "$val" "$group"
}
apply_uclamp_set() {
  [ "${UCLAMP_ENABLE:-1}" = "1" ] || return 0
  local bg="$1" sysbg="$2" fg="$3" topmin="$4" topmax="$5" grp="${6:-base}"
  for base in /dev/cpuctl /sys/fs/cgroup; do
    [ -d "$base" ] || continue
    [ -e "$base/background/cpu.uclamp.max"        ] && write_uclamp "$base/background/cpu.uclamp.max"        "$bg"    "$grp"
    [ -e "$base/system-background/cpu.uclamp.max" ] && write_uclamp "$base/system-background/cpu.uclamp.max" "$sysbg" "$grp"
    [ -e "$base/foreground/cpu.uclamp.max"        ] && write_uclamp "$base/foreground/cpu.uclamp.max"        "$fg"    "$grp"
    [ -e "$base/top-app/cpu.uclamp.min"           ] && write_uclamp "$base/top-app/cpu.uclamp.min"           "$topmin" "$grp"
    [ -e "$base/top-app/cpu.uclamp.max"           ] && write_uclamp "$base/top-app/cpu.uclamp.max"           "$topmax" "$grp"
  done
}

# ── EAS ───────────────────────────────────────────────────────────────────────
apply_eas() {
  [ "${EAS_ENABLE:-1}" = "1" ] || return 0
  local up="$1" down="$2" grp="${3:-base}"
  [ -e /proc/sys/kernel/sched_upmigrate   ] && safe_write /proc/sys/kernel/sched_upmigrate   "$up"   "$grp"
  [ -e /proc/sys/kernel/sched_downmigrate ] && safe_write /proc/sys/kernel/sched_downmigrate "$down" "$grp"
  [ -e /proc/sys/kernel/sched_group_upmigrate   ] && safe_write /proc/sys/kernel/sched_group_upmigrate   "$(( up + 4 ))"   "$grp"
  [ -e /proc/sys/kernel/sched_group_downmigrate ] && safe_write /proc/sys/kernel/sched_group_downmigrate "$(( down + 5 ))" "$grp"
}

# ── schedutil ─────────────────────────────────────────────────────────────────
apply_schedutil() {
  [ "${SCHEDUTIL_ENABLE:-1}" = "1" ] || return 0
  local up="$1" down="$2" grp="${3:-base}" p govs
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    govs="$(avail_govs "$p")"
    echo "$govs" | grep -qw schedutil && safe_write "$p/scaling_governor" schedutil "$grp"
    [ -e "$p/schedutil/up_rate_limit_us"   ] && safe_write "$p/schedutil/up_rate_limit_us"   "$up"   "$grp"
    [ -e "$p/schedutil/down_rate_limit_us" ] && safe_write "$p/schedutil/down_rate_limit_us" "$down" "$grp"
  done
}

# ── KGSL ──────────────────────────────────────────────────────────────────────
kgsl_target_load() {
  [ "${KGSL_TARGET_LOAD_ENABLE:-1}" = "1" ] || return 0
  local tl="$1" grp="${2:-game}"
  local node="/sys/class/kgsl/kgsl-3d0/devfreq/adreno_tz/target_load"
  [ -e "$node" ] && safe_write "$node" "$tl" "$grp"
}
kgsl_base_setup() {
  [ "${KGSL_ENABLE:-1}" = "1" ] || return 0
  local base="/sys/class/kgsl/kgsl-3d0" n
  for n in force_clk_on force_bus_on force_rail_on force_no_nap; do
    [ -e "$base/$n" ] && safe_write "$base/$n" "0" base
  done
}

# ── devfreq / bus ─────────────────────────────────────────────────────────────
set_devfreq_gov() {
  [ "${DEVFREQ_ENABLE:-1}" = "1" ] || return 0
  local d="$1" gov="$2" grp="${3:-game}" av
  [ -d "$d" ] && [ -e "$d/governor" ] || return 1
  if [ -f "$d/available_governors" ]; then
    av="$(cat "$d/available_governors" 2>/dev/null)"
    echo "$av" | grep -qw "$gov" || return 1
  fi
  safe_write "$d/governor" "$gov" "$grp"
}
apply_game_bus() {
  [ "${DEVFREQ_ENABLE:-1}" = "1" ] && [ "${GAME_BUS_PERF:-1}" = "1" ] || return 0
  local d name
  for d in /sys/class/devfreq/*; do
    [ -d "$d" ] || continue; name="$(basename "$d")"
    case "$name" in *cpubw*|*llccbw*|*gpubw*|*memlat*) set_devfreq_gov "$d" performance game;; esac
  done
}
relax_bus() {
  [ "${DEVFREQ_ENABLE:-1}" = "1" ] || return 0
  local d name
  for d in /sys/class/devfreq/*; do
    [ -d "$d" ] || continue; name="$(basename "$d")"
    case "$name" in
      *llccbw*) set_devfreq_gov "$d" mem_latency base;;
      *cpubw*|*gpubw*) set_devfreq_gov "$d" bw_hwmon base;;
    esac
  done
}

# ── CPU/GPU cap ───────────────────────────────────────────────────────────────
cap_policy_pct() {
  local pol="$1" pct="$2" list max target val
  [ -d "$pol" ] || return 1
  list="$(avail_freqs "$pol")"; [ -n "$list" ] || list="$(cat "$pol/cpuinfo_max_freq" 2>/dev/null)"
  max="$(max_freq_from_list "$list")"; [ "$max" -gt 0 ] 2>/dev/null || return 1
  target=$(( max * pct / 100 ))
  val="$(choose_floor_freq "$list" "$target")"
  safe_write "$pol/scaling_max_freq" "$val" game
}
cap_cluster_pct() {
  local cluster="$1" pct="$2" p
  case "$cluster" in
    little) for p in /sys/devices/system/cpu/cpufreq/policy[0-3]; do [ -d "$p" ] && cap_policy_pct "$p" "$pct"; done;;
    gold)   for p in /sys/devices/system/cpu/cpufreq/policy[4-6]; do [ -d "$p" ] && cap_policy_pct "$p" "$pct"; done;;
    prime)  [ -d /sys/devices/system/cpu/cpufreq/policy7 ] && cap_policy_pct /sys/devices/system/cpu/cpufreq/policy7 "$pct";;
  esac
  echo "$pct" > "${DATA}/run/cap_${cluster}_pct"
}
cap_gpu_pct() {
  local pct="$1" base="/sys/class/kgsl/kgsl-3d0" list max target val n
  [ -d "$base" ] || return 1
  list="$(cat "$base/devfreq/available_frequencies" 2>/dev/null)"; [ -n "$list" ] || list="$(cat "$base/gpu_available_frequencies" 2>/dev/null)"
  max="$(max_freq_from_list "$list")"; [ "$max" -gt 0 ] 2>/dev/null || return 1
  target=$(( max * pct / 100 ))
  val="$(choose_floor_freq "$list" "$target")"
  for n in "$base/devfreq/max_freq" "$base/max_gpuclk"; do [ -e "$n" ] && safe_write "$n" "$val" game; done
  echo "$pct" > "${DATA}/run/cap_gpu_pct"
}
release_caps() {
  cap_cluster_pct prime 100; cap_cluster_pct gold 100; cap_cluster_pct little 100; cap_gpu_pct 100
}

# ── Boost kill ────────────────────────────────────────────────────────────────
kill_boosts() {
  local n
  for n in /sys/module/msm_performance/parameters/touchboost \
           /sys/module/cpu_boost/parameters/input_boost_ms \
           /sys/module/cpu_input_boost/parameters/input_boost_ms \
           /sys/module/cpu_input_boost/parameters/enable; do
    [ -e "$n" ] && safe_write "$n" "0" base
  done
  for n in /dev/stune/top-app/schedtune.boost /dev/stune/foreground/schedtune.boost \
           /dev/stune/background/schedtune.boost; do
    [ -e "$n" ] && safe_write "$n" "0" base
  done
}

# ── VM ────────────────────────────────────────────────────────────────────────
apply_vm_game() {
  [ "${VM_GAME_GUARD:-1}" = "1" ] || return 0
  [ -e /proc/sys/vm/swappiness             ] && safe_write /proc/sys/vm/swappiness             "${VM_SWAPPINESS_GAME:-0}"   game
  [ -e /proc/sys/vm/watermark_scale_factor ] && safe_write /proc/sys/vm/watermark_scale_factor "${VM_WATERMARK_GAME:-200}"  game
  [ -e /proc/sys/vm/compaction_proactiveness ] && safe_write /proc/sys/vm/compaction_proactiveness "0" game
  [ -e /proc/sys/vm/page-cluster           ] && safe_write /proc/sys/vm/page-cluster           "0"                          game
}

# ── cpuset ────────────────────────────────────────────────────────────────────
apply_cpuset_game() {
  [ "${GAME_CPUSET_ENABLE:-1}" = "1" ] || return 0
  for b in /dev/cpuset /sys/fs/cgroup/cpuset; do
    [ -d "$b" ] || continue
    [ -e "$b/background/cpus"        ] && safe_write "$b/background/cpus"        "0-3" game
    [ -e "$b/system-background/cpus" ] && safe_write "$b/system-background/cpus" "0-3" game
    [ -e "$b/foreground/cpus"        ] && safe_write "$b/foreground/cpus"        "0-6" game
    [ -e "$b/top-app/cpus"           ] && safe_write "$b/top-app/cpus"           "0-7" game
  done
}
