// Сквозная проверка на заглушках: хук на компьютере → GitHub → answer.mjs plan/run → Telegram.
// Настоящие Telegram, GitHub и Claude не трогаем: локальный HTTP-сервер и фальшивый `claude` в PATH.
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import http from "node:http";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";

const run = promisify(execFile);
const CLOUD = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), "zhorik-e2e-"));
const TOKEN = "123:TEST";

const mock = { private: true, updates: [], calls: [] };
let server;
let base;

before(async () => {
  server = http.createServer((req, res) => {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const json = body ? JSON.parse(body) : {};
      mock.calls.push({ method: req.method, url: req.url, body: json, auth: req.headers.authorization });
      const send = (status, data) => {
        res.writeHead(status, { "content-type": "application/json" });
        res.end(data === undefined ? "" : JSON.stringify(data));
      };
      if (req.url === "/repos/o/r") return send(200, { private: mock.private });
      if (req.url === "/repos/o/r/dispatches") return send(204);
      const tg = req.url.match(/^\/bot([^/]+)\/(\w+)$/);
      if (tg) {
        if (tg[1] !== TOKEN) return send(401, { ok: false, description: "Unauthorized" });
        if (tg[2] === "getUpdates") return send(200, { ok: true, result: json.offset ? [] : mock.updates });
        return send(200, { ok: true, result: { message_id: 1 } });
      }
      send(404, { message: "Not Found" });
    });
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${server.address().port}`;

  // фальшивый claude: запоминает окружение и отвечает как `claude -p --output-format json`
  const bin = path.join(TMP, "bin");
  fs.mkdirSync(bin);
  fs.writeFileSync(
    path.join(bin, "claude"),
    `#!/usr/bin/env node
const fs = require("fs");
const argv = process.argv.slice(2);
const dev = (argv[argv.indexOf("--allowedTools") + 1] || "").includes("Edit");
fs.writeFileSync(${JSON.stringify(path.join(TMP, "claude-call.json"))}, JSON.stringify({ argv, cwd: process.cwd(), dev, hasTgToken: "TELEGRAM_BOT_TOKEN" in process.env, hasGitCfg: Object.keys(process.env).some((k) => k.startsWith("GIT_CONFIG_")), hasGhToken: "GITHUB_TOKEN" in process.env }));
if (dev) {
  fs.appendFileSync("feature.txt", "крок\\n");
  fs.writeFileSync(".env", "SECRET=1\\n");
}
const result = dev ? "Зробив крок: feature.txt. Далі — перевірка." : "Ну таки здрасьте! Відповідаю з резерву 😉";
console.log(JSON.stringify({ type: "result", subtype: "success", is_error: false, result, num_turns: 1, total_cost_usd: 0.01 }));
`,
    { mode: 0o755 },
  );
});

after(() => server.close());

function answerEnv(extra) {
  return {
    PATH: `${path.join(TMP, "bin")}:${process.env.PATH}`,
    HOME: TMP,
    TELEGRAM_BOT_TOKEN: TOKEN,
    ZHORIK_ALLOWED_CHATS: "42",
    ZHORIK_TG_API: base,
    ZHORIK_GH_API: base,
    ZHORIK_JOBS: path.join(TMP, "jobs.json"),
    ZHORIK_BOT_DIR: path.join(TMP, "no-bot-dir"),
    GITHUB_REPOSITORY: "o/r",
    GITHUB_TOKEN: "gh",
    GITHUB_OUTPUT: path.join(TMP, "gh-output"),
    ...extra,
  };
}

const answer = (cmd, env) => run(process.execPath, [path.join(CLOUD, "answer.mjs"), cmd], { env: answerEnv(env) });

function hook(transcript, cwd) {
  const child = execFile(process.execPath, [path.join(CLOUD, "hooks", "handoff.mjs")], {
    env: { PATH: process.env.PATH, HOME: TMP, ZHORIK_GH_REPO: "o/r", ZHORIK_GH_TOKEN: "pat", ZHORIK_GH_API: base },
  });
  child.stdin.end(JSON.stringify({ hook_event_name: "StopFailure", transcript_path: transcript, cwd }));
  return new Promise((resolve) => child.on("exit", resolve));
}

const git = (cwd, ...args) => run("git", ["-C", cwd, "-c", "user.name=t", "-c", "user.email=t@t", ...args]);

test("хук передаёт неотвеченные сообщения в облако один раз", async () => {
  const transcript = path.join(TMP, "t.jsonl");
  const line = (content) => JSON.stringify({ type: "user", message: { role: "user", content } });
  fs.writeFileSync(
    transcript,
    [
      line('<channel source="plugin:telegram:telegram" chat_id="42" message_id="7" user="margo" ts="t1">що в брифі?</channel>'),
      line("You've hit your session limit"),
    ].join("\n"),
  );
  mock.calls = [];
  await hook(transcript);
  const d = mock.calls.filter((c) => c.url === "/repos/o/r/dispatches");
  assert.equal(d.length, 1);
  assert.equal(d[0].auth, "Bearer pat");
  assert.equal(d[0].body.event_type, "zhorik_handoff");
  assert.equal(d[0].body.client_payload.jobs[0].chat_id, "42");
  assert.equal(d[0].body.client_payload.jobs[0].messages[0].text, "що в брифі?");
  assert.equal(d[0].body.client_payload.report_chat_id, "42");
  assert.equal(d[0].body.client_payload.project, null);

  mock.calls = [];
  await hook(transcript);
  assert.equal(mock.calls.length, 0, "повторно то же сообщение не отправляется");
});

test("dispatch: plan находит задание, run отвечает в Telegram без токена в окружении claude", async () => {
  const eventPath = path.join(TMP, "event.json");
  fs.writeFileSync(
    eventPath,
    JSON.stringify({ client_payload: { jobs: [{ chat_id: "42", messages: [{ message_id: "7", user: "margo", ts: "t1", text: "привіт" }], history: [] }] } }),
  );
  mock.calls = [];
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  const env = { GITHUB_EVENT_NAME: "repository_dispatch", GITHUB_EVENT_PATH: eventPath };
  await answer("plan", env);
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=true/);
  await answer("run", env);

  const sent = mock.calls.filter((c) => c.url.endsWith("/sendMessage"));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].body.chat_id, "42");
  assert.equal(sent[0].body.text, "Ну таки здрасьте! Відповідаю з резерву 😉");
  assert.equal(sent[0].body.reply_parameters.message_id, 7);

  const call = JSON.parse(fs.readFileSync(path.join(TMP, "claude-call.json"), "utf8"));
  assert.equal(call.hasTgToken, false);
  assert.ok(call.argv.includes("-p"));
  assert.match(call.argv[call.argv.indexOf("--append-system-prompt") + 1], /Резервный режим[\s\S]*Черновик характера/);
});

test("чужой чат из dispatch игнорируется", async () => {
  const eventPath = path.join(TMP, "event-foreign.json");
  fs.writeFileSync(eventPath, JSON.stringify({ client_payload: { jobs: [{ chat_id: "13", messages: [{ text: "spam" }] }], project: { repo: "o/proj", branch: "main" }, report_chat_id: "13" } }));
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  await answer("plan", { GITHUB_EVENT_NAME: "repository_dispatch", GITHUB_EVENT_PATH: eventPath });
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=false/);
});

test("в публичном репозитории резерв не запускается", async () => {
  mock.private = false;
  try {
    await assert.rejects(answer("plan", { GITHUB_EVENT_NAME: "schedule", GITHUB_EVENT_PATH: "/nonexistent" }), (err) => {
      assert.equal(err.code, 1);
      assert.match(err.stdout, /не приватный/);
      return true;
    });
  } finally {
    mock.private = true;
  }
});

test("расписание: свежий пульс — Telegram не трогаем", async () => {
  mock.calls = [];
  await answer("plan", { GITHUB_EVENT_NAME: "schedule", ZHORIK_HEARTBEAT: String(Math.floor(Date.now() / 1000)) });
  assert.equal(mock.calls.filter((c) => c.url.includes("/bot")).length, 0);
});

test("расписание без пульса — Telegram не трогаем", async () => {
  mock.calls = [];
  await answer("plan", { GITHUB_EVENT_NAME: "schedule" });
  assert.equal(mock.calls.filter((c) => c.url.includes("/bot")).length, 0);
});

test("расписание: пульс пропал — забираем сообщения, отвечаем и подтверждаем", async () => {
  mock.updates = [
    { update_id: 500, message: { message_id: 9, chat: { id: 42 }, from: { id: 42, first_name: "Маргарита" }, date: 1790000000, text: "ти тут?" } },
    { update_id: 501, message: { message_id: 3, chat: { id: 13 }, from: { id: 13 }, date: 1790000001, text: "чужий" } },
  ];
  mock.calls = [];
  const env = { GITHUB_EVENT_NAME: "schedule", ZHORIK_HEARTBEAT: String(Math.floor(Date.now() / 1000) - 3600) };
  await answer("plan", env);
  await answer("run", env);
  const urls = mock.calls.map((c) => `${c.url.split("/").pop()}${c.body.offset ? `@${c.body.offset}` : ""}`);
  assert.deepEqual(urls.filter((u) => u.startsWith("get") || u.startsWith("send")), [
    "getUpdates",
    "sendChatAction",
    "sendMessage",
    "getUpdates@502",
  ]);
  mock.updates = [];
});

test("выключатель ZHORIK_CLOUD=off", async () => {
  mock.calls = [];
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  await answer("plan", { GITHUB_EVENT_NAME: "schedule", ZHORIK_CLOUD: "off", ZHORIK_HEARTBEAT: "1" });
  assert.equal(mock.calls.length, 0);
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=false/);
});

test("без секретов резерв спокойно выходит, а не падает", async () => {
  mock.calls = [];
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  const { stdout } = await answer("plan", { GITHUB_EVENT_NAME: "schedule", TELEGRAM_BOT_TOKEN: "", ZHORIK_HEARTBEAT: "1" });
  assert.match(stdout, /не настроен/);
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=false/);
  assert.equal(mock.calls.length, 0);
});

// ---------- продовження проєкту: Жорик пушить роботу сам, облако бере гілку з «GitHub» (тут — локальний bare-репозиторій) ----------

test("хук: проєкт у теці сесії передається один раз на коміт, без вмісту", async () => {
  const work = path.join(TMP, "work");
  fs.mkdirSync(work);
  await run("git", ["init", "-q", "-b", "main", work]);
  await git(work, "remote", "add", "origin", "https://github.com/o/proj.git");
  fs.writeFileSync(path.join(work, "NEXT.md"), "далі: крок 1\n");
  await git(work, "add", "-A");
  await git(work, "commit", "-q", "-m", "init");
  const transcript = path.join(TMP, "t2.jsonl");
  fs.writeFileSync(transcript, JSON.stringify({ type: "user", message: { role: "user", content: "зроби кабінет" } }));

  mock.calls = [];
  await hook(transcript, work);
  let d = mock.calls.filter((c) => c.url === "/repos/o/r/dispatches");
  assert.equal(d.length, 1);
  assert.deepEqual(d[0].body.client_payload.project, { repo: "o/proj", branch: "main" });
  assert.deepEqual(d[0].body.client_payload.jobs, []);
  assert.ok(!JSON.stringify(d[0].body).includes("зроби кабінет"), "текст розмови не передається");

  mock.calls = [];
  await hook(transcript, work);
  assert.equal(mock.calls.length, 0, "той самий коміт вдруге не передається");

  fs.writeFileSync(path.join(work, "NEXT.md"), "далі: крок 2\n");
  await git(work, "commit", "-q", "-am", "крок 1");
  mock.calls = [];
  await hook(transcript, work);
  d = mock.calls.filter((c) => c.url === "/repos/o/r/dispatches");
  assert.equal(d.length, 1, "новий коміт — нова передача");
});

test("продовження: облако бере гілку, працює в zhorik/cloud, секрети не комітить, звітує в Telegram", async () => {
  const remotes = path.join(TMP, "remotes");
  const bare = path.join(remotes, "o", "proj.git");
  fs.mkdirSync(path.dirname(bare), { recursive: true });
  await run("git", ["init", "-q", "--bare", "-b", "main", bare]);
  const seed = path.join(TMP, "seed");
  await run("git", ["clone", "-q", bare, seed]);
  fs.writeFileSync(path.join(seed, "NEXT.md"), "далі: крок 1\n");
  await git(seed, "add", "-A");
  await git(seed, "commit", "-q", "-m", "init");
  await git(seed, "push", "-q", "origin", "HEAD:main");

  const eventPath = path.join(TMP, "event-project.json");
  fs.writeFileSync(eventPath, JSON.stringify({ client_payload: { jobs: [], project: { repo: "o/proj", branch: "main" }, report_chat_id: "42" } }));
  const env = { GITHUB_EVENT_NAME: "repository_dispatch", GITHUB_EVENT_PATH: eventPath, ZHORIK_GIT_BASE: `file://${remotes}`, ZHORIK_WORK_DIR: path.join(TMP, "wd") };
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  mock.calls = [];
  await answer("plan", env);
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=true/);
  await answer("run", env);

  const call = JSON.parse(fs.readFileSync(path.join(TMP, "claude-call.json"), "utf8"));
  assert.equal(call.dev, true);
  assert.equal(call.hasTgToken, false);
  assert.equal(call.hasGitCfg, false);
  assert.equal(call.hasGhToken, false);
  const sent = mock.calls.filter((c) => c.url.endsWith("/sendMessage"));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].body.chat_id, "42");
  assert.match(sent[0].body.text, /^Зробив крок/);

  const check = path.join(TMP, "check");
  await run("git", ["clone", "-q", "-b", "zhorik/cloud", bare, check]);
  assert.equal(fs.readFileSync(path.join(check, "feature.txt"), "utf8"), "крок\n");
  assert.match(fs.readFileSync(path.join(check, ".zhorik", "CLOUD_LOG.md"), "utf8"), /Зробив крок/);
  assert.equal(fs.existsSync(path.join(check, ".env")), false, ".env не комітиться");
  const { stdout: mainHead } = await git(bare, "rev-parse", "main");
  const { stdout: seedHead } = await git(seed, "rev-parse", "HEAD");
  assert.equal(mainHead.trim(), seedHead.trim(), "main облако не чіпає");

  // сервер тим часом запушив новий крок у main — облако підтягує його в zhorik/cloud і працює далі
  fs.writeFileSync(path.join(seed, "server.txt"), "з сервера\n");
  await git(seed, "add", "-A");
  await git(seed, "commit", "-q", "-m", "крок сервера");
  await git(seed, "push", "-q", "origin", "HEAD:main");
  await answer("plan", env);
  await answer("run", env);
  const check2 = path.join(TMP, "check2");
  await run("git", ["clone", "-q", "-b", "zhorik/cloud", bare, check2]);
  assert.equal(fs.readFileSync(path.join(check2, "feature.txt"), "utf8"), "крок\nкрок\n");
  assert.equal(fs.readFileSync(path.join(check2, "server.txt"), "utf8"), "з сервера\n");
});

test("ZHORIK_CONTINUE=off: проєкт не продовжуємо", async () => {
  const eventPath = path.join(TMP, "event-project.json");
  fs.writeFileSync(path.join(TMP, "gh-output"), "");
  await answer("plan", { GITHUB_EVENT_NAME: "repository_dispatch", GITHUB_EVENT_PATH: eventPath, ZHORIK_CONTINUE: "off" });
  assert.match(fs.readFileSync(path.join(TMP, "gh-output"), "utf8"), /has_jobs=false/);
});
