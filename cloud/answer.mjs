#!/usr/bin/env node
// Облачный резерв Жорика: отвечает в Telegram от имени @бота, когда «мозг» на компьютере молчит
// (кончились лимиты Claude Code или компьютер выключен). Запускается из GitHub Actions (cloud/workflow.yml):
//   node cloud/answer.mjs plan — решить, есть ли на что отвечать (пишет задания в $ZHORIK_JOBS);
//   node cloud/answer.mjs run  — ответить через Claude Code (claude -p, ключ API) и отправить в Telegram.
// В журнал Actions не пишем ни текстов сообщений, ни токенов — только счётчики.
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { buildPrompt, groupUpdates, heartbeatState, parseAllowList, splitMessage } from "./lib.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const env = process.env;
const log = (...a) => console.log("zhorik-cloud:", ...a);
// адреса API переопределяются только в тестах
const TG_API = env.ZHORIK_TG_API || "https://api.telegram.org";
const GH_API = env.ZHORIK_GH_API || "https://api.github.com";

const cfg = {
  token: env.TELEGRAM_BOT_TOKEN || "",
  allow: parseAllowList(env.ZHORIK_ALLOWED_CHATS),
  model: env.ZHORIK_MODEL || "claude-opus-5",
  botDir: path.resolve(ROOT, env.ZHORIK_BOT_DIR || "bot"),
  jobsFile: env.ZHORIK_JOBS || path.join(env.RUNNER_TEMP || "/tmp", "zhorik-jobs.json"),
  switch: (env.ZHORIK_CLOUD || "on").toLowerCase(),
  heartbeat: env.ZHORIK_HEARTBEAT || "",
  staleAfterSec: (Number(env.ZHORIK_TAKEOVER_AFTER_MIN) || 20) * 60,
  fallbackLine: env.ZHORIK_FALLBACK_LINE || "Я на хвилинку відійшов — відповім трохи згодом, шоб я так жил! 🙏",
};

