#!/system/bin/sh
DATA="${MANJIRO_DATA:-/data/adb/manjiro_dinamic}"
printf "Content-Type: application/json\r\nCache-Control: no-cache\r\n\r\n"
cat "$DATA/state/status.json" 2>/dev/null || printf '{}'
