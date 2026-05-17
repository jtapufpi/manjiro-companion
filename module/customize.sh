#!/system/bin/sh
SKIPUNZIP=0
ui_print " "
ui_print "   __  __ ___ _  __ ___ ___ ____ ____ "
ui_print "  |  \/  |_  | \| ||_  || _ \  _ \  _ \\"
ui_print "  | \  / |/ /|    | _  ||   /|  _/|  _/"
ui_print "  |_|\/|_/___|_|\_||___||_|\_\_|  |_|  "
ui_print "        MANJIRO DINAMIC  v1.7.3"
ui_print "        + Manjiro Gaming "
ui_print " "

set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in \
  "$MODPATH/service.sh" "$MODPATH/action.sh" "$MODPATH/uninstall.sh" \
  "$MODPATH/post-fs-data.sh" "$MODPATH/boot-completed.sh" \
  "$MODPATH/system/bin/manjirod" "$MODPATH/system/bin/manjiroctl" \
  "$MODPATH/system/bin/manjiro_probe" "$MODPATH/system/bin/manjiro_bridge" \
  "$MODPATH/system/bin/manjiro_httpd" "$MODPATH/system/bin/manjiro_watchdog" \
  "$MODPATH/scripts/common.sh" "$MODPATH/scripts/rollback.sh" \
  "$MODPATH/scripts/policy.sh" "$MODPATH/scripts/thermal.sh" \
  "$MODPATH/scripts/sense.sh" "$MODPATH/scripts/control.sh" \
  "$MODPATH/scripts/math870.sh" "$MODPATH/scripts/io.sh" \
  "$MODPATH/scripts/atuadores.sh" "$MODPATH/scripts/mitigation.sh" \
  "$MODPATH/webroot/cgi-bin/status.sh" "$MODPATH/webroot/cgi-bin/cmd.sh" \
  "$MODPATH/webroot/cgi-bin/logs.sh" "$MODPATH/webroot/cgi-bin/capabilities.sh" \
  "$MODPATH/webroot/cgi-bin/android.sh" "$MODPATH/webroot/cgi-bin/games.sh"; do
  [ -f "$f" ] && set_perm "$f" 0 0 0755
done
[ -f "$MODPATH/system_monitor.apk" ] && set_perm "$MODPATH/system_monitor.apk" 0 0 0644
[ -f "$MODPATH/ManjiroGaming.apk" ] && set_perm "$MODPATH/ManjiroGaming.apk" 0 0 0644

DATA="/data/adb/manjiro_dinamic"
mkdir -p "$DATA/state" "$DATA/logs" "$DATA/config" \
         "$DATA/backup" "$DATA/dumps" "$DATA/history" \
         "$DATA/run" "$DATA/dalvik-cache/arm64" "$DATA/dalvik-cache/arm" 2>/dev/null
chmod 0771 "$DATA/dalvik-cache" "$DATA/dalvik-cache/arm64" "$DATA/dalvik-cache/arm" 2>/dev/null

# Bootstrap config on first install (overlay games.json + device_mitigation every install)
if [ -d "$MODPATH/config" ]; then
  [ ! -f "$DATA/config/base.conf" ]    && cp -af "$MODPATH/config/base.conf"    "$DATA/config/" 2>/dev/null
  [ ! -f "$DATA/config/runtime.conf" ] && cp -af "$MODPATH/config/runtime.conf" "$DATA/config/" 2>/dev/null
  cp -af "$MODPATH/config/games.json"              "$DATA/config/games.json"              2>/dev/null
  cp -af "$MODPATH/config/device_mitigation.json"  "$DATA/config/device_mitigation.json"  2>/dev/null
fi

# Mark the Manjiro Gaming APK for installation on first boot.
# service.sh picks this up and runs `pm install` once the framework is ready.
if [ -f "$MODPATH/ManjiroGaming.apk" ]; then
  : > "$DATA/state/companion.install_pending"
fi

ui_print "  Modulo instalado."
ui_print "  No primeiro boot, o app Manjiro Gaming sera"
ui_print "  instalado automaticamente."
ui_print " "