async function tg(method, body) {
  const res = await fetch(`${TG_API}/bot${cfg.token}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body || {}),
    signal: AbortSignal.timeout(30_000),
  });
  const data = await res.json().catch(() => ({}));
  if (!data.ok) {
    const err = new Error(`telegram ${method} → ${res.status} ${data.description || ""}`.trim());
    err.status = res.status;
    throw err;
  }
  return data.result;
}

// Логи Actions в публичном репозитории видны всем — там резерв не работает никогда.
async function ensurePrivateRepo() {
  const repo = env.GITHUB_REPOSITORY;
  if (!repo) return; // запуск вручную на своём компьютере
  const res = await fetch(`${GH_API}/repos/${repo}`, {
    headers: { authorization: `Bearer ${env.GITHUB_TOKEN || ""}`, accept: "application/vnd.github+json" },
    signal: AbortSignal.timeout(15_000),
  });
  const info = await res.json().catch(() => ({}));
  if (info.private !== true) {
    throw new Error(`репозиторий ${repo} не приватный (или его не видно) — резерв не запускаю, чтобы переписка не попала в публичные логи`);
  }
}

function readEvent() {
  try {
    return JSON.parse(fs.readFileSync(env.GITHUB_EVENT_PATH, "utf8"));
  } catch {
    return {};
  }
}

async function plan() {
  const out = { jobs: [], confirmOffset: 0 };
  const save = () => {
    fs.writeFileSync(cfg.jobsFile, JSON.stringify(out));
    if (env.GITHUB_OUTPUT) fs.appendFileSync(env.GITHUB_OUTPUT, `has_jobs=${out.jobs.length ? "true" : "false"}\n`);
    log(`заданий: ${out.jobs.length}`);
  };
  if (cfg.switch === "off") {
    log("резерв выключен (ZHORIK_CLOUD=off)");
    return save();
  }
  if (!cfg.token) throw new Error("нет секрета TELEGRAM_BOT_TOKEN");
  if (!cfg.allow.size) throw new Error("нет секрета ZHORIK_ALLOWED_CHATS — кому отвечать, неизвестно");
  await ensurePrivateRepo();

  const event = env.GITHUB_EVENT_NAME || "";
  const payload = readEvent();
  if (event === "repository_dispatch") {
    // компьютер на связи, но Claude Code не смог ответить (лимит) — хук handoff.mjs прислал сообщения
    const p = payload.client_payload || {};
    const chat = String(p.chat_id || "");
    if (chat && cfg.allow.has(chat) && Array.isArray(p.messages) && p.messages.length) {
      out.jobs.push({ chat_id: chat, messages: p.messages, history: Array.isArray(p.history) ? p.history : [] });
    } else {
      log("событие без сообщений или чат не в списке ZHORIK_ALLOWED_CHATS — пропускаю");
    }
  } else if (event === "workflow_dispatch") {
    const i = payload.inputs || {};
    const chat = String(i.chat_id || "");
    if (chat && cfg.allow.has(chat) && i.text) {
      out.jobs.push({ chat_id: chat, messages: [{ message_id: "", user: "", ts: "", text: String(i.text) }], history: [] });
      out.dryRun = String(i.dry_run) === "true";
    } else {
      log("ручной запуск: нужен chat_id из ZHORIK_ALLOWED_CHATS и текст");
    }
  } else {
    // по расписанию: забираем Telegram себе, только если пульс компьютера давно не приходил
    const state = heartbeatState(cfg.heartbeat, Math.floor(Date.now() / 1000), cfg.staleAfterSec);
    log(`пульс компьютера: ${state}`);
    if (state === "stale") {
      const updates = await tg("getUpdates", { timeout: 0, allowed_updates: ["message"] });
      Object.assign(out, groupUpdates(updates, cfg.allow));
      if (!out.jobs.length && out.confirmOffset) await confirm(out.confirmOffset); // чужие/служебные обновления — просто подтверждаем
    }
  }
  save();
}

async function confirm(offset) {
  if (offset) await tg("getUpdates", { offset, timeout: 0, limit: 1 });
}

function runClaude(prompt) {
  const extra = [path.join(HERE, "reserve-prompt.md")];
  // пока настоящий CLAUDE.md бота не перенесён в репозиторий — черновик характера из cloud/persona.md
  if (!fs.existsSync(path.join(cfg.botDir, "CLAUDE.md"))) extra.push(path.join(HERE, "persona.md"));
  const system = extra.map((f) => fs.readFileSync(f, "utf8")).join("\n\n");
  const cwd = fs.existsSync(cfg.botDir) ? cfg.botDir : ROOT;
  // токены Telegram и GitHub модели не нужны — убираем из окружения claude
  const { TELEGRAM_BOT_TOKEN, GITHUB_TOKEN, ZHORIK_ALLOWED_CHATS, ...childEnv } = env;
  const r = spawnSync(
    "claude",
    [
      "-p", prompt,
      "--output-format", "json",
      "--model", cfg.model,
      "--max-turns", "12",
      "--allowedTools", "Read,Glob,Grep,WebSearch,WebFetch",
      "--append-system-prompt", system,
    ],
    { cwd, env: childEnv, encoding: "utf8", timeout: 6 * 60_000, maxBuffer: 32 * 1024 * 1024 },
  );
  if (r.error || r.status !== 0) {
    log(`claude завершился с ошибкой (код ${r.status ?? "—"}): ${(r.error?.message || r.stderr || "").slice(0, 300)}`);
    return null;
  }
  try {
    const res = JSON.parse(r.stdout.trim().split("\n").pop());
    log(`claude: ${res.subtype || "?"}, ходов ${res.num_turns ?? "?"}, $${Number(res.total_cost_usd || 0).toFixed(4)}`);
    return !res.is_error && typeof res.result === "string" && res.result.trim() ? res.result.trim() : null;
  } catch {
    log("claude вернул не JSON");
    return null;
  }
}

async function run() {
  const { jobs = [], confirmOffset = 0, dryRun = false } = JSON.parse(fs.readFileSync(cfg.jobsFile, "utf8"));
  let failed = 0;
  for (const job of jobs) {
    try {
      if (!dryRun) await tg("sendChatAction", { chat_id: job.chat_id, action: "typing" }).catch(() => {});
      const answer = runClaude(buildPrompt(job)) || cfg.fallbackLine;
      if (dryRun) {
        // только ручная проверка в приватном репозитории: показываем ответ в журнале, в Telegram не шлём
        console.log(`--- ответ (dry run) ---\n${answer}\n---`);
        continue;
      }
      const replyTo = Number(job.messages.at(-1)?.message_id) || undefined;
      const parts = splitMessage(answer);
      for (const [i, text] of parts.entries()) {
        await tg("sendMessage", {
          chat_id: job.chat_id,
          text,
          ...(i === 0 && replyTo ? { reply_parameters: { message_id: replyTo, allow_sending_without_reply: true } } : {}),
        });
      }
      log(`ответ отправлен: частей ${parts.length}`);
    } catch (err) {
      failed++;
      log("не удалось ответить:", err.message);
    }
  }
  // подтверждаем обновления, только когда ответили на всё: иначе следующий запуск попробует снова
  if (confirmOffset && !failed) await confirm(confirmOffset);
  if (failed) process.exitCode = 1;
}

const cmd = process.argv[2];
const main = cmd === "plan" ? plan : cmd === "run" ? run : null;
if (!main) {
  console.error("использование: node cloud/answer.mjs plan|run");
  process.exit(2);
}
main().catch((err) => {
  log("ошибка:", err.message);
  process.exit(1);
});
