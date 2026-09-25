#!/usr/bin/env node
// Хук StopFailure для Claude Code на сервере: ход сорвался (кончились лимиты, сбой API) — зовём облачный резерв
// (repository_dispatch «zhorik_handoff» → cloud/workflow.yml):
//   - неотвеченные сообщения Telegram — облако ответит на них;
//   - проект, в папке которого работает сессия (origin на GitHub + текущая ветка), — облако продолжит его
//     с того, что Жорик уже запушил (по правилу из CLAUDE.md: коммит и push после каждого шага, план в NEXT.md).
//     Рабочее дерево, незакоммиченные правки и переписка отсюда никуда не уходят.
// Настройки: переменные окружения или файл ~/.claude/zhorik-cloud.env (строки KEY=VALUE):
//   ZHORIK_GH_REPO   — приватный репозиторий с резервом, например Reignzz/zhorik
//   ZHORIK_GH_TOKEN  — fine-grained токен только на этот репозиторий: Contents RW (+ Variables RW для пульса)
//   ZHORIK_CONTINUE  — off: проект не продолжать, только отвечать в Telegram
// Что уже передано, помним в ~/.claude/zhorik-handoff.json — одно сообщение уходит в облако один раз.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { buildHistory, githubRepoFromUrl, lastInboundChat, pendingInbound, readTranscript, scanTranscript } from "../lib.mjs";
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
    return { sent: Array.isArray(s.sent) ? s.sent : [], continued: String(s.continued || "") };
  } catch {
    return { sent: [], continued: "" };
  }
}

function saveState(state) {
  fs.mkdirSync(path.dirname(STATE), { recursive: true });
  fs.writeFileSync(
    STATE,
    JSON.stringify({ sent: state.sent.slice(-300), continued: state.continued, updatedAt: new Date().toISOString() }, null, 2),
  );
}

const git = (cwd, ...args) => execFileSync("git", ["-C", cwd, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();

// Проект, над которым работает сессия: только адрес на GitHub, ветка и последний коммит (без содержимого).
function currentProject(cwd, conf) {
  if (!cwd || String(conf.ZHORIK_CONTINUE || "on").toLowerCase() === "off") return null;
  try {
    const repo = conf.ZHORIK_PROJECT_REPO || githubRepoFromUrl(git(cwd, "remote", "get-url", "origin"));
    const branch = git(cwd, "rev-parse", "--abbrev-ref", "HEAD");
    const head = git(cwd, "rev-parse", "HEAD");
    return repo && branch && branch !== "HEAD" ? { repo, branch, head } : null;
  } catch {
    return null; // не git-репозиторий или нет origin
  }
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
  const project = currentProject(input.cwd, conf);
  // один и тот же запушенный коммит второй раз не передаём — облако его уже продолжает
  const continueKey = project ? `${project.repo}@${project.branch}@${project.head}` : "";
  const newProject = project && continueKey !== state.continued;
  if (!pending.length && !newProject) return log(`${input.hook_event_name || "hook"}: нового для облака нет`);

  const byChat = new Map();
  for (const m of pending) byChat.set(m.chat_id, [...(byChat.get(m.chat_id) || []), m]);
  const jobs = [...byChat].map(([chatId, messages]) => ({
    chat_id: chatId,
    messages: messages.map(({ message_id, user, ts, text }) => ({ message_id, user, ts, text: text.slice(0, 3000) })),
    history: buildHistory(scan, chatId, { beforePos: messages[0].pos, limit: 8, maxLen: 400 }),
  }));

  const res = await fetch(`${conf.ZHORIK_GH_API || "https://api.github.com"}/repos/${repo}/dispatches`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, accept: "application/vnd.github+json", "content-type": "application/json" },
    body: JSON.stringify({
      event_type: "zhorik_handoff",
      client_payload: {
        jobs,
        project: project ? { repo: project.repo, branch: project.branch } : null,
        report_chat_id: conf.ZHORIK_REPORT_CHAT || lastInboundChat(scan),
        reason: String(input.error_type || input.error || input.matcher || "stop_failure").slice(0, 60),
      },
    }),
    signal: AbortSignal.timeout(15_000),
  });
  if (res.status !== 204) return log(`GitHub ответил ${res.status}: ${(await res.text()).slice(0, 200)}`);
  state.sent.push(...pending.map((m) => m.key));
  if (project) state.continued = continueKey;
  saveState(state);
  log(`передано в облако: сообщений ${pending.length}${project ? `, проект ${project.repo} (${project.branch})` : ""}`);
}

// Хук не должен мешать Claude Code: любые ошибки — только в журнал.
main().catch((err) => log("ошибка:", err.message));
