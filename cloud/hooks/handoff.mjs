#!/usr/bin/env node
// Хук StopFailure для Claude Code на компьютере: ход сорвался (кончились лимиты, сбой API) —
// неотвеченные сообщения Telegram уходят в облачный резерв (repository_dispatch → cloud/workflow.yml).
// Настройки: переменные окружения или файл ~/.claude/zhorik-cloud.env (строки KEY=VALUE):
//   ZHORIK_GH_REPO   — приватный репозиторий с резервом, например Reignzz/zhorik
//   ZHORIK_GH_TOKEN  — fine-grained токен только на этот репозиторий: Contents RW (+ Variables RW для пульса)
// Что уже передано, помним в ~/.claude/zhorik-handoff.json — одно сообщение уходит в облако один раз.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { buildHistory, pendingInbound, readTranscript, scanTranscript } from "../lib.mjs";
import { loadConfig } from "./env.mjs";

const HOME = os.homedir();
const STATE = path.join(HOME, ".claude", "zhorik-handoff.json");
const LOG = path.join(HOME, ".claude", "zhorik-handoff.log");

function log(...a) {
  try {
    fs.appendFileSync(LOG, `${new Date().toISOString()} ${a.join(" ")}\n`);
  } catch {
    // журнал — не главное
  }
}

function loadState() {
  try {
    const s = JSON.parse(fs.readFileSync(STATE, "utf8"));
    return { sent: Array.isArray(s.sent) ? s.sent : [] };
  } catch {
    return { sent: [] };
  }
}

function saveState(state) {
  fs.mkdirSync(path.dirname(STATE), { recursive: true });
  fs.writeFileSync(STATE, JSON.stringify({ sent: state.sent.slice(-300), updatedAt: new Date().toISOString() }, null, 2));
}

async function main() {
  const input = JSON.parse(fs.readFileSync(0, "utf8") || "{}");
  const conf = loadConfig();
  const repo = conf.ZHORIK_GH_REPO;
  const token = conf.ZHORIK_GH_TOKEN;
  if (!repo || !token) return log("нет ZHORIK_GH_REPO/ZHORIK_GH_TOKEN — облако не позвать");
  if (!input.transcript_path) return log("в данных хука нет transcript_path");

  const scan = scanTranscript(readTranscript(fs.readFileSync(input.transcript_path, "utf8")));
  const state = loadState();
  const pending = pendingInbound(scan, new Set(state.sent));
  if (!pending.length) return log(`${input.hook_event_name || "hook"}: неотвеченных сообщений Telegram нет`);

  // по одному вызову облака на чат: все его новые сообщения вместе
  const byChat = new Map();
  for (const m of pending) byChat.set(m.chat_id, [...(byChat.get(m.chat_id) || []), m]);
  for (const [chatId, messages] of byChat) {
    const res = await fetch(`${conf.ZHORIK_GH_API || "https://api.github.com"}/repos/${repo}/dispatches`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        accept: "application/vnd.github+json",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        event_type: "tg_message",
        client_payload: {
          chat_id: chatId,
          messages: messages.map(({ message_id, user, ts, text }) => ({ message_id, user, ts, text: text.slice(0, 3000) })),
          history: buildHistory(scan, chatId, { beforePos: messages[0].pos }),
          reason: String(input.error_type || input.error || input.matcher || "stop_failure").slice(0, 60),
        },
      }),
      signal: AbortSignal.timeout(15_000),
    });
    if (res.status !== 204) {
      log(`GitHub ответил ${res.status} для чата ${chatId}: ${(await res.text()).slice(0, 200)}`);
      continue;
    }
    state.sent.push(...messages.map((m) => m.key));
    saveState(state);
    log(`передано в облако: чат ${chatId}, сообщений ${messages.length}`);
  }
}

// Хук не должен мешать Claude Code: любые ошибки — только в журнал.
main().catch((err) => log("ошибка:", err.message));
