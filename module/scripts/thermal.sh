#!/system/bin/sh
# thermal — PID controller + zone classification (LEGACY shim).
#
# v1.6 note: the advanced engine in scripts/control.sh + scripts/sense.sh now
# owns tactical decisions. This file is kept for backward compatibility and
# is still sourced by manjirod for PID + 5s prediction. All thresholds are
# anchored on SoC die-temp (c10), NOT battery.

# Zones in c10 (tenths °C) — SD870 SoC die.
ZONE_FREE="${ZONE_FREE_C10:-750}"
ZONE_NORMAL="${ZONE_NORMAL_C10:-800}"
ZONE_SOFT="${ZONE_SOFT_C10:-830}"
ZONE_HARD="${ZONE_HARD_C10:-850}"
ZONE_CRITICAL="${ZONE_CRITICAL_C10:-900}"

thermal_zone_name() {
  local t="$1"
  [ "$t" -ge "$ZONE_CRITICAL" ] && echo "critical" && return
  [ "$t" -ge "$ZONE_HARD"     ] && echo "hard"     && return
  [ "$t" -ge "$ZONE_SOFT"     ] && echo "soft"     && return
  [ "$t" -ge "$ZONE_NORMAL"   ] && echo "normal"   && return
  echo "free"
}

# State files for PID
PID_INT_FILE="${DATA}/run/pid_integral"
PID_ERR_FILE="${DATA}/run/pid_last_error"
PID_TS_FILE="${DATA:-/data/adb/manjiro_dinamic}/run/pid_last_ts"

pid_reset() { echo 0 > "$PID_INT_FILE"; echo 0 > "$PID_ERR_FILE"; echo "$(now_s)" > "$PID_TS_FILE"; }

pid_compute() {
  local pred="$1" target="${2:-${PID_TARGET_C10:-800}}"
  local KP="${PID_KP:-2}" KI="${PID_KI:-1}" KD="${PID_KD:-5}" IMAX="${I_MAX:-300}"

  local now_t; now_t="$(now_s)"
  local last_t; last_t="$(cat "$PID_TS_FILE" 2>/dev/null)"; is_num "$last_t" || last_t="$now_t"
  local dt=$(( now_t - last_t )); [ "$dt" -lt 1 ] && dt=1; [ "$dt" -gt 10 ] && dt=10
  echo "$now_t" > "$PID_TS_FILE"

  local error=$(( pred - target ))
  local integral; integral="$(cat "$PID_INT_FILE" 2>/dev/null)"; is_num "$integral" || integral=0
  local last_err; last_err="$(cat "$PID_ERR_FILE" 2>/dev/null)"; is_num "$last_err" || last_err="$error"

  integral=$(( integral + error * dt ))
  [ "$integral" -gt "$IMAX"    ] && integral="$IMAX"
  [ "$integral" -lt "-$IMAX"   ] && integral="-$IMAX"
  echo "$integral" > "$PID_INT_FILE"
  echo "$error"    > "$PID_ERR_FILE"

  local deriv=$(( (error - last_err) / dt ))
  local demand=$(( KP * error / 10 + KI * integral / 100 + KD * deriv / 10 ))
  demand="$(clamp "$demand" 0 100)"
  echo "$demand"
}

# Linear 5s extrapolation — kept as fallback if MPC isn't available.
predict_temp_5s() {
  local temp_now="$1"
  local prev_temp; prev_temp="$(cat "${DATA}/run/pred_prev_temp" 2>/dev/null)"; is_num "$prev_temp" || prev_temp="$temp_now"
  local prev_ts; prev_ts="$(cat "${DATA}/run/pred_prev_ts" 2>/dev/null)"; is_num "$prev_ts" || prev_ts="$(now_s)"
  local now_ts; now_ts="$(now_s)"
  local dt=$(( now_ts - prev_ts )); [ "$dt" -lt 1 ] && dt=1
  local slope=$(( (temp_now - prev_temp) * 10 / dt ))
  local pred=$(( temp_now + slope * 5 / 10 ))
  echo "$temp_now" > "${DATA}/run/pred_prev_temp"
  echo "$now_ts"   > "${DATA}/run/pred_prev_ts"
  echo "$slope"    > "${DATA}/run/slope_temp_c10_per_10s"
  echo "$pred"
}
