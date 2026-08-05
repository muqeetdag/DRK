#!/system/bin/sh
# DRKMUQEET installer
SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=true
LATESTARTSERVICE=true
REPLACE=""

DRK_HOME=/data/adb/drk

print_modname() {
  ui_print "  💀👻🗿 DRKMUQEET — system optimizer & privacy toolkit (begonia)"
}

on_install() {
  ui_print "  Installing module files..."
}

set_permissions() {
  # /data runtime home
  mkdir -p "$DRK_HOME/logs" "$DRK_HOME/state"
  # first-install: seed the config file (never overwrite user edits)
  if [ ! -f "$DRK_HOME/drk.conf" ]; then
    cp -f "$MODPATH/config/drk.conf" "$DRK_HOME/drk.conf"
    chmod 0644 "$DRK_HOME/drk.conf"
    ui_print "  Created $DRK_HOME/drk.conf (boot-time settings)"
  else
    ui_print "  Keeping existing $DRK_HOME/drk.conf"
  fi
  # keep runtime logs readable
  chmod 0777 "$DRK_HOME/logs" "$DRK_HOME/state" 2>/dev/null
  # executables
  set_perm "$MODPATH/system/bin/drk"    0 0 0755
  set_perm "$MODPATH/system/bin/drkd"   0 0 0755
  set_perm_recursive "$MODPATH/system/bin/drk.d" 0 0 0755 0644
  ui_print "  Done. Run 'drk help' in a root shell."
}
