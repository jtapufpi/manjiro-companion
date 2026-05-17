#!/system/bin/sh
# rollback — transactional node backup/restore
BACKUP_DIR="${DATA:-/data/adb/manjiro_dinamic}/backup"
mkdir -p "$BACKUP_DIR" 2>/dev/null

_backup_file() { echo "$BACKUP_DIR/${1}.db"; }

backup_node() {
  local node="$1" group="${2:-base}"
  [ -e "$node" ] || return 1
  local f; f="$(_backup_file "$group")"
  grep -qF "|$node|" "$f" 2>/dev/null && return 0
  local val; val="$(head -n 1 "$node" 2>/dev/null)"
  echo "$(date +%s)|$node|$val" >> "$f"
}

safe_write() {
  local node="$1" val="$2" group="${3:-base}"
  [ -e "$node" ] || return 1
  [ -w "$node" ] || return 1
  backup_node "$node" "$group"
  echo "$val" > "$node" 2>/dev/null
  local rc=$?
  [ $rc -ne 0 ] && logi_s "write_fail:$node" && return 1
  return 0
}

verify_write() {
  local node="$1" expected="$2"
  [ -r "$node" ] || return 1
  local actual; actual="$(head -n 1 "$node" 2>/dev/null)"
  [ "$actual" = "$expected" ] && return 0
  # Trim whitespace
  actual="$(echo "$actual" | tr -d ' \t')"
  expected2="$(echo "$expected" | tr -d ' \t')"
  [ "$actual" = "$expected2" ] && return 0
  return 1
}

rollback_group() {
  local group="${1:-base}"
  local f; f="$(_backup_file "$group")"
  [ -f "$f" ] || return 0
  while IFS='|' read -r ts node val; do
    [ -e "$node" ] && [ -w "$node" ] && echo "$val" > "$node" 2>/dev/null
  done < "$f"
  logi_s "rollback:$group"
}

rollback_all() {
  for g in base game thermal screenoff devfreq kgsl uclamp eas; do
    rollback_group "$g"
  done
  logi_s "rollback:all"
}

clear_group_backup() {
  local group="$1"
  rm -f "$(_backup_file "$group")" 2>/dev/null
}

# Transactional write set
_TX_FILE="${DATA:-/data/adb/manjiro_dinamic}/run/tx_ops"
_TX_OK="${DATA:-/data/adb/manjiro_dinamic}/run/tx_ok"
begin_transaction() { rm -f "$_TX_FILE" 2>/dev/null; echo "ok" > "$_TX_OK"; }
tx_write() {
  local node="$1" val="$2" group="${3:-game}"
  if safe_write "$node" "$val" "$group"; then
    echo "$node|$val" >> "$_TX_FILE" 2>/dev/null
  else
    echo "FAIL:$node" > "$_TX_OK"
    logi_s "tx_fail:$node=$val"
  fi
}
commit_transaction() {
  local s; s="$(cat "$_TX_OK" 2>/dev/null)"
  case "$s" in FAIL*) rollback_transaction; return 1;; esac
  rm -f "$_TX_FILE" "$_TX_OK" 2>/dev/null; return 0
}
rollback_transaction() {
  rollback_group game
  rm -f "$_TX_FILE" "$_TX_OK" 2>/dev/null
  local c; c="$(cat "${DATA:-/data/adb/manjiro_dinamic}/run/tx_fail_count" 2>/dev/null)"
  is_num "$c" || c=0
  echo $(( c + 1 )) > "${DATA:-/data/adb/manjiro_dinamic}/run/tx_fail_count"
  logi_s "tx:rollback"
}
