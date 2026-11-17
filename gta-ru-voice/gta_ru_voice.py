#!/usr/bin/env python3
"""
Живой русский перевод речи из игры (PS5 -> карта захвата -> Mac).

Звук с карты захвата -> поиск фраз (VAD) -> распознавание (Whisper, MLX на Apple Silicon)
-> перевод EN->RU -> озвучка встроенным голосом macOS (say) в выбранные наушники.

Запуск:
    python gta_ru_voice.py --list                       # показать устройства
    python gta_ru_voice.py --input "USB Video" --output "Наушники"
"""
import argparse
import queue
import subprocess
import sys
import threading
import time

import numpy as np
import sounddevice as sd
import webrtcvad
from deep_translator import GoogleTranslator

SR = 16000            # частота для Whisper/VAD
FRAME = 480           # 30 мс при 16 кГц
HALLUCINATIONS = {
    "", "you", "thank you.", "thanks for watching!", "thank you for watching.",
    "bye.", "music", "[music]", "(music)", "♪", "...",
}


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


# ---------------------------------------------------------------- распознавание
class Recognizer:
    def __init__(self, model: str):
        import mlx_whisper  # только Apple Silicon
        self.mlx = mlx_whisper
        self.model = model
        log(f"Загружаю модель {model} (первый раз скачается ~1.5 ГБ)...")
        self.mlx.transcribe(np.zeros(SR, dtype=np.float32), path_or_hf_repo=model,
                            language="en", verbose=None)
        log("Модель готова.")

    def __call__(self, audio: np.ndarray) -> str:
        r = self.mlx.transcribe(
            audio, path_or_hf_repo=self.model, language="en",
            condition_on_previous_text=False, verbose=None,
            no_speech_threshold=0.6, logprob_threshold=-1.0,
        )
        parts = [s["text"].strip() for s in r.get("segments", [])
                 if s.get("no_speech_prob", 0) < 0.6 and s.get("avg_logprob", 0) > -1.0]
        text = " ".join(parts).strip()
        return "" if text.lower() in HALLUCINATIONS else text


# ---------------------------------------------------------------- захват + VAD
class Segmenter:
    """Режет поток на фразы: начало по VAD, конец по паузе или по лимиту длины."""

    def __init__(self, out_q, aggressiveness, silence_ms, max_sec):
        self.vad = webrtcvad.Vad(aggressiveness)
        self.out_q = out_q
        self.buf = np.zeros(0, dtype=np.float32)
        self.pre = []                     # предзапись ~300 мс
        self.ring = []                    # последние решения VAD
        self.speech = []
        self.active = False
        self.silence = 0
        self.silence_frames = silence_ms // 30
        self.max_frames = int(max_sec * 1000 / 30)

    def feed(self, x16k: np.ndarray):
        self.buf = np.concatenate([self.buf, x16k])
        while len(self.buf) >= FRAME:
            fr, self.buf = self.buf[:FRAME], self.buf[FRAME:]
            pcm = (np.clip(fr, -1, 1) * 32767).astype(np.int16).tobytes()
            is_sp = self.vad.is_speech(pcm, SR)
            self._step(fr, is_sp)

    def _step(self, fr, is_sp):
        self.ring = (self.ring + [is_sp])[-10:]
        if not self.active:
            self.pre = (self.pre + [fr])[-10:]
            if sum(self.ring) >= 6:
                self.active, self.speech, self.silence = True, list(self.pre), 0
            return
        self.speech.append(fr)
        self.silence = 0 if is_sp else self.silence + 1
        if self.silence >= self.silence_frames or len(self.speech) >= self.max_frames:
            seg = np.concatenate(self.speech)
            if len(seg) > SR * 0.5:
                self.out_q.put(seg)
            self.active, self.speech, self.pre, self.ring = False, [], [], []


