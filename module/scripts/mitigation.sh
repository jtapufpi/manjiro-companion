#!/system/bin/sh
# mitigation — lê device_mitigation.json e materializa lista de tags bloqueadas
# em state/mitigation.list, lida em atuadores.sh por mit_allows()

MITIGATION_JSON="${DATA:-/data/adb/manjiro_dinamic}/config/device_mitigation.json"
MITIGATION_OUT="${DATA:-/data/adb/manjiro_dinamic}/state/mitigation.list"

mitigation_match_keys() {
  local manufacturer device model board soc kver
  manufacturer="$(getprop ro.product.manufacturer 2>/dev/null | tr 'A-Z' 'a-z')"
  device="$(getprop ro.product.device 2>/dev/null | tr 'A-Z' 'a-z')"
  model="$(getprop ro.product.model 2>/dev/null | tr 'A-Z ' 'a-z_')"
  board="$(getprop ro.board.platform 2>/dev/null | tr 'A-Z' 'a-z')"
  soc="$(getprop ro.soc.model 2>/dev/null)"
  kver="$(uname -r 2>/dev/null | cut -d. -f1-2)"

  echo "default"
  [ -n "$manufacturer" ] && [ -n "$device" ] && echo "$manufacturer/$device/edge20pro"
  [ -n "$manufacturer" ] && [ -n "$device" ] && echo "$manufacturer/$device"
  [ -n "$soc" ] && echo "qcom/$soc"
  [ -n "$board" ] && echo "qcom/$board"
  [ -n "$kver" ] && echo "kernel/$kver"
  # Vendor profile sniff
  if [ -f /system/build.prop ] && grep -qi "oplus\|realme" /system/build.prop 2>/dev/null; then
    echo "vendor/oplus"
  fi
}

mitigation_rebuild() {
  rm -f "$MITIGATION_OUT" 2>/dev/null
  [ -f "$MITIGATION_JSON" ] || { : > "$MITIGATION_OUT"; return 0; }

  # Extrai itens do default + cada chave que combina
  local keys; keys="$(mitigation_match_keys)"

  # Default
  awk '
    /"default"[[:space:]]*:/ { found=1; next }
    found && /"items"[[:space:]]*:/ { in_items=1; next }
    in_items {
      while (match($0, /"[A-Z_]+"/)) {
        item=substr($0, RSTART+1, RLENGTH-2)
        print item
        $0 = substr($0, RSTART + RLENGTH)
      }
      if ($0 ~ /\]/) { in_items=0; found=0 }
    }
  ' "$MITIGATION_JSON" >> "$MITIGATION_OUT" 2>/dev/null

  # Per-device
  for k in $keys; do
    [ "$k" = "default" ] && continue
    local esc; esc="$(echo "$k" | sed 's/[/.&]/\\&/g')"
    awk -v target="\"$k\"" '
      $0 ~ target { found=1; next }
      found && /"items"[[:space:]]*:/ { in_items=1; next }
      in_items {
        while (match($0, /"[A-Z_]+"/)) {
          item=substr($0, RSTART+1, RLENGTH-2)
          print item
          $0 = substr($0, RSTART + RLENGTH)
        }
        if ($0 ~ /\]/) { in_items=0; found=0 }
      }
    ' "$MITIGATION_JSON" >> "$MITIGATION_OUT" 2>/dev/null
  done

  # Deduplica
  sort -u -o "$MITIGATION_OUT" "$MITIGATION_OUT" 2>/dev/null
}
