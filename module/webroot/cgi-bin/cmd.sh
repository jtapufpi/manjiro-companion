#!/system/bin/sh
DATA="${MANJIRO_DATA:-/data/adb/manjiro_dinamic}"
MODDIR="/data/adb/modules/manjiro_dinamic"
# busybox httpd passes POST body via stdin, length in CONTENT_LENGTH
_len="${CONTENT_LENGTH:-0}"
if [ "$_len" -gt 0 ] 2>/dev/null; then
  _body="$(dd bs=1 count="$_len" 2>/dev/null)"
else
  _body="$(cat 2>/dev/null)"
fi
_cmd="$(echo "$_body" | grep -o '"cmd":"[^"]*"' | cut -d'"' -f4 | head -1)"
case "$_cmd" in
  reload|reset_safe|game_on|game_off|dump)
    echo "$_cmd" > "$DATA/state/control.cmd" ;;
  daemon_restart)
    pkill -f manjirod 2>/dev/null
    sleep 1
    nohup "$MODDIR/system/bin/manjirod" \
      --data "$DATA" --moddir "$MODDIR" \
      >"$DATA/logs/daemon_stdout.log" 2>&1 & ;;
esac
printf "Content-Type: application/json\r\n\r\n{}"
