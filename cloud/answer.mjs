#!/usr/bin/env node
// Облачный резерв Жорика: когда «мозг» на сервере молчит (кончились лимиты Claude Code или сервер выключен),
// отвечает в Telegram от имени того же бота и продолжает его текущий проект. Запускается из GitHub Actions
// (cloud/workflow.yml):
//   node cloud/answer.mjs plan — решить, есть ли что делать (пишет задания в $ZHORIK_JOBS);
//   node cloud/answer.mjs run  — ответить/продолжить через Claude Code (claude -p, ключ API), отчитаться в Telegram.
// Проект облако берёт с GitHub (ветка, которую Жорик запушил), работает в ветке zhorik/cloud и пушит её же.
// В журнал Actions не пишем ни текстов сообщений, ни токенов — только счётчики.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  buildContinuePrompt,
  buildPrompt,
  commitTitle,
  groupUpdates,
  heartbeatState,
  isSecretPath,
  parseAllowList,
  splitMessage,
} from "./lib.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const env = process.env;
const log = (...a) => console.log("zhorik-cloud:", ...a);
// адреса API и git переопределяются только в тестах
const TG_API = env.ZHORIK_TG_API || "https://api.telegram.org";
const GH_API = env.ZHORIK_GH_API || "https://api.github.com";
const CLOUD_BRANCH = "zhorik/cloud";
const SILENT = "(тиша)";

