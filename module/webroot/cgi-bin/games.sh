#!/system/bin/sh
DATA="${MANJIRO_DATA:-/data/adb/manjiro_dinamic}"
GAMES="$DATA/config/games.json"

# ── POST: add a package to games.json ────────────────────────────────────────
if [ "${REQUEST_METHOD:-}" = "POST" ]; then
  read -r BODY
  ACTION="$(echo "$BODY" | sed -n 's/.*action=\([^&]*\).*/\1/p')"
  PKG_RAW="$(echo "$BODY" | sed -n 's/.*pkg=\([^&]*\).*/\1/p')"
  # URL-decode the package name (only %XX and +) — keep it minimal.
  PKG="$(printf '%b' "$(echo "$PKG_RAW" | sed 's/+/ /g; s/%/\\x/g')")"
  case "$PKG" in
    *[!a-zA-Z0-9._]*|""|*..*) PKG="" ;;
  esac

  if [ "$ACTION" = "add" ] && [ -n "$PKG" ] && [ -f "$GAMES" ]; then
    if ! grep -q "\"$PKG\"" "$GAMES" 2>/dev/null; then
      TMP="$GAMES.tmp"
      sed 's/}\s*$/,/' "$GAMES" > "$TMP"
      cat >> "$TMP" <<EOF
  "$PKG": {"enabled": true, "class": "user_added", "weight": "standard", "target": "balanced", "thermal_target_c10": 800, "profile": "standard"}
}
EOF
      mv -f "$TMP" "$GAMES"
      echo "reload" > "$DATA/state/control.cmd" 2>/dev/null
    fi
    printf "Content-Type: application/json\r\n\r\n"
    printf '{"ok":true,"pkg":"%s"}' "$PKG"
    exit 0
  fi

  printf "Content-Type: application/json\r\nStatus: 400\r\n\r\n"
  printf '{"ok":false}'
  exit 0
fi

# ── GET: return the games.json verbatim ──────────────────────────────────────
printf "Content-Type: application/json\r\nCache-Control: no-cache\r\n\r\n"
cat "$GAMES" 2>/dev/null || printf '{}'
