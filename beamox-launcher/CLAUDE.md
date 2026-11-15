# Beamox Launcher — памятка для Claude

Лаунчер для рабочего телефона Moto G57 в стиле iOS 26 для компании **Beamox** (Kotlin + Jetpack Compose).
Общение с владельцем — по-русски.

## Что уже сделано
- Главный экран один в один с iPhone владельца (размеры сняты со скриншота: виджеты 177 pt, иконки 69 pt,
  шаг 99×108 pt, док 116 pt; масштаб `k = ширина/430`) — `ui/Home.kt`.
- Liquid Glass (размытые обои под доком/поиском) — `ui/Glass.kt`; форма иконок — суперэллипс (`SuperellipseShape`).
- Своя строка состояния как у iPhone — `ui/StatusBar.kt` (системная скрыта в `MainActivity`).
- Настройки как на iPhone (переключатели iOS 26, шевроны, плавающий поиск) — `ui/SettingsScreen.kt`, `ui/Ios.kt`.
- Чат Claude в стиле iMessage — `ui/ClaudeScreen.kt` (Anthropic Java SDK, модель по умолчанию `claude-opus-5-5`).
- Живая панель Beamox («Генератор охватов», `…/c/<id>/data.json`): студия, площадки, сессии Claude, лимиты, сервер — `core/Dashboard.kt`.
  Ссылка вводится на телефоне (в репозиторий НЕ коммитить — репозиторий публичный).
- Голос «Привет, Бимокс» — `voice/`. Очистка рекламы — `core/Bloat.kt`. Наборы иконок из Google Play — `core/IconPack.kt`.
- Скрипты Mac: `scripts/mac/install.sh` (бэкап → установка → разрешения → очистка), `scripts/backup.sh`, `scripts/restore_full.sh`.

## Сборка и проверка
```bash
./gradlew assembleRelease                 # APK: app/build/outputs/apk/release/app-release.apk
./gradlew :app:recordPaparazziDebug       # скриншоты экранов без телефона: app/src/test/snapshots/images/
adb install -r app/build/outputs/apk/release/app-release.apk
```
Нужен Android SDK (Android Studio). Для скриншотов с живыми данными положите копию data.json в
`app/src/test/resources/sample_dashboard.json` (в .gitignore).

## Выпуск
GitHub Actions (`.github/workflows/beamox-launcher.yml`) собирает APK на каждый push, а выкладывает в релиз
`beamox-launcher` только вручную (Actions → Run workflow) или коммитом с `[release]`. На телефоне: Настройки → «Обновление ПО».

## Открытые вопросы
- Владелец ещё не одобрил последний вид — выпускать только после его «выпускай».
- Брендбук Beamox (логотип, шрифты) лежит на Mac владельца — заменить временный знак-лучи и шрифт Inter на фирменные.
- Реальные ссылки на соцсети, почты, телефоны команды — в `app/src/main/assets/beamox_config.json` сейчас шаблоны.
- Студия роликов: веб-адрес студии (если есть) — вставить в Настройках.
