// Облачный резерв Жорика — общие функции без сети и без файлов (их проверяют тесты в cloud/test).
// Используются и в облаке (answer.mjs), и на компьютере (hooks/handoff.mjs).

const ENTITIES = { "&quot;": '"', "&apos;": "'", "&#39;": "'", "&lt;": "<", "&gt;": ">", "&amp;": "&" };
export const decodeEntities = (s) => String(s ?? "").replace(/&(quot|apos|#39|lt|gt|amp);/g, (m) => ENTITIES[m]);

// <channel source="plugin:telegram:telegram" chat_id="…" message_id="…" user="…" ts="…">текст</channel>
const CHANNEL_RE = /<channel\s+([^>]*)>([\s\S]*?)<\/channel>/g;
const ATTR_RE = /([\w-]+)="([^"]*)"/g;

export function parseChannelTags(text) {
  const out = [];
  for (const m of String(text ?? "").matchAll(CHANNEL_RE)) {
    const attrs = {};
    for (const a of m[1].matchAll(ATTR_RE)) attrs[a[1]] = decodeEntities(a[2]);
    out.push({ attrs, body: decodeEntities(m[2]).trim() });
  }
  return out;
}

// Транскрипт Claude Code — JSONL; битые строки пропускаем.
export function readTranscript(raw) {
  const entries = [];
  for (const line of String(raw ?? "").split("\n")) {
    if (!line.trim()) continue;
    try {
      entries.push(JSON.parse(line));
    } catch {
      // строка дописывается прямо сейчас или повреждена — не наша забота
    }
  }
  return entries;
}

function blocks(entry) {
  const c = entry?.message?.content;
  if (typeof c === "string") return [{ type: "text", text: c }];
  return Array.isArray(c) ? c : [];
}

// Входящие сообщения Telegram и ответы бота (инструмент reply плагина) в порядке транскрипта.
export function scanTranscript(entries) {
  const inbound = [];
  const replies = [];
  entries.forEach((entry, pos) => {
    const role = entry?.message?.role || entry?.type;
    for (const b of blocks(entry)) {
      if (role === "user" && b?.type === "text") {
        for (const { attrs, body } of parseChannelTags(b.text)) {
          if (!/telegram/i.test(attrs.source || "") || !attrs.chat_id) continue;
          inbound.push({
            key: `${attrs.chat_id}:${attrs.message_id || pos}`,
            chat_id: attrs.chat_id,
            message_id: attrs.message_id || "",
            user: attrs.user || "",
            ts: attrs.ts || "",
            at: Date.parse(attrs.ts) || Date.parse(entry?.timestamp) || NaN,
            text: body,
            pos,
          });
        }
      } else if (role === "assistant" && b?.type === "tool_use" && /(^|__)reply$/.test(b.name || "")) {
        const chat = b.input?.chat_id;
        if (chat != null) replies.push({ chat_id: String(chat), text: String(b.input?.text || ""), pos });
      }
    }
  });
  return { inbound, replies };
}

// Сообщения, на которые бот так и не ответил (после его последнего ответа в этом чате), и которые мы ещё не передавали
// в облако. Старую переписку не поднимаем: только свежие (maxAgeMs) и не больше limit последних.
export function pendingInbound(scan, sentKeys = new Set(), { now = Date.now(), maxAgeMs = 2 * 3600e3, limit = 10 } = {}) {
  const lastReply = new Map();
  for (const r of scan.replies) lastReply.set(r.chat_id, r.pos);
  return scan.inbound
    .filter((m) => m.pos > (lastReply.get(m.chat_id) ?? -1) && !sentKeys.has(m.key))
    .filter((m) => !Number.isFinite(m.at) || now - m.at <= maxAgeMs)
    .slice(-limit);
}

const clip = (s, n) => (s.length > n ? `${s.slice(0, n - 1)}…` : s);

