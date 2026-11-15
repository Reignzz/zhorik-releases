#!/usr/bin/env bash
# =====================================================================
#  Установка лаунчера Beamox на Moto G57 с Mac — одной командой.
#  1) Телефон: Настройки → О телефоне → 7 раз нажать «Номер сборки»
#     → Настройки → Система → Для разработчиков → «Отладка по USB» = ВКЛ
#  2) Подключите кабель, на телефоне нажмите «Разрешить» (галочка «Всегда»)
#  3) В Терминале Mac:  bash install.sh
#     Флаги: --apk путь.apk   — поставить свой APK вместо скачивания
#            --no-debloat     — не удалять лишние приложения
#            --yes            — не задавать вопросов
#            --no-backup      — пропустить бэкап (не рекомендуется)
#            --no-media       — бэкап без фото/видео (быстрее)
# =====================================================================
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPTS="$(cd "$HERE/.." && pwd)"
PKG="com.beamox.launcher"
APK_URL="https://github.com/reignzz/zhorik-releases/releases/download/beamox-launcher/BeamoxLauncher.apk"
APK=""; DEBLOAT=1; YES=0; BACKUP=1; NOMEDIA=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --no-debloat) DEBLOAT=0; shift ;;
    --yes|-y) YES=1; shift ;;
    --no-backup) BACKUP=0; shift ;;
    --no-media) NOMEDIA=1; shift ;;
    *) echo "Неизвестный флаг: $1"; exit 1 ;;
  esac
done

b(){ printf "\n\033[1;34m▶ %s\033[0m\n" "$*"; }
ok(){ printf "  \033[32m✓\033[0m %s\n" "$*"; }
warn(){ printf "  \033[33m!\033[0m %s\n" "$*"; }
die(){ printf "\n\033[31m✗ %s\033[0m\n" "$*"; exit 1; }
ask(){ [[ $YES == 1 ]] && return 0; read -r -p "  $1 [Д/н] " a; [[ -z "$a" || "$a" =~ ^[ДдYy] ]]; }

echo "
   ██████  ██  █████  ███    ███  ██████  ██   ██
   ██   ██ ██ ██   ██ ████  ████ ██    ██  ██ ██
   ██████  ██ ███████ ██ ████ ██ ██    ██   ███
   ██   ██ ██ ██   ██ ██  ██  ██ ██    ██  ██ ██
   ██████  ██ ██   ██ ██      ██  ██████  ██   ██   Launcher"

# ---------------------------------------------------------------- 1. ADB
b "1/8 Инструменты Android (adb)"
if command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
else
  PT="$HOME/.beamox/platform-tools"
  if [[ ! -x "$PT/adb" ]]; then
    echo "  Скачиваю Android platform-tools от Google…"
    mkdir -p "$HOME/.beamox"
    curl -fL --progress-bar -o /tmp/platform-tools.zip \
      https://dl.google.com/android/repository/platform-tools-latest-darwin.zip || die "Не удалось скачать platform-tools"
    unzip -qo /tmp/platform-tools.zip -d "$HOME/.beamox" && rm -f /tmp/platform-tools.zip
    xattr -dr com.apple.quarantine "$PT" 2>/dev/null || true
  fi
  ADB="$PT/adb"
fi
export ADB
ok "adb: $ADB"

# ---------------------------------------------------------------- 2. Телефон
b "2/8 Ищу телефон по кабелю"
"$ADB" start-server >/dev/null 2>&1
for i in $(seq 1 60); do
  st="$("$ADB" get-state 2>/dev/null || true)"
  [[ "$st" == "device" ]] && break
  if "$ADB" devices | grep -q unauthorized; then
    [[ $i == 1 ]] && warn "На телефоне появилось окно «Разрешить отладку по USB?» — нажмите «Разрешить» (и «Всегда»)."
  elif [[ $i == 1 ]]; then
    warn "Телефон не виден. Проверьте: кабель для данных, «Отладка по USB» включена, режим USB «Передача файлов»."
  fi
  sleep 2
done
[[ "$("$ADB" get-state 2>/dev/null)" == "device" ]] || die "Телефон не подключён. Повторите после включения отладки по USB."
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
ANDROID="$("$ADB" shell getprop ro.build.version.release | tr -d '\r')"
ok "Найден: $MODEL (Android $ANDROID)"
[[ "$MODEL" == *g57* || "$MODEL" == *G57* ]] || warn "Это не Moto G57 — продолжаю, всё должно работать на любом Android 10+."

# ---------------------------------------------------------------- 2b. Бэкап
b "Полный бэкап телефона перед изменениями"
if [[ $BACKUP == 1 ]]; then
  NO_MEDIA=$NOMEDIA "$SCRIPTS/backup.sh" || die "Бэкап не удался — установка остановлена, телефон не изменён."
  echo "  Откат в любой момент:  bash $SCRIPTS/restore_full.sh"
else
  warn "Бэкап пропущен (--no-backup)"
fi

