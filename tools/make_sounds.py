"""Synthesises SonderChess's move sounds (no recordings: every sound is generated here).
Run: python3 tools/make_sounds.py  -> app/src/main/res/raw/snd_<set>_<event>.ogg (needs numpy, scipy, ffmpeg)."""
import numpy as np, subprocess, os, tempfile
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)

def t(sec): return np.arange(int(SR * sec)) / SR
def env(sec, decay, attack=0.002):
    x = t(sec); a = np.clip(x / attack, 0, 1); return a * np.exp(-x / decay)
def band(sig, lo, hi):
    b, a = butter(2, [lo / (SR / 2), hi / (SR / 2)], btype="band"); return lfilter(b, a, sig)
def low(sig, f):
    b, a = butter(2, f / (SR / 2)); return lfilter(b, a, sig)
def pad(sig, sec): return np.concatenate([np.zeros(int(SR * sec)), sig])
def mix(*parts):
    n = max(len(p) for p in parts); out = np.zeros(n)
    for p in parts: out[: len(p)] += p
    return out
def tone(f, sec, decay, amp=1.0):
    x = t(sec); return amp * np.sin(2 * np.pi * f * x) * env(sec, decay)
def sweep(f0, f1, sec, decay, amp=1.0):
    x = t(sec); f = f0 * (f1 / f0) ** (x / sec); ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * np.sin(ph) * env(sec, decay)

# One "tap" per set: what a piece landing sounds like.
def tap_default(power=1.0):
    n = rng.standard_normal(int(SR * 0.09))
    knock = band(n, 700, 3500) * env(0.09, 0.012) * 0.9
    body = mix(tone(170, 0.12, 0.03, 0.8), tone(320, 0.08, 0.015, 0.4))
    return mix(knock, body) * power
def tap_nature(power=1.0):
    return mix(sweep(1400, 520, 0.16, 0.05, 0.9), tone(260, 0.1, 0.03, 0.25)) * power
def tap_metal(power=1.0):
    n = rng.standard_normal(int(SR * 0.02)); click = band(n, 2000, 8000) * env(0.02, 0.004) * 0.6
    ring = mix(*(tone(f, 0.45, d, a) for f, d, a in [(1180, 0.16, 0.5), (2710, 0.09, 0.3), (4130, 0.05, 0.2), (620, 0.2, 0.25)]))
    return mix(click, ring) * power
def tap_marble(power=1.0):
    n = rng.standard_normal(int(SR * 0.04)); click = band(n, 2500, 9000) * env(0.04, 0.005) * 0.9
    ring = mix(tone(1650, 0.18, 0.035, 0.35), tone(3300, 0.1, 0.02, 0.15))
    return mix(click, ring) * power
def tap_space(power=1.0):
    s = sweep(520, 1150, 0.14, 0.06, 0.7)
    return mix(s, pad(s * 0.35, 0.09), pad(s * 0.12, 0.18)) * power

TAPS = {"default": tap_default, "nature": tap_nature, "metal": tap_metal, "marble": tap_marble, "space": tap_space}

def events(tap, name):
    chime_base = {"default": 523, "nature": 660, "metal": 784, "marble": 880, "space": 440}[name]
    move = tap(1.0)
    capture = mix(tap(1.25), pad(low(rng.standard_normal(int(SR * 0.05)), 1500) * env(0.05, 0.01) * 0.5, 0.004))
    castle = mix(tap(0.9), pad(tap(0.9), 0.11))
    check = mix(tap(1.0), pad(tone(chime_base * 2, 0.35, 0.09, 0.35), 0.03))
    promote = mix(tap(1.0), pad(sweep(chime_base, chime_base * 2, 0.3, 0.12, 0.35), 0.04))
    end = mix(*(pad(tone(chime_base * r, 0.9, 0.35, 0.3), i * 0.09) for i, r in enumerate([1, 1.25, 1.5])))
    return {"move": move, "capture": capture, "castle": castle, "check": check, "promote": promote, "end": end}

def write(sig, path):
    sig = sig / max(1e-9, np.max(np.abs(sig))) * 0.85
    fade = min(len(sig), int(SR * 0.01)); sig[-fade:] *= np.linspace(1, 0, fade)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f: tmp = f.name
    wavfile.write(tmp, SR, (sig * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.remove(tmp)

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, tap in TAPS.items():
        for ev, sig in events(tap, name).items():
            write(sig, os.path.join(OUT, f"snd_{name}_{ev}.ogg"))
    print("done")
