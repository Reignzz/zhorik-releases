#!/usr/bin/env bash
# Удаляет рекламный/лишний софт с подключённого телефона (без root).
# Использование: ./debloat.sh            — удалить по списку debloat_packages.txt
#                ./debloat.sh --dry-run  — только показать, что будет удалено
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-adb}"
DRY=0; [[ "${1:-}" == "--dry-run" ]] && DRY=1
LOG="$DIR/removed_$(date +%Y%m%d_%H%M%S).txt"

installed="$($ADB shell pm list packages --user 0 2>/dev/null | tr -d '\r' | sed 's/^package://')"
removed=0; skipped=0
while IFS= read -r line; do
  pkg="$(echo "$line" | sed 's/#.*//' | xargs)"
  [[ -z "$pkg" ]] && continue
  if ! grep -qx "$pkg" <<<"$installed"; then skipped=$((skipped+1)); continue; fi
  if [[ $DRY == 1 ]]; then echo "  будет удалено: $pkg"; continue; fi
  out="$($ADB shell pm uninstall -k --user 0 "$pkg" 2>&1 | tr -d '\r')"
  if [[ "$out" == *Success* ]]; then
    echo "  ✓ удалено: $pkg"; echo "$pkg" >> "$LOG"; removed=$((removed+1))
  else
    # некоторые системные пакеты нельзя удалить — отключаем
    $ADB shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 && { echo "  ○ отключено: $pkg"; echo "$pkg" >> "$LOG"; } \
      || echo "  ! не удалось: $pkg ($out)"
  fi
done < "$DIR/debloat_packages.txt"

if [[ $DRY == 0 ]]; then
  echo "Готово: удалено/отключено $removed, не найдено на телефоне $skipped."
  [[ -f "$LOG" ]] && echo "Список удалённого сохранён: $LOG (для restore.sh)"
fi