# ---------------------------------------------------------------- 3. APK
b "3/8 Лаунчер Beamox"
if [[ -z "$APK" ]]; then
  for c in "$HERE/BeamoxLauncher.apk" "$SCRIPTS/../app/build/outputs/apk/release/app-release.apk"; do
    [[ -f "$c" ]] && { APK="$c"; break; }
  done
fi
if [[ -z "$APK" ]]; then
  APK="/tmp/BeamoxLauncher.apk"
  echo "  Скачиваю последнюю сборку…"
  curl -fL --progress-bar -o "$APK" "$APK_URL" || die "Не удалось скачать APK. Скачайте вручную: $APK_URL и запустите: bash install.sh --apk путь.apk"
fi
ok "APK: $APK"
out="$("$ADB" install -r -g "$APK" 2>&1)"
if [[ "$out" != *Success* ]]; then
  if [[ "$out" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
    warn "Стоит версия с другой подписью — переустанавливаю"
    "$ADB" uninstall "$PKG" >/dev/null 2>&1
    out="$("$ADB" install -g "$APK" 2>&1)"
  fi
  [[ "$out" == *Success* ]] || die "Установка не удалась: $out"
fi
ok "Установлено"

# ---------------------------------------------------------------- 4. Разрешения
b "4/8 Разрешения (микрофон, звонки, системные настройки, заставка)"
for p in RECORD_AUDIO CAMERA CALL_PHONE POST_NOTIFICATIONS; do
  "$ADB" shell pm grant "$PKG" "android.permission.$p" >/dev/null 2>&1
done
"$ADB" shell appops set "$PKG" WRITE_SETTINGS allow >/dev/null 2>&1        # звуки, яркость
"$ADB" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1   # голос «без рук» открывает разделы
"$ADB" shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow >/dev/null 2>&1  # самообновление
"$ADB" shell cmd notification allow_dnd "$PKG" >/dev/null 2>&1            # «Не беспокоить» в пункте управления
"$ADB" shell dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1
ok "Разрешения выданы"

# ---------------------------------------------------------------- 5. Лаунчер и ассистент по умолчанию
b "5/8 Beamox — главный экран и ассистент по умолчанию"
if "$ADB" shell cmd role add-role-holder android.app.role.HOME "$PKG" 2>&1 | grep -qiE "error|exception"; then
  "$ADB" shell cmd package set-home-activity "$PKG/.MainActivity" >/dev/null 2>&1
fi
ok "Кнопка «Домой» → Beamox"
"$ADB" shell cmd role add-role-holder android.app.role.ASSISTANT "$PKG" >/dev/null 2>&1 \
  && ok "Долгое нажатие/жест ассистента → голос Beamox" || warn "Ассистента можно выбрать вручную: Настройки → Приложения → По умолчанию → Цифровой помощник"

# ---------------------------------------------------------------- 6. Ключи, почты, заставка
b "6/8 Ключи, почты, соцсети, заставка"
DATA="/sdcard/Android/data/$PKG/files"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 3   # создаёт папку приложения
"$ADB" shell mkdir -p "$DATA" >/dev/null 2>&1
for img in lockscreen.png homescreen.png; do
  [[ -f "$HERE/brand/$img" ]] && "$ADB" push "$HERE/brand/$img" "$DATA/$img" >/dev/null && ok "Своя картинка: $img"
done
if [[ -f "$HERE/secrets.json" ]]; then
  "$ADB" push "$HERE/secrets.json" "$DATA/provision.json" >/dev/null && ok "Ключ Claude, пароли почт и настройки переданы (в телефоне хранятся зашифрованно)"
else
  warn "Нет файла secrets.json — ключ Claude и пароли почт можно ввести в Beamox → Настройки."
  warn "Или: cp $HERE/secrets.example.json $HERE/secrets.json, заполните и запустите install.sh снова."
fi
# Перезапуск: лаунчер применит заставку Beamox, звуки и ключи
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
ok "Заставка Beamox и звуки применяются"

# ---------------------------------------------------------------- 7. Очистка
b "7/8 Удаление рекламы и лишних приложений"
if [[ $DEBLOAT == 1 ]]; then
  "$SCRIPTS/debloat.sh" --dry-run
  if ask "Удалить эти приложения? (вернуть можно: scripts/restore.sh)"; then
    "$SCRIPTS/debloat.sh"
  else warn "Пропущено"; fi
else warn "Пропущено (--no-debloat)"; fi

# ---------------------------------------------------------------- 8. Оптимизация
b "8/8 Ускорение и стиль iOS"
"$SCRIPTS/optimize.sh"

"$ADB" shell input keyevent KEYCODE_HOME
echo
printf "\033[1;32m✅ Готово! Beamox установлен.\033[0m\n"
echo "  • Нажмите «Домой» — откроется Beamox (если спросит — выберите Beamox и «Всегда»)."
echo "  • Скажите «Привет, Бимокс, открой офис» — сначала включите голос в Beamox → Настройки → «Привет, Бимокс»."
echo "  • Заблокируйте экран — там заставка Beamox."
echo "  • Вернуть всё как было:  bash $SCRIPTS/restore_full.sh"
echo "  • Обновление позже: Beamox → Настройки → «Проверить обновления» или снова bash install.sh"
