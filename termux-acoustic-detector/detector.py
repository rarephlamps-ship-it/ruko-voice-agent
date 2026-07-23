#!/data/data/com.termux/files/usr/bin/python
"""Local acoustic event detector for Termux.

Detects sound patterns consistent with speech, footsteps and multirotor/drone
propellers. It does not transcribe speech or identify speakers.
"""

from __future__ import annotations

import csv
import json
import math
import os
import shutil
import signal
import struct
import subprocess
import sys
import time
import wave
from collections import deque
from datetime import datetime, timedelta
from pathlib import Path

APP = Path.home() / ".local/share/termux-acoustic-detector"
EVENTS = APP / "events"
TMP = APP / "tmp"
LOG = APP / "events.csv"
CONFIG = Path.home() / ".config/termux-acoustic-detector/config.json"
STOP = False
LAST_ALERT: dict[str, float] = {}


def stop_handler(_sig, _frame):
    global STOP
    STOP = True


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, capture_output=True, check=check)


def require(command: str):
    if shutil.which(command) is None:
        raise RuntimeError(f"Missing command: {command}")


def load_config() -> dict:
    return json.loads(CONFIG.read_text(encoding="utf-8"))


def prepare():
    EVENTS.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    require("termux-microphone-record")
    require("ffmpeg")
    if not LOG.exists():
        with LOG.open("w", newline="", encoding="utf-8") as f:
            csv.writer(f).writerow([
                "timestamp", "classification", "confidence", "rms",
                "speech", "footstep", "drone", "dominant_hz", "audio"
            ])


def clean_old(days: int):
    if days <= 0:
        return
    cutoff = datetime.now() - timedelta(days=days)
    for path in EVENTS.glob("*.wav"):
        if datetime.fromtimestamp(path.stat().st_mtime) < cutoff:
            path.unlink(missing_ok=True)


def capture(seconds: int, sample_rate: int) -> Path:
    raw = TMP / "capture.m4a"
    wav = TMP / "capture.wav"
    raw.unlink(missing_ok=True)
    wav.unlink(missing_ok=True)
    run(["termux-microphone-record", "-q"], check=False)
    result = run(["termux-microphone-record", "-f", str(raw), "-l", str(seconds)], check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "Could not start microphone")
    time.sleep(seconds + 0.7)
    run(["termux-microphone-record", "-q"], check=False)
    if not raw.exists() or raw.stat().st_size < 1000:
        raise RuntimeError("No usable recording. Check Termux:API and microphone permission.")
    result = run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", str(raw),
        "-ac", "1", "-ar", str(sample_rate), "-c:a", "pcm_s16le", str(wav)
    ], check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "ffmpeg conversion failed")
    return wav


def read_pcm(path: Path, gain: float) -> tuple[int, list[float]]:
    with wave.open(str(path), "rb") as w:
        if w.getsampwidth() != 2:
            raise RuntimeError("Expected 16-bit PCM")
        rate = w.getframerate()
        channels = w.getnchannels()
        raw = w.readframes(w.getnframes())
    values = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    if channels == 1:
        samples = [max(-1.0, min(1.0, (v / 32768.0) * gain)) for v in values]
    else:
        samples = []
        for i in range(0, len(values), channels):
            v = sum(values[i:i + channels]) / channels
            samples.append(max(-1.0, min(1.0, (v / 32768.0) * gain)))
    mean = sum(samples) / max(1, len(samples))
    return rate, [v - mean for v in samples]


def rms(samples: list[float]) -> float:
    return math.sqrt(sum(v * v for v in samples) / max(1, len(samples)))


def goertzel(samples: list[float], rate: int, freq: float) -> float:
    n = len(samples)
    if n == 0:
        return 0.0
    k = int(0.5 + n * freq / rate)
    omega = 2.0 * math.pi * k / n
    coeff = 2.0 * math.cos(omega)
    q0 = q1 = q2 = 0.0
    for value in samples:
        q0 = coeff * q1 - q2 + value
        q2, q1 = q1, q0
    return max(0.0, q1 * q1 + q2 * q2 - coeff * q1 * q2) / (n * n)


def band_energy(samples: list[float], rate: int, freqs: list[int]) -> float:
    return sum(goertzel(samples, rate, f) for f in freqs) / max(1, len(freqs))


def clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def scale(value: float, low: float, high: float) -> float:
    return clamp((value - low) / max(1e-12, high - low))


