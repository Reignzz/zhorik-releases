# Облачный резерв Жорика

Когда «мозг» бота @odesbud_bot на компьютере молчит, отвечает облако: GitHub Actions запускает Claude Code
(`claude -p`) с тем же CLAUDE.md бота и шлёт ответ в Telegram от имени того же бота.

| Что случилось | Кто замечает | Что делает облако |
|---|---|---|
| У Claude Code на компьютере кончились лимиты (или сбой API, слетел вход) | хук `StopFailure` → `hooks/handoff.mjs` | получает `repository_dispatch: zhorik_handoff` с неотвеченными сообщениями и проектом; отвечает и продолжает работу в ветке `zhorik/cloud` |
| Компьютер выключен или бот не запущен | пропал пульс `ZHORIK_HEARTBEAT` (`hooks/heartbeat.mjs`) | раз в 15 минут в рабочее время сам забирает сообщения (`getUpdates`) и отвечает |
| Компьютер снова на связи | пульс свежий | Telegram не трогает |

Резерв расходует **ключ API Anthropic** (оплата по факту), а не подписку: облачные сессии Claude Code делят лимиты
с компьютером, поэтому, когда лимит кончился там, он кончился и в облаке.

## Файлы

- `answer.mjs` — запуск в Actions: `plan` (есть ли на что отвечать), `run` (ответить).
- `lib.mjs` — разбор транскрипта Claude Code и обновлений Telegram, без сети (покрыто тестами).
- `reserve-prompt.md` — дополнение к системному промпту в резервном режиме.
- `persona.md` — временный черновик характера, пока CLAUDE.md бота не перенесён в репозиторий.
- `workflow.yml` — шаблон `.github/workflows/zhorik-cloud.yml`.
- `hooks/handoff.mjs` — хук `StopFailure` на компьютере; `hooks/heartbeat.mjs` — пульс; `hooks/settings.example.json` — как подключить хук.
- `test/` — `node --test cloud/test/*.test.mjs` (юнит-тесты и сквозные на заглушках, без сети и без трат).

## Установка

Резерв работает **только в приватном репозитории** (`answer.mjs` проверяет это сам): в публичном журналы Actions видны всем.

1. Приватный репозиторий с исходниками бота (например, `Reignzz/zhorik`): папка `cloud/` целиком, `cloud/workflow.yml` →
   `.github/workflows/zhorik-cloud.yml`, папка бота с его `CLAUDE.md` (по умолчанию `bot/`, иначе переменная `ZHORIK_BOT_DIR`).
   `repository_dispatch` и расписание запускают workflow только из ветки по умолчанию — туда он и должен попасть.
2. Settings → Secrets and variables → Actions → **Secrets**:
   - `TELEGRAM_BOT_TOKEN` — тот же токен, что у плагина (`~/.claude/channels/telegram/.env`);
   - `ANTHROPIC_API_KEY` — ключ из console.anthropic.com, лучше отдельный, с лимитом расходов;
   - `ZHORIK_ALLOWED_CHATS` — id чатов через запятую (те же, что в allowlist плагина `~/.claude/channels/telegram/access.json`).
3. Там же → **Variables** (необязательно): `ZHORIK_MODEL` (по умолчанию `claude-opus-5-5`), `ZHORIK_BOT_DIR`,
   `ZHORIK_TAKEOVER_AFTER_MIN` (по умолчанию 20), `ZHORIK_CLOUD=off` — выключить резерв.
4. Fine-grained токен GitHub **только на этот репозиторий**: Contents — Read and write (для `repository_dispatch`),
   Variables — Read and write (для пульса). На компьютере — файл `~/.claude/zhorik-cloud.env`, права 600:
   ```
   ZHORIK_GH_REPO=Reignzz/zhorik
   ZHORIK_GH_TOKEN=github_pat_…
   ```
5. На компьютере — хук: блок из `hooks/settings.example.json` в `~/.claude/settings.json`
   (или в `.claude/settings.json` папки бота), путь к `handoff.mjs` поправить.
6. На компьютере — пульс раз в 5 минут: `node <путь>/cloud/hooks/heartbeat.mjs` (launchd на Mac, cron на Linux,
   Планировщик заданий на Windows). Пока пульс не настроен, облако само Telegram не опрашивает.
7. В CLAUDE.md бота — правило про двойные ответы (см. `docs/CLOUD_PLAN.md`, этап 4).

## Проверка

- Actions → zhorik-cloud → Run workflow: `chat_id` из списка, текст, `dry_run` включён — ответ появится в журнале, в Telegram не уйдёт.
- С `dry_run` выключенным — ответ придёт в Telegram.
- Хук: временно поставить в matcher `unknown|rate_limit`, … — или дождаться лимита; журнал хука — `~/.claude/zhorik-handoff.log`.

## Деньги

- Один ответ ≈ $0.1–0.35 на `claude-opus-5-5` (в основном системный промпт Claude Code и CLAUDE.md; повторные ответы в течение часа дешевле за счёт кеша).
  Дешевле — `ZHORIK_MODEL=claude-sonnet-5`; решает владелец.
- GitHub Actions в приватном репозитории: бесплатно 2000 минут в месяц; расписание (пн–пт, 12 часов, раз в 15 минут) ≈ 1000 минут, ответы — по 1–2 минуты.
