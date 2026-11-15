#!/usr/bin/env bash
# =====================================================================
#  Полный откат телефона к состоянию из бэкапа (backup.sh).
#  ./restore_full.sh                       — из последнего бэкапа
#  ./restore_full.sh ~/BeamoxBackup/<дата> — из конкретного
#  флаги: --keep-beamox  — не удалять Beamox
#         --push-media   — вернуть на телефон фото/видео/файлы из бэкапа
# =====================================================================
set -uo pipefail
ADB="${ADB:-adb}"
PKG=com.beamox.launcher
B=""; KEEP=0; MEDIA=0
for a in "$@"; do
  case "$a" in --keep-beamox) KEEP=1 ;; --push-media) MEDIA=1 ;; *) B="$a" ;; esac
done
[[ -z "$B" ]] && B="$(cat "$HOME/BeamoxBackup/LATEST" 2>/dev/null)"
[[ -d "$B" ]] || { echo "Бэкап не найден. Укажите папку: ./restore_full.sh ~/BeamoxBackup/<дата>"; exit 1; }
ok(){ printf "  \033[32m✓\033[0m %s\n" "$*"; }
sh(){ "$ADB" shell "$@" 2>/dev/null | tr -d '\r'; }
echo "Восстанавливаю из: $B"

# 1. Вернуть удалённые приложения и их включённость
now="$(sh pm list packages --user 0 | sed 's/^package://' | sort)"
n=0
while read -r p; do
  [[ -z "$p" ]] && continue
  if ! grep -qx "$p" <<<"$now"; then sh cmd package install-existing --user 0 "$p" >/dev/null; n=$((n+1)); fi
done < "$B/packages_installed.txt"
dis_now="$(sh pm list packages -d --user 0 | sed 's/^package://')"
for p in $dis_now; do grep -qx "$p" "$B/packages_disabled.txt" || sh pm enable --user 0 "$p" >/dev/null; done
ok "Системные приложения возвращены: $n"

# 2. Переустановить ваши приложения, если их нет
for d in "$B"/apks/*/; do
  p="$(basename "$d")"
  grep -qx "$p" <<<"$(sh pm list packages --user 0 | sed 's/^package://')" && continue
  apks=("$d"*.apk); [[ -e "${apks[0]}" ]] || continue
  "$ADB" install-multiple -r "${apks[@]}" >/dev/null 2>&1 && ok "Переустановлено: $p"
done

# 3. Настройки, которые меняют Beamox/optimize.sh — возвращаем исходные значения
restore_key(){ # ns key
  local v; v="$(grep -m1 "^$2=" "$B/settings_$1.txt" | cut -d= -f2-)"
  if [[ -n "$v" ]]; then sh settings put "$1" "$2" "$v" >/dev/null; else sh settings delete "$1" "$2" >/dev/null; fi
}
for k in window_animation_scale transition_animation_scale animator_duration_scale; do restore_key global "$k"; done
for k in sound_effects_enabled ringtone notification_sound alarm_alert screen_brightness screen_brightness_mode accelerometer_rotation; do
  restore_key system "$k"
done
ok "Анимации, звуки, яркость, поворот — как были"

# 4. Приложения по умолчанию
while IFS='=' read -r role holder; do
  [[ -n "$holder" ]] && sh cmd role add-role-holder "$role" "$holder" >/dev/null
done < "$B/roles.txt"
ok "Рабочий стол и приложения по умолчанию — как были"

# 5. Убрать Beamox
sh dumpsys deviceidle whitelist -$PKG >/dev/null
if [[ $KEEP == 0 ]]; then "$ADB" uninstall "$PKG" >/dev/null 2>&1 && ok "Beamox удалён"; fi

# 6. Файлы (по желанию — Beamox их не трогает)
if [[ $MEDIA == 1 && -d "$B/media" ]]; then
  for d in "$B"/media/*; do "$ADB" push "$d" /sdcard/ >/dev/null 2>&1 && ok "Файлы: $(basename "$d")"; done
fi

echo
ok "Готово. Обои верните вручную: долгое нажатие на рабочем столе → Обои (Android не даёт сохранить их без root)."
