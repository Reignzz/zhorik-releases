// Настройки хуков на компьютере: ~/.claude/zhorik-cloud.env (строки KEY=VALUE), поверх — переменные окружения.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export const ENV_FILE = path.join(os.homedir(), ".claude", "zhorik-cloud.env");

export function loadEnvFile(file = ENV_FILE) {
  const out = {};
  try {
    for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
      if (m) out[m[1]] = m[2].replace(/^(['"])(.*)\1$/, "$2");
    }
  } catch {
    // файла нет — берём только окружение
  }
  return out;
}

export const loadConfig = () => ({ ...loadEnvFile(), ...process.env });
