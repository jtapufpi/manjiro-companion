#!/system/bin/sh
# MANJIRO DINAMIC — shared primitives (sourced by scripts)

is_num()  { case "$1" in ''|*[!0-9-]*) return 1;; *) return 0;; esac; }
is_unum() { case "$1" in ''|*[!0-9]*) return 1;; *) return 0;; esac; }
clamp() {
  local v="$1" lo="$2" hi="$3"
  [ "$v" -lt "$lo" ] 2>/dev/null && v="$lo"
  [ "$v" -gt "$hi" ] 2>/dev/null && v="$hi"
  echo "$v"
}

logi_base() {
  local logfile="$1"; shift
  [ "${LOG_ENABLE:-1}" = "1" ] || return 0
  echo "$(date '+%H:%M:%S') $*" >> "$logfile" 2>/dev/null
  local sz; sz="$(wc -c < "$logfile" 2>/dev/null)"; sz="${sz:-0}"
  [ "$sz" -gt 204800 ] && tail -n 300 "$logfile" > "${logfile}.tmp" 2>/dev/null && \
    mv "${logfile}.tmp" "$logfile" 2>/dev/null
}
logi()   { logi_base "${LOGDIR:-/data/adb/manjiro_dinamic/logs}/main.log"   "$@"; }
logi_m() { logi_base "${LOGDIR:-/data/adb/manjiro_dinamic/logs}/monitor.log" "$@"; }
logi_s() { logi_base "${LOGDIR:-/data/adb/manjiro_dinamic/logs}/safety.log"  "$@"; }

read_node() { [ -r "$1" ] && head -n 1 "$1" 2>/dev/null || echo "NA"; }

atomic_write() {
  local dest="$1" content="$2"
  printf '%s' "$content" > "${dest}.tmp" 2>/dev/null
  mv "${dest}.tmp" "$dest" 2>/dev/null
}

# Thermal zone temp converter — handles micro-/milli-/centi-degree variants.
_tz_to_c10() {
  local t="$1" c10
  is_num "$t" || { echo 0; return; }
  [ "$t" -lt 0 ] 2>/dev/null && { echo 0; return; }
  [ "$t" -gt 200000 ] 2>/dev/null && { echo 0; return; }
  if [ "$t" -gt 10000 ]; then    c10=$(( t / 100 ))
  elif [ "$t" -gt 1000 ]; then   c10=$(( t / 10 ))
  elif [ "$t" -gt 100 ]; then    c10="$t"
  else                           c10=$(( t * 10 )); fi
  echo "$c10"
}

# SD870-aware SoC die-temp: prefer thermal zones whose type matches the
# Snapdragon CPU/GPU cluster sensors. Falls back to overall max if nothing
# matches (so this never returns 0 on an unfamiliar device).
soc_thermal_c10() {
  local d type t max=0 c10
  for d in /sys/class/thermal/thermal_zone*; do
    [ -d "$d" ] || continue
    type="$(cat "$d/type" 2>/dev/null)"
    case "$type" in
      cpu*|*cpuss*|*-cpu-*|*-cpu*|silver*|gold*|prime*|big*|little*|*-l3-*|\
      gpu*|*gpuss*|*-gpu-*|aoss*|*aoss-*|soc-thermal*|ap-*|*-skin*)
        : ;;
      *)
        continue ;;
    esac
    t="$(cat "$d/temp" 2>/dev/null | head -n 1)"
    c10="$(_tz_to_c10 "$t")"
    [ "$c10" -gt "$max" ] && max="$c10"
  done
  if [ "$max" -eq 0 ]; then
    max_thermal_c10
    return
  fi
  echo "$max"
}

# Legacy "absolute max" reader kept for callers that want the worst case
# across the whole platform (modem, charge ic, etc.). Tactical control
# should use soc_thermal_c10.
max_thermal_c10() {
  local z t max=0 c10
  for z in /sys/class/thermal/thermal_zone*/temp; do
    [ -r "$z" ] || continue
    t="$(head -n 1 "$z" 2>/dev/null)"
    c10="$(_tz_to_c10 "$t")"
    [ "$c10" -gt "$max" ] && max="$c10"
  done
  echo "$max"
}

