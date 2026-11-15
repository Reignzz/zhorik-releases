#!/usr/bin/env bash
# Ускорение и «iOS-ощущение» телефона без root.
set -uo pipefail
ADB="${ADB:-adb}"
PKG=com.beamox.launcher
# Быстрые анимации (как на iPhone — плавно, но быстро)
$ADB shell settings put global window_animation_scale 0.7
$ADB shell settings put global transition_animation_scale 0.7
$ADB shell settings put global animator_duration_scale 0.7
# Навигация жестами (как на iPhone)
$ADB shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1
# Звук нажатий выключаем — у Beamox своя лёгкая вибрация
$ADB shell settings put system sound_effects_enabled 0
# Компиляция приложений для скорости (может занять пару минут)
$ADB shell cmd package compile -m speed-profile -a >/dev/null 2>&1 &
# Не «усыплять» лаунчер и голосового ассистента
$ADB shell dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
$ADB shell cmd appops set $PKG RUN_IN_BACKGROUND allow >/dev/null 2>&1
echo "  ✓ анимации, жесты, оптимизация"
