#!/usr/bin/env bash
# Возвращает удалённые debloat.sh приложения.
# ./restore.sh                       — вернуть всё из всех removed_*.txt
# ./restore.sh com.spotify.music     — вернуть одно приложение
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-adb}"
if [[ $# -gt 0 ]]; then pkgs="$*"; else pkgs="$(cat "$DIR"/removed_*.txt 2>/dev/null | sort -u)"; fi
[[ -z "$pkgs" ]] && { echo "Нечего восстанавливать"; exit 0; }
for p in $pkgs; do
  $ADB shell cmd package install-existing --user 0 "$p" >/dev/null 2>&1
  $ADB shell pm enable --user 0 "$p" >/dev/null 2>&1
  echo "  ↺ $p"
done
echo "Готово."
