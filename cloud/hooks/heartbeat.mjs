#!/usr/bin/env node
// Пульс компьютера для облачного резерва: раз в 5 минут (launchd/cron/Планировщик) проверяем, что бот
// на компьютере жив (запущен Claude Code с каналом Telegram), и пишем время в переменную репозитория
// ZHORIK_HEARTBEAT. Нет пульса дольше ZHORIK_TAKEOVER_AFTER_MIN — облако само забирает сообщения из Telegram.
// Настройки те же, что у handoff.mjs (~/.claude/zhorik-cloud.env). ZHORIK_ALIVE_PATTERN — как узнать процесс бота.
import os from "node:os";
import { execFileSync } from "node:child_process";
import { loadConfig } from "./env.mjs";

const conf = loadConfig();
const pattern = new RegExp(conf.ZHORIK_ALIVE_PATTERN || "channels.*telegram|telegram.*server\\.ts", "i");

function processes() {
  if (os.platform() === "win32") {
    return execFileSync("powershell", ["-NoProfile", "-Command", "Get-CimInstance Win32_Process | ForEach-Object { $_.CommandLine }"], {
      encoding: "utf8",
    });
  }
  return execFileSync("ps", ["-Ao", "command="], { encoding: "utf8" });
}

async function main() {
  const repo = conf.ZHORIK_GH_REPO;
  const token = conf.ZHORIK_GH_TOKEN;
  if (!repo || !token) throw new Error("нет ZHORIK_GH_REPO/ZHORIK_GH_TOKEN");
  const alive = processes().split(/\r?\n/).some((line) => pattern.test(line) && !line.includes("heartbeat.mjs"));
  if (!alive) {
    console.log("бот на компьютере не запущен — пульс не отправляю");
    return;
  }
  const value = String(Math.floor(Date.now() / 1000));
  const headers = { authorization: `Bearer ${token}`, accept: "application/vnd.github+json", "content-type": "application/json" };
  const base = `${conf.ZHORIK_GH_API || "https://api.github.com"}/repos/${repo}/actions/variables`;
  let res = await fetch(`${base}/ZHORIK_HEARTBEAT`, { method: "PATCH", headers, body: JSON.stringify({ name: "ZHORIK_HEARTBEAT", value }) });
  if (res.status === 404) {
    res = await fetch(base, { method: "POST", headers, body: JSON.stringify({ name: "ZHORIK_HEARTBEAT", value }) });
  }
  if (res.status >= 300) throw new Error(`GitHub ответил ${res.status}: ${(await res.text()).slice(0, 200)}`);
  console.log("пульс отправлен");
}

main().catch((err) => {
  console.error("heartbeat:", err.message);
  process.exit(1);
});