def analyse(samples: list[float], rate: int) -> dict[str, float]:
    overall = rms(samples)
    frame = max(256, rate // 20)
    env = [rms(samples[i:i + frame]) for i in range(0, len(samples), frame)]
    median = sorted(env)[len(env) // 2] if env else 0.0
    impulse = (max(env) / max(1e-7, median)) if env else 0.0
    active = sum(1 for x in env if x > max(0.003, median * 1.7)) / max(1, len(env))

    speech_e = band_energy(samples, rate, [180, 250, 400, 700, 1000, 1600, 2400, 3200])
    low_e = band_energy(samples, rate, [45, 65, 90, 120, 160, 220])
    drone_base = band_energy(samples, rate, [80, 100, 125, 150, 180, 220, 260, 320])
    high_e = band_energy(samples, rate, [500, 750, 1000, 1500, 2000, 2500])
    total = speech_e + low_e + drone_base + high_e + 1e-12

    speech_ratio = speech_e / total
    low_ratio = low_e / total
    drone_ratio = (drone_base + 0.45 * high_e) / total

    # Drone signatures tend to be sustained and harmonic rather than impulsive.
    harmonic = 0.0
    best_f = 0.0
    best = 0.0
    for fundamental in range(70, 351, 10):
        e1 = goertzel(samples, rate, fundamental)
        eh = sum(goertzel(samples, rate, fundamental * m) for m in (2, 3, 4)) / 3
        score = e1 + 0.7 * eh
        if score > best:
            best, best_f = score, float(fundamental)
            harmonic = eh / max(1e-12, e1 + eh)

    speech = clamp(0.52 * scale(speech_ratio, 0.22, 0.58) + 0.28 * scale(active, 0.15, 0.75) + 0.20 * scale(impulse, 1.2, 4.0))
    footstep = clamp(0.46 * scale(low_ratio, 0.18, 0.62) + 0.40 * scale(impulse, 2.2, 10.0) + 0.14 * scale(0.5 - active, 0.0, 0.45))
    drone = clamp(0.38 * scale(drone_ratio, 0.30, 0.72) + 0.42 * scale(harmonic, 0.18, 0.68) + 0.20 * scale(active, 0.45, 0.95))

    return {
        "rms": overall, "speech": speech, "footstep": footstep,
        "drone": drone, "dominant_hz": best_f
    }


def classify(scores: dict[str, float], cfg: dict) -> tuple[str, float]:
    if scores["rms"] < cfg["minimum_rms"]:
        return "QUIET", 0.0
    candidates = {
        "SPEECH_PATTERN": scores["speech"],
        "FOOTSTEP_PATTERN": scores["footstep"],
        "DRONE_PATTERN": scores["drone"],
    }
    label = max(candidates, key=candidates.get)
    threshold = {
        "SPEECH_PATTERN": cfg["speech_threshold"],
        "FOOTSTEP_PATTERN": cfg["footstep_threshold"],
        "DRONE_PATTERN": cfg["drone_threshold"],
    }[label]
    return (label, candidates[label]) if candidates[label] >= threshold else ("UNKNOWN_SOUND", candidates[label])


def alert(label: str, confidence: float, cfg: dict):
    now = time.time()
    if now - LAST_ALERT.get(label, 0) < cfg["cooldown_seconds"]:
        return
    LAST_ALERT[label] = now
    if cfg.get("notifications") and shutil.which("termux-notification"):
        run(["termux-notification", "--title", "Acoustic Detector", "--content", f"{label}: {confidence:.0%}"], check=False)
    if cfg.get("vibrate") and shutil.which("termux-vibrate"):
        run(["termux-vibrate", "-d", "400"], check=False)


def log_event(label: str, confidence: float, scores: dict[str, float], audio: str):
    stamp = datetime.now().isoformat(timespec="seconds")
    with LOG.open("a", newline="", encoding="utf-8") as f:
        csv.writer(f).writerow([
            stamp, label, f"{confidence:.4f}", f"{scores['rms']:.6f}",
            f"{scores['speech']:.4f}", f"{scores['footstep']:.4f}",
            f"{scores['drone']:.4f}", f"{scores['dominant_hz']:.1f}", audio
        ])
    print(f"[{stamp}] {label:<18} {confidence:>5.0%} rms={scores['rms']:.4f} speech={scores['speech']:.2f} steps={scores['footstep']:.2f} drone={scores['drone']:.2f} peak={scores['dominant_hz']:.0f}Hz")


def main():
    signal.signal(signal.SIGINT, stop_handler)
    signal.signal(signal.SIGTERM, stop_handler)
    prepare()
    cfg = load_config()
    clean_old(int(cfg["retention_days"]))
    print("Termux Acoustic Detector started. Ctrl+C stops it.")
    print(f"Log: {LOG}")
    errors = 0
    while not STOP:
        try:
            wav = capture(int(cfg["record_seconds"]), int(cfg["sample_rate"]))
            rate, samples = read_pcm(wav, float(cfg["software_gain"]))
            scores = analyse(samples, rate)
            label, confidence = classify(scores, cfg)
            saved = ""
            if cfg.get("save_event_audio") and label not in {"QUIET", "UNKNOWN_SOUND"}:
                dst = EVENTS / f"{datetime.now():%Y%m%d_%H%M%S}_{label}_{int(confidence*100)}.wav"
                shutil.copy2(wav, dst)
                saved = str(dst)
            log_event(label, confidence, scores, saved)
            if label not in {"QUIET", "UNKNOWN_SOUND"}:
                alert(label, confidence, cfg)
            errors = 0
        except Exception as exc:
            errors += 1
            print(f"ERROR: {exc}", file=sys.stderr)
            if errors >= 5:
                return 1
            time.sleep(3)
        finally:
            for p in TMP.glob("capture.*"):
                p.unlink(missing_ok=True)
        if not STOP:
            time.sleep(float(cfg["pause_seconds"]))
    run(["termux-microphone-record", "-q"], check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
