// node --test cloud/test
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  buildHistory,
  buildPrompt,
  groupUpdates,
  heartbeatState,
  parseAllowList,
  parseChannelTags,
  pendingInbound,
  readTranscript,
  scanTranscript,
  splitMessage,
} from "../lib.mjs";

const NOW = { now: Date.parse("2026-09-25T07:05:00Z") }; // сообщения в тестах — от 07:00
const tgIn = (chat, id, text, extra = "") =>
  `<channel source="plugin:telegram:telegram" chat_id="${chat}" message_id="${id}" user="margo" ts="2026-09-25T07:00:00Z"${extra}>${text}</channel>`;
const user = (content) => JSON.stringify({ type: "user", message: { role: "user", content } });
const reply = (chat, text) =>
  JSON.stringify({
    type: "assistant",
    message: { role: "assistant", content: [{ type: "tool_use", name: "mcp__plugin_telegram_telegram__reply", input: { chat_id: chat, text } }] },
  });

test("parseChannelTags: атрибуты и текст, HTML-сущности", () => {
  const [m] = parseChannelTags(tgIn("42", "7", "Привіт &amp; &quot;бриф&quot;"));
  assert.equal(m.attrs.chat_id, "42");
  assert.equal(m.attrs.message_id, "7");
  assert.equal(m.body, 'Привіт & "бриф"');
});

test("readTranscript пропускает битые строки", () => {
  assert.equal(readTranscript(`${user("a")}\n{битая\n\n${user("b")}`).length, 2);
});

test("pendingInbound: только сообщения после последнего ответа и ещё не переданные", () => {
  const raw = [
    user(tgIn("42", "1", "перше")),
    reply("42", "відповідь"),
    user([{ type: "text", text: tgIn("42", "2", "друге") }]),
    user(tgIn("42", "3", "третє")),
    user(tgIn("99", "5", "інший чат")),
    user("звичайний текст без каналу"),
  ].join("\n");
  const scan = scanTranscript(readTranscript(raw));
  assert.deepEqual(
    pendingInbound(scan, new Set(), NOW).map((m) => m.key),
    ["42:2", "42:3", "99:5"],
  );
  assert.deepEqual(
    pendingInbound(scan, new Set(["42:2", "99:5"]), NOW).map((m) => m.key),
    ["42:3"],
  );
});

test("pendingInbound: старые сообщения не поднимаем, свежих — не больше limit", () => {
  const at = (iso) => `<channel source="plugin:telegram:telegram" chat_id="42" message_id="${iso}" ts="${iso}">x</channel>`;
  const scan = scanTranscript(
    readTranscript([user(at("2026-09-25T01:00:00Z")), user(at("2026-09-25T09:30:00Z")), user(at("2026-09-25T09:40:00Z"))].join("\n")),
  );
  const now = Date.parse("2026-09-25T10:00:00Z");
  assert.deepEqual(
    pendingInbound(scan, new Set(), { now }).map((m) => m.message_id),
    ["2026-09-25T09:30:00Z", "2026-09-25T09:40:00Z"],
  );
  assert.equal(pendingInbound(scan, new Set(), { now, limit: 1 }).length, 1);
});

test("pendingInbound: сообщения не из Telegram не считаются", () => {
  const scan = scanTranscript(readTranscript(user('<channel source="plugin:fakechat:fakechat" chat_id="1">hi</channel>')));
  assert.equal(pendingInbound(scan).length, 0);
});

test("buildHistory: обе стороны, по порядку, с обрезкой", () => {
  const raw = [user(tgIn("42", "1", "питання")), reply("42", "x".repeat(700)), user(tgIn("42", "2", "нове"))].join("\n");
  const scan = scanTranscript(readTranscript(raw));
  const pending = pendingInbound(scan, new Set(), NOW);
  const h = buildHistory(scan, "42", { beforePos: pending[0].pos });
  assert.deepEqual(h.map((x) => x.who), ["user", "bot"]);
  assert.equal(h[1].text.length, 600);
});

test("splitMessage: части не длиннее лимита, текст не теряется", () => {
  const text = Array.from({ length: 50 }, (_, i) => `Абзац ${i} ${"слово ".repeat(30)}`).join("\n\n");
  const parts = splitMessage(text, 1000);
  assert.ok(parts.length > 1);
  assert.ok(parts.every((p) => p.length <= 1000));
  const norm = (s) => s.replace(/\s+/g, " ").trim();
  assert.equal(norm(parts.join(" ")), norm(text));
  assert.deepEqual(splitMessage("  коротко  "), ["коротко"]);
  assert.deepEqual(splitMessage(""), []);
});

test("heartbeatState", () => {
  assert.equal(heartbeatState("", 1000, 60), "none");
  assert.equal(heartbeatState("abc", 1000, 60), "none");
  assert.equal(heartbeatState("990", 1000, 60), "fresh");
  assert.equal(heartbeatState("900", 1000, 60), "stale");
});

test("groupUpdates: только разрешённые чаты, по заданию на чат, offset для подтверждения", () => {
  const allow = parseAllowList("42, 77");
  const { jobs, confirmOffset } = groupUpdates(
    [
      { update_id: 10, message: { message_id: 1, chat: { id: 42 }, from: { id: 42, first_name: "Маргарита" }, date: 1790000000, text: "раз" } },
      { update_id: 11, message: { message_id: 2, chat: { id: 13 }, from: { id: 13 }, date: 1790000001, text: "чужий" } },
      { update_id: 12, message: { message_id: 3, chat: { id: 42 }, from: { id: 42 }, date: 1790000002, photo: [{}], caption: "дивись" } },
      { update_id: 13, edited_message: {} },
    ],
    allow,
  );
  assert.equal(confirmOffset, 14);
  assert.equal(jobs.length, 1);
  assert.equal(jobs[0].chat_id, "42");
  assert.equal(jobs[0].messages.length, 2);
  assert.equal(jobs[0].messages[0].user, "Маргарита");
  assert.match(jobs[0].messages[1].text, /дивись\n\[вкладення: фото/);
});

test("buildPrompt: история и новые сообщения, сообщение обёрнуто в <message>", () => {
  const p = buildPrompt({
    chat_id: "42",
    history: [{ who: "user", text: "що в брифі?" }, { who: "bot", text: "три задачі" }],
    messages: [{ user: "Маргарита", ts: "2026-09-25T07:00:00Z", text: "а четверта?" }],
  });
  assert.match(p, /Співрозмовник: що в брифі\?\nЖорик: три задачі/);
  assert.match(p, /<message from="Маргарита" at="2026-09-25T07:00:00Z">\nа четверта\?\n<\/message>/);
});
