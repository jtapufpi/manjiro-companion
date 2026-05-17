#!/system/bin/sh
# MANJIRO DINAMIC — apk_apply.sh
# Ponte: traduz comandos do Manjiro Gaming (APK) para parametros do motor.
#
# Uso (chamado pelo manjirod a partir de control.cmd):
#   apk_apply.sh mode    <LITE|BALANCED|PERFORMANCE|AUTO>
#   apk_apply.sh profile <pkg> <profile>
#   apk_apply.sh quality <pkg> <pct>
#
# Estrategia:
#   • Modo global vira "user.conf" (sobrescreve runtime.conf via . source).
#   • Quality/profile por jogo vira "$DATA/state/apk_quality.json".
#   • Cada execucao gera log em $LOGDIR/apk_bridge.log (consumido pelo APK).

MODDIR="/data/adb/modules/manjiro_dinamic"
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules_update/manjiro_dinamic"
DATA="/data/adb/manjiro_dinamic"
STATEDIR="$DATA/state"
CONFDIR="$DATA/config"
LOGDIR="$DATA/logs"

mkdir -p "$STATEDIR" "$CONFDIR" "$LOGDIR" 2>/dev/null

ts() { date +'%Y-%m-%d %H:%M:%S'; }
log() { echo "[$(ts)] $*" >> "$LOGDIR/apk_bridge.log"; }

write_user_conf() {
  # Recebe pares CHAVE=VALOR no stdin e escreve um user.conf novo.
  local out="$CONFDIR/user.conf"
  {
    echo "# MANJIRO user.conf — gerado pelo APK em $(ts)"
    cat
  } > "$out.tmp" 2>/dev/null
  mv -f "$out.tmp" "$out" 2>/dev/null
}

apply_mode() {
  local key="$1"
  case "$key" in
    LITE|lite)
      write_user_conf <<EOF
# Modo Lite (APK) — economiza bateria, temperatura mais conservadora.
APK_MODE=LITE
ENGINE_MODE=lite
SOC_TARGET_C10=750
ZONE_SOFT_C10=790
ZONE_HARD_C10=820
W_THERMAL=40
W_POWER=25
W_LATENCY=15
LOOP_ACTIVE_SEC=20
LOOP_GAME_SEC=4
EOF
      ;;
    PERFORMANCE|performance|perf)
      write_user_conf <<EOF
# Modo Performance (APK) — folga termica + foco em fluidez.
APK_MODE=PERFORMANCE
ENGINE_MODE=advanced
SOC_TARGET_C10=830
ZONE_SOFT_C10=860
ZONE_HARD_C10=880
W_THERMAL=20
W_POWER=5
W_LATENCY=40
LOOP_ACTIVE_SEC=12
LOOP_GAME_SEC=2
EOF
      ;;
    BALANCED|balanced)
      write_user_conf <<EOF
# Modo Balanced (APK) — equilibrio entre bateria e fluidez.
APK_MODE=BALANCED
ENGINE_MODE=advanced
SOC_TARGET_C10=800
ZONE_SOFT_C10=830
ZONE_HARD_C10=850
W_THERMAL=30
W_POWER=10
W_LATENCY=25
LOOP_ACTIVE_SEC=15
LOOP_GAME_SEC=3
EOF
      ;;
    AUTO|auto|"")
      # Auto: APK escolhe dinamicamente; aqui so removemos override.
      rm -f "$CONFDIR/user.conf" 2>/dev/null
      log "mode=AUTO (user.conf removido, motor decide)"
      return 0
      ;;
    *)
      log "mode_set: chave invalida='$key'"
      return 1
      ;;
  esac
  log "mode_set: $key aplicado em user.conf"
}

apply_profile() {
  local pkg="$1" profile="$2"
  [ -z "$pkg" ] || [ -z "$profile" ] && { log "profile_set: faltam args"; return 1; }
  local f="$STATEDIR/apk_profiles.json"
  # Atualiza/insere par pkg:profile no JSON simples linha-a-linha.
  local tmp="$f.tmp"
  : > "$tmp"
  if [ -f "$f" ]; then
    grep -v "^\"$pkg\":" "$f" >> "$tmp" 2>/dev/null
  fi
  echo "\"$pkg\":\"$profile\"" >> "$tmp"
  mv -f "$tmp" "$f"
  log "profile_set: $pkg -> $profile"
}

apply_quality() {
  local pkg="$1" pct="$2"
  [ -z "$pkg" ] || [ -z "$pct" ] && { log "quality: faltam args"; return 1; }
  local f="$STATEDIR/apk_quality.json"
  local tmp="$f.tmp"
  : > "$tmp"
  if [ -f "$f" ]; then
    grep -v "^\"$pkg\":" "$f" >> "$tmp" 2>/dev/null
  fi
  echo "\"$pkg\":$pct" >> "$tmp"
  mv -f "$tmp" "$f"

  # Aplica imediato no jogo atual: scaling de render via wm density
  # so faz sentido em foreground; se nao for o jogo da frente, fica
  # registrado para a proxima sessao.
  local fg
  fg="$(grep -o '"foreground_package":"[^"]*"' "$STATEDIR/status.json" 2>/dev/null \
        | head -1 | cut -d'"' -f4)"
  if [ -n "$pkg" ] && [ "$fg" = "$pkg" ]; then
    # Ajusta resolucao de render via wm size (best-effort; depende do dispositivo)
    local base_w base_h new_w new_h
    base_h="$(wm size 2>/dev/null | awk '/Physical size/ {print $3}' | cut -dx -f2)"
    base_w="$(wm size 2>/dev/null | awk '/Physical size/ {print $3}' | cut -dx -f1)"
    if [ -n "$base_w" ] && [ -n "$base_h" ] && [ "$pct" -ge 40 ] && [ "$pct" -le 100 ]; then
      new_w=$(( base_w * pct / 100 ))
      new_h=$(( base_h * pct / 100 ))
      if [ "$pct" = "100" ]; then
        wm size reset >/dev/null 2>&1
      else
        wm size "${new_w}x${new_h}" >/dev/null 2>&1
      fi
      log "quality: $pkg -> $pct% (wm size aplicado)"
    else
      log "quality: $pkg -> $pct% (registrado)"
    fi
  else
    log "quality: $pkg -> $pct% (registrado; sera aplicado quando abrir)"
  fi
}

case "$1" in
  mode)    apply_mode    "$2" ;;
  profile) apply_profile "$2" "$3" ;;
  quality) apply_quality "$2" "$3" ;;
  *)
    log "uso: apk_apply.sh {mode|profile|quality} ARGS..."
    exit 2
    ;;
esac
