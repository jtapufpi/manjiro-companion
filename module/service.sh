#!/system/bin/sh
# MANJIRO DINAMIC — service.sh (bridge build)
#
# Pipeline:
#   1. system_monitor.apk (Encore port)  →  writes plain-text state file
#      ($DATA/state/apk_status) using app_process.
#   2. manjiro_bridge                    →  reads apk_status, writes
#      android_status.json (Manjiro schema) + heartbeat (timestamp_ms=…)
#      so manjirod sees helper_alive=true.
#   3. manjirod                          →  consumes heartbeat + JSON.

MODDIR="${0%/*}"
DATA="/data/adb/manjiro_dinamic"

# ── Filesystem layout (also created by customize.sh on first install) ──────
mkdir -p "$DATA/state" "$DATA/logs" "$DATA/config" \
         "$DATA/backup" "$DATA/dumps" "$DATA/history" \
         "$DATA/run" \
         "$DATA/dalvik-cache" \
         "$DATA/dalvik-cache/arm64" \
         "$DATA/dalvik-cache/arm" 2>/dev/null

chmod 0771 "$DATA/dalvik-cache" 2>/dev/null
chmod 0771 "$DATA/dalvik-cache/arm64" "$DATA/dalvik-cache/arm" 2>/dev/null
chown -R 0:0 "$DATA" 2>/dev/null

# SELinux contexts that allow ART to mmap the cache (best-effort).
chcon -R u:object_r:dalvikcache_data_file:s0 "$DATA/dalvik-cache" 2>/dev/null
chcon u:object_r:system_data_file:s0          "$DATA"             2>/dev/null

