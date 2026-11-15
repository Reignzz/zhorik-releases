#!/usr/bin/env bash
# =====================================================================
#  Полный бэкап телефона на Mac ПЕРЕД установкой Beamox (без root).
#  Сохраняет: список приложений и их состояние, APK ваших приложений,
#  системные настройки, приложения по умолчанию, фото/видео/документы,
#  контакты/SMS/журнал звонков (если Android разрешит).
#  Использование: ./backup.sh [папка]   (по умолчанию ~/BeamoxBackup/<дата>)
#                 NO_MEDIA=1 ./backup.sh — без фото/видео (быстро)
# =====================================================================
set -uo pipefail
ADB="${ADB:-adb}"
OUT="${1:-$HOME/BeamoxBackup/$(date +%Y-%m-%d_%H-%M-%S)}"
mkdir -p "$OUT"/{apks,media,data}
ok(){ printf "  \033[32m✓\033[0m %s\n" "$*"; }
warn(){ printf "  \033[33m!\033[0m %s\n" "$*"; }
sh(){ "$ADB" shell "$@" 2>/dev/null | tr -d '\r'; }

echo "  Папка бэкапа: $OUT"

# 1. Устройство
sh getprop > "$OUT/device_props.txt"
ok "Сведения об устройстве: $(sh getprop ro.product.model), Android $(sh getprop ro.build.version.release)"

# 2. Приложения и их состояние
sh pm list packages -u --user 0 | sed 's/^package://' | sort > "$OUT/packages_all.txt"
sh pm list packages --user 0    | sed 's/^package://' | sort > "$OUT/packages_installed.txt"
sh pm list packages -d --user 0 | sed 's/^package://' | sort > "$OUT/packages_disabled.txt"
sh pm list packages -3 --user 0 | sed 's/^package://' | sort > "$OUT/packages_user.txt"
ok "Приложения: $(wc -l < "$OUT/packages_installed.txt" | xargs) установлено, $(wc -l < "$OUT/packages_disabled.txt" | xargs) отключено"

# 3. Приложения по умолчанию (рабочий стол, ассистент, SMS, звонилка, браузер)
: > "$OUT/roles.txt"
for r in HOME ASSISTANT SMS DIALER BROWSER; do
  echo "android.app.role.$r=$(sh cmd role get-role-holders android.app.role.$r | head -1)" >> "$OUT/roles.txt"
done
ok "Приложения по умолчанию сохранены"

# 4. Все системные настройки
for ns in global secure system; do sh settings list "$ns" > "$OUT/settings_$ns.txt"; done
ok "Системные настройки: $(cat "$OUT"/settings_*.txt | wc -l | xargs) параметров"

# 5. APK установленных вами приложений (чтобы вернуть, даже если пропадут из Play)
n=0
while read -r pkg; do
  [[ -z "$pkg" ]] && continue
  mkdir -p "$OUT/apks/$pkg"
  for p in $(sh pm path "$pkg" | sed 's/^package://'); do
    "$ADB" pull "$p" "$OUT/apks/$pkg/" >/dev/null 2>&1
  done
  n=$((n+1))
done < "$OUT/packages_user.txt"
ok "APK приложений: $n"

# 6. Контакты, SMS, звонки (Android 15 может не дать доступ — тогда используйте Google-бэкап)
for pair in "contacts:content://com.android.contacts/data" "sms:content://sms" "calllog:content://call_log/calls"; do
  name="${pair%%:*}"; uri="${pair#*:}"
  if sh content query --uri "$uri" > "$OUT/data/$name.txt" && [[ -s "$OUT/data/$name.txt" ]]; then
    ok "$name: $(grep -c '^Row' "$OUT/data/$name.txt") записей"
  else
    rm -f "$OUT/data/$name.txt"; warn "$name: Android не разрешил чтение по USB — сохраните через Google (см. ниже)"
  fi
done

# 7. Фото, видео, документы, загрузки, медиа мессенджеров
if [[ "${NO_MEDIA:-0}" != "1" ]]; then
  echo "  Копирую фото/видео/файлы (может занять время)…  размер на телефоне: $(sh du -sh /sdcard/ | cut -f1)"
  for d in DCIM Pictures Movies Music Download Documents Recordings Ringtones Notifications Alarms Android/media; do
    if [[ -n "$(sh ls -d /sdcard/$d 2>/dev/null)" ]]; then
      mkdir -p "$OUT/media/$(dirname "$d")"
      "$ADB" pull "/sdcard/$d" "$OUT/media/$(dirname "$d")/" >/dev/null 2>&1 && ok "Файлы: $d"
    fi
  done
else
  warn "Фото/видео пропущены (NO_MEDIA=1)"
fi

cat > "$OUT/README.txt" <<TXT
Бэкап телефона $(sh getprop ro.product.model) от $(date).
Вернуть всё как было:   bash restore_full.sh "$OUT"
Вернуть ещё и файлы:    bash restore_full.sh "$OUT" --push-media
TXT
echo "$OUT" > "$HOME/BeamoxBackup/LATEST"

echo
ok "Бэкап готов: $OUT ($(du -sh "$OUT" | cut -f1))"
warn "Дополнительно рекомендуется облачная копия Google (пароли Wi-Fi, переписки, данные приложений):"
warn "  Телефон → Настройки → Система → Резервное копирование → «Создать копию»"
