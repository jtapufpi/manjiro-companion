#!/system/bin/sh
MODDIR="${0%/*}"
DATA="/data/adb/manjiro_dinamic"
MOD_ID="manjiro_dinamic"

launch_webui() {
  # WebUI X (newest)
  if pm path com.dergoogler.mmrl.wx >/dev/null 2>&1; then
    am start -n "com.dergoogler.mmrl.wx/.ui.activity.webui.WebUIActivity" -e id "$MOD_ID" >/dev/null 2>&1 && return 0
  fi
  # MMRL
  if pm path com.dergoogler.mmrl >/dev/null 2>&1; then
    am start -n "com.dergoogler.mmrl/.ui.activity.webui.WebUIActivity" -e MOD_ID "$MOD_ID" >/dev/null 2>&1 && return 0
  fi
  # KSU WebUI Standalone
  if pm path io.github.a13e300.ksuwebui >/dev/null 2>&1; then
    am start -n "io.github.a13e300.ksuwebui/.WebUIActivity" -e id "$MOD_ID" >/dev/null 2>&1 && return 0
  fi
  # Fallback: open built-in httpd in browser.
  am start -a android.intent.action.VIEW -d "http://127.0.0.1:8080" >/dev/null 2>&1
  return 0
}

status() {
  local f="$DATA/state/status.json"
  [ -f "$f" ] || { echo "Manjiro: aguardando..."; return; }
  local mode soc_c10 bat_c10 pred5 action game safe
  mode="$(grep -o '"mode":"[^"]*"' "$f" | head -1 | cut -d'"' -f4)"
  soc_c10="$(grep -o '"soc_temp_c10":[0-9]*' "$f" | head -1 | cut -d: -f2)"
  [ -z "$soc_c10" ] && soc_c10="$(grep -o '"temp_c10":[0-9]*' "$f" | head -1 | cut -d: -f2)"
  bat_c10="$(grep -o '"battery_temp_c10":[0-9]*' "$f" | head -1 | cut -d: -f2)"
  pred5="$(grep -o '"temp_pred_5s_c10":[0-9]*' "$f" | head -1 | cut -d: -f2)"
  action="$(grep -o '"last_action":"[^"]*"' "$f" | head -1 | cut -d'"' -f4)"
  safe="$(grep -o '"safe_mode":[a-z]*' "$f" | head -1 | cut -d: -f2)"
  game="$(grep -o '"foreground_package":"[^"]*"' "$f" | head -1 | cut -d'"' -f4)"

  fmt_c10() { local c="${1:-0}"; printf '%d.%d°C' "$(( c / 10 ))" "$(( c % 10 ))"; }
  case "${mode:-IDLE}" in
    IDLE)     m="Em repouso" ;;
    ACTIVE)   m="Em uso" ;;
    ENGAGED)  m="Em jogo" ;;
    COOLDOWN) m="Resfriando" ;;
    SAFE)     m="Emergência" ;;
    *)        m="$mode" ;;
  esac
  echo "Manjiro Dinamic  ·  $m"
  echo "App     ${game:-—}"
  echo "SoC     $(fmt_c10 "${soc_c10:-0}")    em 5s $(fmt_c10 "${pred5:-0}")"
  echo "Bateria $(fmt_c10 "${bat_c10:-0}")"
  echo "Estado  ${action:-estável}"
  [ "$safe" = "true" ] && echo "        modo emergência ativo"
}

case "$1" in
  status)     status ;;
  reload)     echo "reload"     > "$DATA/state/control.cmd"; echo "ok" ;;
  reset-safe) echo "reset_safe" > "$DATA/state/control.cmd"; echo "ok" ;;
  ""|web|webui)
    # Called by the manager's "Open" / action button — launch best viewer.
    launch_webui
    ;;
  *) status ;;
esac