const cfg = {
  token: env.TELEGRAM_BOT_TOKEN || "",
  allow: parseAllowList(env.ZHORIK_ALLOWED_CHATS),
  model: env.ZHORIK_MODEL || "claude-fable-5-1", // та же модель, что у Жорика на сервере (zhorik-run.sh)
  botDir: path.resolve(ROOT, env.ZHORIK_BOT_DIR || "bot"),
  personaFile: env.ZHORIK_PERSONA_FILE ? path.resolve(ROOT, env.ZHORIK_PERSONA_FILE) : "",
  jobsFile: env.ZHORIK_JOBS || path.join(env.RUNNER_TEMP || os.tmpdir(), "zhorik-jobs.json"),
  workDir: env.ZHORIK_WORK_DIR || path.join(env.RUNNER_TEMP || os.tmpdir(), "zhorik-project"),
  gitBase: (env.ZHORIK_GIT_BASE || "https://github.com").replace(/\/+$/, ""),
  switch: (env.ZHORIK_CLOUD || "on").toLowerCase(),
  continueSwitch: (env.ZHORIK_CONTINUE || "on").toLowerCase(),
  continueTurns: Number(env.ZHORIK_CONTINUE_MAX_TURNS) || 80,
  continueMin: Number(env.ZHORIK_CONTINUE_TIMEOUT_MIN) || 40,
  heartbeat: env.ZHORIK_HEARTBEAT || "",
  staleAfterSec: (Number(env.ZHORIK_TAKEOVER_AFTER_MIN) || 20) * 60,
  fallbackLine: env.ZHORIK_FALLBACK_LINE || "Я на хвилинку відійшов — відповім трохи згодом, шоб я так жил! 🙏",
  continueFailLine: "Спробував продовжити роботу над проєктом у хмарі, але не вийшло — підхоплю, щойно повернуся 🙏",
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

const validRepo = (s) => /^[\w.-]+\/[\w.-]+$/.test(s);
const validBranch = (s) => /^(?!-)(?!.*\.\.)[\w./-]+$/.test(s);

// Сообщение от хука handoff.mjs: неотвеченные сообщения по чатам + проект, который надо продолжить.
function dispatchJobs(p) {
  const jobs = (Array.isArray(p.jobs) ? p.jobs : [])
    .filter((j) => cfg.allow.has(String(j.chat_id)) && Array.isArray(j.messages) && j.messages.length)
    .map((j) => ({
      chat_id: String(j.chat_id),
      messages: j.messages,
      history: Array.isArray(j.history) ? j.history : [],
      // задачи из очереди бота (zhorik-cloud.mjs на сервере) — как их видит Жорик: id, from, text, context…
      ...(Array.isArray(j.tasks) ? { tasks: j.tasks.filter((t) => t && typeof t === "object").slice(0, 20) } : {}),
    }));
  const proj = p.project;
  if (proj && cfg.continueSwitch !== "off") {
    const report = String(p.report_chat_id || "");
    if (!validRepo(String(proj.repo)) || !validBranch(String(proj.branch))) {
      log("проект: неверное имя репозитория или ветки — не продолжаю");
    } else if (!cfg.allow.has(report)) {
      log("проект: чат для отчёта не в ZHORIK_ALLOWED_CHATS — не продолжаю");
    } else {
      let job = jobs.find((j) => j.chat_id === report);
      if (!job) jobs.push((job = { chat_id: report, messages: [], history: [] }));
      job.project = { repo: String(proj.repo), branch: String(proj.branch) };
    }
  }
  return jobs;
}

async function plan() {
  const out = { jobs: [], confirmOffset: 0 };
  const save = () => {
    fs.writeFileSync(cfg.jobsFile, JSON.stringify(out));
    if (env.GITHUB_OUTPUT) fs.appendFileSync(env.GITHUB_OUTPUT, `has_jobs=${out.jobs.length ? "true" : "false"}\n`);
    log(`заданий: ${out.jobs.length}${out.jobs.some((j) => j.project) ? " (с продолжением проекта)" : ""}`);
  };
  if (cfg.switch === "off") {
    log("резерв выключен (ZHORIK_CLOUD=off)");
    return save();
  }
  // резерв ещё не настроен (нет секретов) — спокойно выходим, а не падаем каждые 15 минут
  if (!cfg.token || !cfg.allow.size) {
    log("резерв не настроен: нужны секреты TELEGRAM_BOT_TOKEN и ZHORIK_ALLOWED_CHATS");
    return save();
  }
  await ensurePrivateRepo();

  const event = env.GITHUB_EVENT_NAME || "";
  const payload = readEvent();
  if (event === "repository_dispatch") {
    // сервер на связи, но Claude Code не смог продолжить (лимит) — хук handoff.mjs прислал, что делать
    out.jobs = dispatchJobs(payload.client_payload || {});
    // текущие правила бота с сервера (zhorik-cloud.mjs) — главнее копии в репозитории, она может отставать
    const rules = payload.client_payload?.rules;
    if (out.jobs.length && typeof rules === "string" && rules.trim()) out.rules = rules.slice(0, 40_000);
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
    // по расписанию: забираем Telegram себе, только если пульс сервера давно не приходил
    const state = heartbeatState(cfg.heartbeat, Math.floor(Date.now() / 1000), cfg.staleAfterSec);
    log(`пульс сервера: ${state}`);
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

// ---------- git для продолжения проекта: токен только в окружении git, не в аргументах и не у claude ----------

function gitEnv() {
  const token = env.ZHORIK_GH_PAT || env.GITHUB_TOKEN || "";
  const e = { ...env, GIT_TERMINAL_PROMPT: "0" };
  if (token && cfg.gitBase.startsWith("https://")) {
    e.GIT_CONFIG_COUNT = "1";
    e.GIT_CONFIG_KEY_0 = `http.${cfg.gitBase}/.extraheader`;
    e.GIT_CONFIG_VALUE_0 = `AUTHORIZATION: basic ${Buffer.from(`x-access-token:${token}`).toString("base64")}`;
  }
  return e;
}

const git = (cwd, ...args) =>
  execFileSync("git", args, { cwd, env: gitEnv(), encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();

function hasRef(dir, ref) {
  try {
    git(dir, "rev-parse", "--verify", "--quiet", ref);
    return true;
  } catch {
    return false;
  }
}

// Клонируем проект: продолжаем zhorik/cloud (если облако уже работало) с подтянутой веткой Жорика, иначе — от его ветки.
function prepareProject({ repo, branch }) {
  const dir = cfg.workDir;
  fs.rmSync(dir, { recursive: true, force: true });
  git(path.dirname(dir), "clone", "--quiet", "--no-checkout", `${cfg.gitBase}/${repo}.git`, dir);
  git(dir, "config", "user.name", "Жорик (облако)");
  git(dir, "config", "user.email", "zhorik-cloud@users.noreply.github.com");
  const serverRef = `refs/remotes/origin/${branch}`;
  let note = "";
  if (hasRef(dir, `refs/remotes/origin/${CLOUD_BRANCH}`)) {
    git(dir, "checkout", "--quiet", "-B", CLOUD_BRANCH, `origin/${CLOUD_BRANCH}`);
    if (hasRef(dir, serverRef)) {
      try {
        git(dir, "merge", "--quiet", "--no-edit", `origin/${branch}`);
      } catch {
        try {
          git(dir, "merge", "--abort");
        } catch {
          // слияние не началось
        }
        note = `гілка ${branch} на сервері розійшлася з ${CLOUD_BRANCH} (конфлікт злиття) — працюю далі в ${CLOUD_BRANCH}, серверні зміни не підтягнуто`;
      }
    }
  } else if (hasRef(dir, serverRef)) {
    git(dir, "checkout", "--quiet", "-B", CLOUD_BRANCH, `origin/${branch}`);
  } else {
    throw new Error(`в ${repo} нет ветки ${branch} — Жорик её не запушил`);
  }
  return { dir, note };
}

// Коммит и push результата облака в zhorik/cloud (файлы с секретами — никогда).
function publishProject(dir, report, tasks = []) {
  const logFile = path.join(dir, ".zhorik", "CLOUD_LOG.md");
  fs.mkdirSync(path.dirname(logFile), { recursive: true });
  if (!fs.existsSync(logFile)) fs.writeFileSync(logFile, "# Журнал облачного резерва\n\nЧто Жорик сделал в облаке, пока на сервере кончились лимиты.\n");
  const taskList = tasks.map((t) => `- #${t.id} «${String(t.text ?? "").replace(/\s+/g, " ").slice(0, 150)}»`).join("\n");
  fs.appendFileSync(logFile, `\n## ${new Date().toISOString()}\n\n${taskList ? `Задачі:\n${taskList}\n\nЗвіт у чат:\n\n` : ""}${report}\n`);
  git(dir, "add", "-A");
  const staged = git(dir, "diff", "--cached", "--name-only", "-z").split("\0").filter(Boolean);
  const secret = staged.filter(isSecretPath);
  if (secret.length) {
    git(dir, "reset", "--quiet", "--", ...secret);
    log(`файлы с секретами не коммичу: ${secret.length}`);
  }
  if (staged.length === secret.length) return false;
  git(dir, "commit", "--quiet", "-m", commitTitle(report));
  git(dir, "push", "--quiet", "origin", `HEAD:refs/heads/${CLOUD_BRANCH}`);
  return true;
}

function runClaude(prompt, { cwd = null, dev = false } = {}) {
  const parts = [];
  const workDir = cwd || (fs.existsSync(cfg.botDir) ? cfg.botDir : ROOT);
  // Правила й характер бота: ZHORIK_PERSONA_FILE (наприклад ZHORIK.md бота) → CLAUDE.md у ZHORIK_BOT_DIR →
  // чернетка cloud/persona.md. CLAUDE.md бота в чужій теці (проєкт) сам не підхопиться — додаємо текст явно.
  // Правила резерву — після них: де розходяться (виклад, git, скрипти сервера), головніші вони.
  const personaFile = cfg.personaFile && fs.existsSync(cfg.personaFile) ? cfg.personaFile : null;
  const botClaude = path.join(cfg.botDir, "CLAUDE.md");
  if (personaFile) parts.push(`# Правила й характер бота\n\n${fs.readFileSync(personaFile, "utf8")}`);
  else if (!fs.existsSync(botClaude)) parts.push(fs.readFileSync(path.join(HERE, "persona.md"), "utf8"));
  else if (path.resolve(workDir) !== cfg.botDir) parts.push(`# CLAUDE.md бота (характер і правила)\n\n${fs.readFileSync(botClaude, "utf8")}`);
  parts.push(fs.readFileSync(path.join(HERE, "reserve-prompt.md"), "utf8"));
  if (dev) parts.push(fs.readFileSync(path.join(HERE, "continue-prompt.md"), "utf8"));
  // токены Telegram и GitHub модели не нужны — убираем из окружения claude
  const childEnv = { ...env };
  for (const k of Object.keys(childEnv)) {
    if (["TELEGRAM_BOT_TOKEN", "GITHUB_TOKEN", "ZHORIK_GH_PAT", "ZHORIK_ALLOWED_CHATS"].includes(k) || k.startsWith("GIT_CONFIG_")) delete childEnv[k];
  }
  const r = spawnSync(
    "claude",
    [
      "-p", prompt,
      "--output-format", "json",
      "--model", cfg.model,
      "--max-turns", String(dev ? cfg.continueTurns : 12),
      "--allowedTools", dev ? "Read,Edit,Write,Glob,Grep,Bash,WebSearch,WebFetch" : "Read,Glob,Grep,WebSearch,WebFetch",
      "--append-system-prompt", parts.join("\n\n"),
    ],
    { cwd: workDir, env: childEnv, encoding: "utf8", timeout: (dev ? cfg.continueMin : 6) * 60_000, maxBuffer: 64 * 1024 * 1024 },
  );
  if (r.error || r.status !== 0) {
    // причина (ключ, баланс, доступ к модели) — в JSON-ответе claude на stdout, а не в stderr
    let detail = r.error?.message || "";
    try {
      const res = JSON.parse((r.stdout || "").trim().split("\n").pop());
      detail ||= `${res.subtype || ""} ${res.result || ""}`;
    } catch {
      // не JSON
    }
    detail ||= r.stderr || r.stdout || "";
    log(`claude завершился с ошибкой (код ${r.status ?? "—"}): ${detail.replace(/\s+/g, " ").trim().slice(0, 300)}`);
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

// Продолжить проект; не вышло — хотя бы ответить на сообщения обычным образом.
function continueProject(job) {
  try {
    const { dir, note } = prepareProject(job.project);
    const report = runClaude(buildContinuePrompt(job, note), { cwd: dir, dev: true });
    if (!report) return { text: cfg.continueFailLine, failed: true };
    const pushed = publishProject(dir, report, job.tasks);
    log(pushed ? `проект: изменения запушены в ${CLOUD_BRANCH}` : "проект: изменений нет");
    return { text: report, failed: false };
  } catch (err) {
    log("проект: не удалось продолжить —", err.message.split("\n")[0].slice(0, 200));
    if (job.messages.length) return { text: runClaude(buildPrompt(job)) || cfg.fallbackLine, failed: true };
    return { text: cfg.continueFailLine, failed: true };
  }
}

async function run() {
  const { jobs = [], confirmOffset = 0, dryRun = false, rules = "" } = JSON.parse(fs.readFileSync(cfg.jobsFile, "utf8"));
  if (rules) {
    cfg.personaFile = path.join(path.dirname(cfg.jobsFile), "zhorik-rules.md");
    fs.writeFileSync(cfg.personaFile, rules);
  }
  let failed = 0;
  for (const job of jobs) {
    try {
      if (!dryRun) await tg("sendChatAction", { chat_id: job.chat_id, action: "typing" }).catch(() => {});
      let answer;
      if (job.project) {
        const res = continueProject(job);
        answer = res.text;
        if (res.failed) failed++;
      } else {
        answer = runClaude(buildPrompt(job)) || cfg.fallbackLine;
      }
      // «(тиша)» — по правилам бота в чат писати нічого (самі балачки, а не задачі)
      if (answer.trim() === SILENT) {
        log("відповідь: (тиша) — у чат нічого не надсилаю");
        continue;
      }
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