(
  # ── Wait for boot completed ────────────────────────────────────────────
  i=0
  while [ "$i" -lt 60 ]; do
    [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ] && break
    sleep 2; i=$(( i + 1 ))
  done
  # Give the framework an extra moment to publish services like
  # ActivityTaskManager / NotificationManager that the APK reflects into.
  sleep 8

  # ── Bootstrap default config on first boot ─────────────────────────────
  if [ -d "$MODDIR/config" ] && [ ! -f "$DATA/config/runtime.conf" ]; then
    cp -af "$MODDIR/config/"* "$DATA/config/" 2>/dev/null
  fi

  # ── Install/refresh the Manjiro Gaming APK (v1.7.3+) ───────────────────
  # Manjiro Gaming is a regular Android app, installed via pm. It reads the
  # daemon's status.json via root and provides the UI/notifications/game
  # library + per-game modes/quality slider/memory cleanup. Distinct from
  # system_monitor.apk (headless data collector).
  #
  # Also removes the legacy Manjiro Companion package if present, since
  # Gaming is the renamed successor with a different applicationId.
  GAMING_APK="$MODDIR/ManjiroGaming.apk"
  GAMING_PKG="com.jtapzg.manjirogaming"
  LEGACY_PKG="com.jtapzg.manjiro.companion"
  GAMING_VERS_FILE="$DATA/state/companion.installed_vers"
  if [ -f "$GAMING_APK" ]; then
    # Compare module version with what we last installed to avoid reinstalls.
    CUR_VERS="$(grep '^version=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2)"
    LAST_VERS="$(cat "$GAMING_VERS_FILE" 2>/dev/null)"
    PENDING="$DATA/state/companion.install_pending"
    if [ -f "$PENDING" ] || [ "$CUR_VERS" != "$LAST_VERS" ]; then
      if command -v pm >/dev/null 2>&1; then
        if pm list packages 2>/dev/null | grep -q "package:$LEGACY_PKG"; then
          pm uninstall --user 0 "$LEGACY_PKG" >>"$DATA/logs/companion_install.log" 2>&1
        fi
        pm install -r --user 0 "$GAMING_APK" >"$DATA/logs/companion_install.log" 2>&1
        if pm list packages 2>/dev/null | grep -q "package:$GAMING_PKG"; then
          echo "$CUR_VERS" > "$GAMING_VERS_FILE"
          rm -f "$PENDING" 2>/dev/null
        fi
      fi
    fi
  fi

  # ── Clean up any stale helper/bridge from a previous boot ──────────────
  pkill -f EncoreSysMon  2>/dev/null
  pkill -f manjiro_bridge 2>/dev/null
  rm -f "$DATA/state/helper.pid" \
        "$DATA/state/bridge.pid" \
        "$DATA/state/java.lock" 2>/dev/null

  # ── ART environment (prevents Aborted/SIGABRT 134 on app_process) ──────
  export ANDROID_DATA="$DATA"
  export ANDROID_RUNTIME_ROOT="${ANDROID_RUNTIME_ROOT:-/apex/com.android.runtime}"
  export ANDROID_TZDATA_ROOT="${ANDROID_TZDATA_ROOT:-/apex/com.android.tzdata}"
  export ANDROID_I18N_ROOT="${ANDROID_I18N_ROOT:-/apex/com.android.i18n}"
  export ANDROID_ART_ROOT="${ANDROID_ART_ROOT:-/apex/com.android.art}"
  export CLASSPATH="$MODDIR/system_monitor.apk"

  # Pick the right app_process for the device's primary ABI.
  APP_PROC="app_process"
  if [ -x /system/bin/app_process64 ] && [ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ]; then
    APP_PROC="app_process64"
  elif [ -x /system/bin/app_process32 ]; then
    APP_PROC="app_process32"
  fi

  # ── 1) System Monitor (the "eyes" — ported from Encore) ────────────────
  if [ -f "$MODDIR/system_monitor.apk" ]; then
    # NOTE on positional args: the APK accepts `<output_path> [lock_file]`.
    # We deliberately route its raw output to apk_status (Encore-style
    # plain-text key/value); the bridge below translates that into the
    # JSON schema manjirod expects.
    nohup "$APP_PROC" \
        -Djava.class.path="$CLASSPATH" / \
        --nice-name=EncoreSysMon \
        com.rem01gaming.systemmonitor.MainKt \
        "$DATA/state/apk_status" \
        "$DATA/state/java.lock" \
        >"$DATA/logs/monitor.log" 2>&1 &
    echo "$!" > "$DATA/state/helper.pid"
  fi

  # ── 2) Translator/heartbeat bridge (the new "spinal cord") ─────────────
  if [ -x "$MODDIR/system/bin/manjiro_bridge" ]; then
    nohup "$MODDIR/system/bin/manjiro_bridge" \
        --data "$DATA" --moddir "$MODDIR" \
        >"$DATA/logs/bridge.log" 2>&1 &
    echo "$!" > "$DATA/state/bridge.pid"
  fi

  # ── 3) Instance lock for the daemon ────────────────────────────────────
  LOCKDIR="$DATA/state/daemon.lock"
  if mkdir "$LOCKDIR" 2>/dev/null; then
    echo $$ > "$LOCKDIR/pid"
  else
    old_pid="$(cat "$LOCKDIR/pid" 2>/dev/null)"
    if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
      exit 0
    fi
    rm -rf "$LOCKDIR"
    mkdir "$LOCKDIR" 2>/dev/null
    echo $$ > "$LOCKDIR/pid"
  fi

  # ── 4) The daemon (the "brain") ────────────────────────────────────────
  if [ -x "$MODDIR/system/bin/manjirod" ]; then
    nohup "$MODDIR/system/bin/manjirod" \
        --data "$DATA" --moddir "$MODDIR" \
        >"$DATA/logs/daemon_stdout.log" 2>&1 &
    echo "$!" > "$DATA/state/daemon.pid"
  fi

  # ── 4b) Watchdog (respawns the daemon if it dies silently) ─────────────
  if [ -x "$MODDIR/system/bin/manjiro_watchdog" ]; then
    nohup "$MODDIR/system/bin/manjiro_watchdog" "$MODDIR" "$DATA" \
        >>"$DATA/logs/watchdog.log" 2>&1 &
    echo "$!" > "$DATA/state/watchdog.pid"
  fi

  # ── 5) WebUI ───────────────────────────────────────────────────────────
  if [ -x "$MODDIR/system/bin/manjiro_httpd" ]; then
    "$MODDIR/system/bin/manjiro_httpd" start >/dev/null 2>&1
  fi
) &
