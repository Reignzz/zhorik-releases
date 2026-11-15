#!/usr/bin/env python3
"""Синтез оригинальных фирменных звуков Beamox в мягком «iOS-стиле» (маримба/колокольчик).
Звуки свои — не копии Apple. Запуск: python3 tools/make_sounds.py → app/src/main/res/raw/*.wav"""
import numpy as np, wave, os

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def note(freq, dur, kind="marimba", vol=0.6):
    t = np.arange(int(SR * dur)) / SR
    if kind == "marimba":   # деревянная маримба: основной тон + 4-я гармоника, быстрое затухание
        env = np.exp(-t * 7.5)
        sig = np.sin(2*np.pi*freq*t) + 0.35*np.sin(2*np.pi*freq*4*t)*np.exp(-t*20) + 0.15*np.sin(2*np.pi*freq*10*t)*np.exp(-t*40)
    elif kind == "bell":    # стеклянный колокольчик
        env = np.exp(-t * 3.2)
        sig = np.sin(2*np.pi*freq*t) + 0.5*np.sin(2*np.pi*freq*2.76*t)*np.exp(-t*5) + 0.25*np.sin(2*np.pi*freq*5.4*t)*np.exp(-t*9)
    else:                   # мягкая «капля»
        env = np.exp(-t * 14)
        sig = np.sin(2*np.pi*(freq*(1 + 0.25*np.exp(-t*30)))*t)
    attack = np.minimum(1, t / 0.004)
    return sig * env * attack * vol

def mix(events, total):
    out = np.zeros(int(SR * total))
    for start, sig in events:
        i = int(SR * start); j = min(len(out), i + len(sig))
        out[i:j] += sig[: j - i]
    # лёгкая «комнатная» реверберация
    for d, g in [(0.031, 0.25), (0.053, 0.18), (0.089, 0.12)]:
        k = int(SR * d); out[k:] += out[:-k] * g
    out /= max(1e-9, np.abs(out).max()) / 0.85
    return out

def save(name, data):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    print("ok", name, f"{len(data)/SR:.1f}s")

N = {"C5":523.25,"D5":587.33,"E5":659.25,"G5":783.99,"A5":880.0,"B5":987.77,"C6":1046.5,"D6":1174.66,"E6":1318.5,"G6":1567.98,"A4":440.0,"G4":392.0,"E4":329.63}

# Рингтон «Beamox Opening»: восходящая маримба-фраза, 2 повтора (~7 с)
phrase = [("E5",0),("G5",.18),("C6",.36),("E6",.54),("D6",.9),("B5",1.08),("G5",1.26),("A5",1.62),("C6",1.8),("E6",1.98),("G6",2.16)]
ev = []
for rep in range(2):
    base = rep * 3.4
    ev += [(base + st, note(N[n], 1.2, "marimba", .55)) for n, st in phrase]
save("beamox_ring.wav", mix(ev, 7.0))

# Уведомление «Glass»: два колокольчика
save("beamox_notify.wav", mix([(0, note(N["E6"], 1.4, "bell", .6)), (.13, note(N["B5"], 1.6, "bell", .5))], 1.9))

# Сообщение «Beamox Note»: три быстрые ноты маримбы
save("beamox_message.wav", mix([(0, note(N["G5"], .9)), (.11, note(N["C6"], .9)), (.22, note(N["E6"], 1.1))], 1.4))

# Будильник «Morning»: мягкая повторяющаяся мелодия (~10 с)
ev = []
for rep in range(4):
    b = rep * 2.4
    ev += [(b, note(N["C5"], 1.0, "bell", .5)), (b+.3, note(N["E5"], 1.0, "bell", .5)),
           (b+.6, note(N["G5"], 1.0, "bell", .5)), (b+.9, note(N["C6"], 1.4, "bell", .55))]
save("beamox_alarm.wav", mix(ev, 10.0))

# Звук «капля» для интерфейса / активации голоса
save("beamox_tap.wav", mix([(0, note(N["A5"], .25, "drop", .7))], .3))
save("beamox_listen.wav", mix([(0, note(N["E5"], .5, "bell", .5)), (.09, note(N["A5"], .6, "bell", .5))], .8))