# GPU-specific thermal: try kgsl/gpuss/gpu-* zones, fall back to soc.
gpu_thermal_c10() {
  local d type t c10
  for d in /sys/class/thermal/thermal_zone*; do
    [ -d "$d" ] || continue
    type="$(cat "$d/type" 2>/dev/null)"
    case "$type" in
      gpu*|*gpuss*|*-gpu-*) ;;
      *) continue ;;
    esac
    t="$(cat "$d/temp" 2>/dev/null | head -n 1)"
    c10="$(_tz_to_c10 "$t")"
    [ "$c10" -gt 0 ] && echo "$c10" && return
  done
  soc_thermal_c10
}
battery_temp_c10() {
  local t; t="$(cat /sys/class/power_supply/battery/temp 2>/dev/null | head -n 1)"
  is_num "$t" || { echo 0; return; }
  # kernel reports in tenths already (410 = 41.0°C)
  [ "$t" -gt 1000 ] && echo $(( t / 10 )) || echo "$t"
}
battery_level() {
  cat /sys/class/power_supply/battery/capacity 2>/dev/null | head -n 1 || echo 0
}
battery_status_str() {
  cat /sys/class/power_supply/battery/status 2>/dev/null | head -n 1 || echo "Unknown"
}
battery_ma() {
  local v; v="$(cat /sys/class/power_supply/battery/current_now 2>/dev/null | head -n 1)"
  is_num "$v" || { echo 0; return; }
  # microamps → milliamps, preserve sign
  local sign=1; [ "$v" -lt 0 ] && { sign=-1; v=$(( -v )); }
  [ "$v" -gt 10000 ] && v=$(( v / 1000 ))
  echo $(( v * sign ))
}
gpu_busy() {
  local n v
  for n in /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage \
           /sys/class/kgsl/kgsl-3d0/devfreq/gpu_busy_percentage; do
    [ -r "$n" ] || continue
    v="$(head -n 1 "$n" 2>/dev/null)"; set -- $v; echo "${1:-0}"; return
  done; echo 0
}
psi_some_c100() {
  # Returns avg10 some in centipercent
  local file="$1" line val int dec
  [ -r "$file" ] || { echo 0; return; }
  line="$(grep '^some ' "$file" 2>/dev/null | head -n 1)"
  val="$(echo "$line" | sed -n 's/.*avg10=\([0-9.]*\).*/\1/p')"
  [ -n "$val" ] || { echo 0; return; }
  int="${val%%.*}"; dec="${val#*.}"; dec="${dec}00"; dec="${dec%${dec#??}}"
  is_num "$int" || int=0; is_num "$dec" || dec=0
  echo $(( int * 100 + dec ))
}
psi_full_c100() {
  local file="$1" line val int dec
  [ -r "$file" ] || { echo 0; return; }
  line="$(grep '^full ' "$file" 2>/dev/null | head -n 1)"
  val="$(echo "$line" | sed -n 's/.*avg10=\([0-9.]*\).*/\1/p')"
  [ -n "$val" ] || { echo 0; return; }
  int="${val%%.*}"; dec="${val#*.}"; dec="${dec}00"; dec="${dec%${dec#??}}"
  is_num "$int" || int=0; is_num "$dec" || dec=0
  echo $(( int * 100 + dec ))
}
screen_on() {
  local s; s="$(dumpsys power 2>/dev/null | grep -m 1 -E 'Display Power: state=|mWakefulness=')"
  echo "$s" | grep -qiE 'ON|Awake' && return 0
  [ -r /sys/class/graphics/fb0/blank ] && [ "$(cat /sys/class/graphics/fb0/blank 2>/dev/null)" = "0" ] && return 0
  return 1
}
find_fg_app() {
  local out pkg
  out="$(cmd window dump 2>/dev/null | grep -m 1 -E 'mCurrentFocus|topResumedActivity')"
  pkg="$(echo "$out" | sed -n 's/.* \([a-zA-Z0-9_][a-zA-Z0-9_.]*\)\/.*/\1/p' | head -n 1)"
  [ -n "$pkg" ] && echo "$pkg" && return 0
  out="$(dumpsys window 2>/dev/null | grep -m 1 'mCurrentFocus')"
  echo "$out" | sed -n 's/.* \([a-zA-Z0-9_][a-zA-Z0-9_.]*\)\/.*/\1/p' | head -n 1
}
now_ms() { date +%s%3N 2>/dev/null || echo $(( $(date +%s) * 1000 )); }
now_s()  { date +%s; }
c10_to_str() { local c="$1"; echo "$(( c / 10 )).$(( c % 10 ))"; }
