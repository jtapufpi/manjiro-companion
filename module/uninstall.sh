#!/system/bin/sh
DATA="/data/adb/manjiro_dinamic"
# Kill daemon and helper
daemon_pid="$(cat "$DATA/state/daemon.pid" 2>/dev/null)"
[ -n "$daemon_pid" ] && kill "$daemon_pid" 2>/dev/null
helper_pid="$(cat "$DATA/state/helper.pid" 2>/dev/null)"
[ -n "$helper_pid" ] && kill "$helper_pid" 2>/dev/null
bridge_pid="$(cat "$DATA/state/bridge.pid" 2>/dev/null)"
[ -n "$bridge_pid" ] && kill "$bridge_pid" 2>/dev/null
pkill -f manjirod 2>/dev/null
pkill -f manjiro_httpd 2>/dev/null
pkill -f manjiro_bridge 2>/dev/null
pkill -f EncoreSysMon 2>/dev/null
pkill -f SystemMonitor 2>/dev/null
# Rollback nodes
BACKUP="$DATA/backup/base_nodes.db"
if [ -f "$BACKUP" ]; then
  while IFS='|' read -r ts node val; do
    [ -e "$node" ] && [ -w "$node" ] && echo "$val" > "$node" 2>/dev/null
  done < "$BACKUP"
fi
# Remove lock
rm -rf "$DATA/state/daemon.lock" 2>/dev/null
