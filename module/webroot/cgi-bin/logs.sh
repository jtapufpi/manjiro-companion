#!/system/bin/sh
DATA="${MANJIRO_DATA:-/data/adb/manjiro_dinamic}"
printf "Content-Type: application/json\r\nCache-Control: no-cache\r\n\r\n"
_lines="$(tail -n 80 "$DATA/logs/main.log" 2>/dev/null | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n')"
printf '{"lines":"%s"}' "$_lines"
