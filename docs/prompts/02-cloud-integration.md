# Промпт 2 — перенос резерва в приватный репозиторий

Для облачной сессии Claude Code (claude.ai/code) с двумя репозиториями: приватный с исходниками бота
(после этапа 1, например `Reignzz/zhorik`) и `Reignzz/zhorik-releases` (ветка `claude/zen-clarke-ea8j8h`, папка `cloud/`).

---

Нужно включить облачный резерв бота Жорика (@odesbud_bot) в приватном репозитории с исходниками бота.
Контекст — `docs/CLOUD_PLAN.md` и `cloud/README.md` в Reignzz/zhorik-releases (ветка claude/zen-clarke-ea8j8h) и `docs/CLOUD_HANDOFF.md` в приватном репозитории.

1. Прочитай docs/CLOUD_HANDOFF.md. Сверь с тем, на что рассчитан резерв:
   - сообщения Telegram приходят в сессию Claude Code как `<channel source="…telegram…" chat_id="…" message_id="…" user="…" ts="…">текст</channel>`, ответ — инструмент с именем, оканчивающимся на `reply`, с полями `chat_id` и `text` (`cloud/lib.mjs`, `scanTranscript`);
   - плагин опрашивает Telegram через getUpdates (а не webhook);
   - где лежит CLAUDE.md бота и что в нём ссылается на локальные инструменты.
   Если что-то устроено иначе — поправь `cloud/lib.mjs` / `cloud/hooks/handoff.mjs` и тесты под реальность (реальные строки транскрипта бери из CLOUD_HANDOFF без личных данных).
2. Скопируй `cloud/` из zhorik-releases в приватный репозиторий, `cloud/workflow.yml` → `.github/workflows/zhorik-cloud.yml`. Если CLAUDE.md бота лежит не в `bot/`, пропиши путь в README как значение переменной ZHORIK_BOT_DIR.
3. Если у посредника есть API для событий от бота (`bot_reply` и т.п.) — добавь в `answer.mjs` отправку `bot_reply` после ответа, с отдельным секретом; если API нет — не выдумывай, опиши в README.
4. `node --test cloud/test/*.test.mjs` — всё зелёное. Секретов в коммитах нет (проверь поиском по шаблонам токенов).
5. Закоммить в отдельную ветку и запушь. Итог: что осталось сделать владельцу (секреты Actions, токен GitHub в ~/.claude/zhorik-cloud.env, хук, пульс, правило в CLAUDE.md) — списком по `docs/CLOUD_PLAN.md`, этап 4.

Не трогай работающего бота на компьютере и настройки Telegram (webhook не ставить: плагин работает через getUpdates).