def make_resampler(dev_sr: int):
    if dev_sr % SR == 0:
        f = dev_sr // SR
        return lambda x: x[: len(x) - len(x) % f].reshape(-1, f).mean(axis=1)
    def interp(x):
        n = int(len(x) * SR / dev_sr)
        return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return interp


# ---------------------------------------------------------------- озвучка
def speaker(tts_q, voice, rate, out_dev):
    while True:
        text = tts_q.get()
        # если накопилась очередь — выкидываем старое и говорим быстрее, чтобы не отставать
        while tts_q.qsize() > 2:
            text = tts_q.get()
        r = rate + 40 * tts_q.qsize()
        cmd = ["say", "-v", voice, "-r", str(r)]
        if out_dev:
            cmd += ["-a", out_dev]
        subprocess.run(cmd + [text])


def worker(seg_q, tts_q, recognize, translator):
    while True:
        seg = seg_q.get()
        t0 = time.time()
        try:
            en = recognize(seg)
            if not en:
                continue
            ru = translator.translate(en)
        except Exception as e:  # сеть/модель — не падаем
            log("Ошибка:", e)
            continue
        log(f"EN: {en}\n         RU: {ru}   ({time.time() - t0:.1f} c)")
        tts_q.put(ru)


# ---------------------------------------------------------------- main
def main():
    p = argparse.ArgumentParser(description="Русская озвучка речи из игры в реальном времени")
    p.add_argument("--list", action="store_true", help="показать аудиоустройства и выйти")
    p.add_argument("--input", help="имя (часть имени) входа: карта захвата")
    p.add_argument("--output", help="устройство для русского голоса (часть имени), по умолчанию системное")
    p.add_argument("--voice", default="Milena", help="голос macOS (Milena, Yuri ...)")
    p.add_argument("--rate", type=int, default=210, help="скорость речи, слов/мин")
    p.add_argument("--model", default="mlx-community/whisper-large-v3-turbo")
    p.add_argument("--vad", type=int, default=3, choices=[0, 1, 2, 3],
                   help="строгость детектора речи (3 = меньше реагирует на музыку)")
    p.add_argument("--silence-ms", type=int, default=600, help="пауза, завершающая фразу")
    p.add_argument("--max-sec", type=float, default=8.0, help="максимальная длина фразы")
    a = p.parse_args()

    if a.list:
        print(sd.query_devices())
        print("\nВыходы для голоса (say):")
        subprocess.run(["say", "-a", "?"])
        print("\nРусские голоса:")
        subprocess.run("say -v '?' | grep ru_RU", shell=True)
        return

    if sys.platform != "darwin":
        sys.exit("Скрипт рассчитан на macOS.")

    dev = sd.query_devices(a.input, "input") if a.input else sd.query_devices(kind="input")
    dev_sr = int(dev["default_samplerate"])
    ch = min(2, dev["max_input_channels"])
    log(f"Вход: {dev['name']} ({dev_sr} Гц, {ch} кан.)")

    recognize = Recognizer(a.model)
    translator = GoogleTranslator(source="en", target="ru")
    seg_q, tts_q, raw_q = queue.Queue(), queue.Queue(), queue.Queue()
    seg = Segmenter(seg_q, a.vad, a.silence_ms, a.max_sec)
    resample = make_resampler(dev_sr)

    threading.Thread(target=worker, args=(seg_q, tts_q, recognize, translator), daemon=True).start()
    threading.Thread(target=speaker, args=(tts_q, a.voice, a.rate, a.output), daemon=True).start()

    def cb(indata, frames, t, status):
        raw_q.put(indata.mean(axis=1).copy())

    block = int(dev_sr * 0.03)
    with sd.InputStream(device=dev["index"], channels=ch, samplerate=dev_sr,
                        blocksize=block, dtype="float32", callback=cb):
        log("Слушаю игру. Ctrl+C — выход.")
        try:
            while True:
                seg.feed(resample(raw_q.get()))
        except KeyboardInterrupt:
            log("Выход.")


if __name__ == "__main__":
    main()