// Короткая история разговора для облака: последние реплики обеих сторон в этом чате.
export function buildHistory(scan, chatId, { limit = 12, maxLen = 600, beforePos = Infinity } = {}) {
  const items = [
    ...scan.inbound.filter((m) => m.chat_id === chatId && m.pos < beforePos).map((m) => ({ pos: m.pos, who: "user", text: m.text })),
    ...scan.replies.filter((r) => r.chat_id === chatId && r.pos < beforePos).map((r) => ({ pos: r.pos, who: "bot", text: r.text })),
  ].sort((a, b) => a.pos - b.pos);
  return items.slice(-limit).map(({ who, text }) => ({ who, text: clip(text, maxLen) }));
}

// Telegram принимает до 4096 символов — режем по абзацам/строкам/пробелам.
export function splitMessage(text, max = 4000) {
  const parts = [];
  let rest = String(text ?? "").trim();
  while (rest.length > max) {
    let cut = rest.lastIndexOf("\n\n", max);
    if (cut < max / 2) cut = rest.lastIndexOf("\n", max);
    if (cut < max / 2) cut = rest.lastIndexOf(" ", max);
    if (cut < max / 2) cut = max;
    parts.push(rest.slice(0, cut).trim());
    rest = rest.slice(cut).trim();
  }
  if (rest) parts.push(rest);
  return parts;
}

export const parseAllowList = (s) => new Set(String(s ?? "").split(/[\s,;]+/).map((x) => x.trim()).filter(Boolean));

// Пульс компьютера: «none» — пульс не настроен (облако само Telegram не опрашивает), «fresh» — компьютер на связи, «stale» — молчит.
export function heartbeatState(raw, nowSec, staleAfterSec) {
  const hb = Number(raw);
  if (!raw || !Number.isFinite(hb) || hb <= 0) return "none";
  return nowSec - hb > staleAfterSec ? "stale" : "fresh";
}

function describeMessage(msg) {
  const text = msg.text ?? msg.caption ?? "";
  const extras = [];
  if (msg.photo) extras.push("фото");
  if (msg.voice || msg.audio) extras.push("голосове/аудіо");
  if (msg.video || msg.video_note) extras.push("відео");
  if (msg.document) extras.push(`файл «${msg.document.file_name || "без назви"}»`);
  if (msg.sticker) extras.push(`стікер ${msg.sticker.emoji || ""}`.trim());
  const note = extras.length ? `[вкладення: ${extras.join(", ")} — у резервному режимі їх не видно]` : "";
  return [text, note].filter(Boolean).join("\n");
}

// Обновления getUpdates → задания «ответить в чат» (по одному на чат, все его сообщения вместе).
export function groupUpdates(updates, allow) {
  const jobs = new Map();
  let maxId = 0;
  for (const u of updates || []) {
    maxId = Math.max(maxId, Number(u.update_id) || 0);
    const msg = u.message;
    if (!msg?.chat) continue;
    const chat = String(msg.chat.id);
    if (!allow.has(chat) && !allow.has(String(msg.from?.id ?? ""))) continue;
    if (!jobs.has(chat)) jobs.set(chat, { chat_id: chat, messages: [], history: [] });
    jobs.get(chat).messages.push({
      message_id: String(msg.message_id),
      user: msg.from?.first_name || msg.from?.username || "",
      ts: msg.date ? new Date(msg.date * 1000).toISOString() : "",
      text: describeMessage(msg),
    });
  }
  return { jobs: [...jobs.values()], confirmOffset: maxId ? maxId + 1 : 0 };
}

// Запрос для «мозга»: новые сообщения + недавняя история. Отвечать — только текстом ответа.
export function buildPrompt(job) {
  const lines = ["[Резервний режим] Нові повідомлення в Telegram, на які треба відповісти."];
  if (job.history?.length) {
    lines.push("", "Попередня розмова (старіші вгорі):");
    for (const h of job.history) lines.push(`${h.who === "bot" ? "Жорик" : "Співрозмовник"}: ${h.text}`);
  }
  lines.push("", "Нові повідомлення:");
  for (const m of job.messages || []) {
    const who = m.user ? `${m.user}` : "Співрозмовник";
    lines.push(`<message from="${who}"${m.ts ? ` at="${m.ts}"` : ""}>`, m.text, "</message>");
  }
  lines.push("", "Напиши відповідь, яку треба надіслати в цей чат. Виведи лише текст відповіді, без пояснень.");
  return lines.join("\n");
}
