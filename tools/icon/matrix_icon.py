#!/usr/bin/env python3
"""Regenerates the launcher icons and TV banner in the matrix neon-green style.

Recolors the original navy/cyan artwork (kept in tools/icon/source/) to neon
green on black and adds a faint "digital rain" backdrop. Run from the repo root:

    python3 tools/icon/matrix_icon.py
"""
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools/icon/source"
RES = ROOT / "app/src/main/res"

BLACK = np.array([2, 8, 4], float)
NEON = np.array([57, 255, 20], float)       # was cyan
RING = np.array([0, 190, 60], float)        # was the blue ring
PALE = np.array([215, 255, 210], float)     # was white text
GLYPHS = "0123456789ABCDEFZ:.=*+<>|"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"


def recolor(img: Image.Image):
    """Returns (rgb float array, logo coverage 0..1) for navy/cyan/white artwork."""
    a = np.asarray(img.convert("RGBA"), float)
    rgb, alpha = a[..., :3], a[..., 3] / 255.0
    bg = np.median(rgb[alpha > 0.5][rgb[alpha > 0.5][:, 2] < 90], axis=0)
    t = np.maximum((rgb[..., 2] - bg[2]) / (253 - bg[2]), (rgb[..., 0] - bg[0]) / (251 - bg[0]))
    t = np.clip((t - 0.06) / 0.94, 0, 1)
    safe = np.maximum(t, 1e-3)[..., None]
    u = bg + (rgb - bg) / safe
    white = np.clip((u[..., 0] - 60) / 180, 0, 1)[..., None]
    cyan = np.clip((u[..., 1] - 140) / 75, 0, 1)[..., None]
    target = (RING * (1 - cyan) + NEON * cyan) * (1 - white) + PALE * white
    return target, t * alpha


def rain(w: int, h: int, seed: int = 7) -> Image.Image:
    rnd = random.Random(seed)
    img = Image.new("RGB", (w, h), tuple(int(c) for c in BLACK))
    d = ImageDraw.Draw(img)
    size = max(8, h // 18)
    font = ImageFont.truetype(FONT, size)
    for x in range(0, w, int(size * 0.9)):
        length = rnd.randint(4, 14)
        head = rnd.randint(-length, h // size + 2)
        for i in range(length):
            y = (head - i) * size
            if y < -size or y > h:
                continue
            fade = 1 - i / length
            g = int(30 + 120 * fade) if i else 210
            col = (int(g * 0.25), g, int(g * 0.3)) if i else (170, 255, 170)
            d.text((x, y), rnd.choice(GLYPHS), font=font, fill=col)
    return img.point(lambda v: int(v * 0.42))


def compose(src: Image.Image, with_rain=True) -> Image.Image:
    target, t = recolor(src)
    w, h = src.size
    base = np.asarray(rain(w, h), float) if with_rain else np.tile(BLACK, (h, w, 1))
    mask = Image.fromarray((t * 255).astype(np.uint8))
    glow = np.asarray(mask.filter(ImageFilter.GaussianBlur(max(2, w // 60))), float)[..., None] / 255
    base = np.clip(base * (1 - 0.6 * glow) + NEON * 0.55 * glow, 0, 255)
    out = base * (1 - t[..., None]) + target * t[..., None]
    return Image.fromarray(out.astype(np.uint8), "RGB")


def logo_only(src: Image.Image) -> Image.Image:
    """Transparent foreground layer: recolored logo plus its glow."""
    target, t = recolor(src)
    mask = Image.fromarray((t * 255).astype(np.uint8))
    glow = np.asarray(mask.filter(ImageFilter.GaussianBlur(src.size[0] // 50)), float) / 255
    a = np.clip(t + glow * 0.7, 0, 1)
    col = (target * t[..., None] + NEON * (glow * 0.7)[..., None]) / np.maximum(a, 1e-3)[..., None]
    rgba = np.dstack([np.clip(col, 0, 255), a * 255]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def shaped(img: Image.Image, round_icon: bool) -> Image.Image:
    s = img.size[0] * 4
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    pad = int(s * 0.04)
    if round_icon:
        d.ellipse((pad, pad, s - pad, s - pad), fill=255)
    else:
        d.rounded_rectangle((pad, pad, s - pad, s - pad), radius=int(s * 0.12), fill=255)
    out = img.convert("RGBA")
    out.putalpha(m.resize(img.size, Image.LANCZOS))
    return out


def main():
    densities = {"ldpi": 0.75, "mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

    # Logo crop of the original foreground (its navy square), recolored.
    fg_src = Image.open(SRC / "ic_launcher_foreground.png").convert("RGBA")
    a = np.asarray(fg_src)
    ys, xs = np.where(a[..., 3] > 200)
    square = fg_src.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    icon_master = compose(square.resize((1024, 1024), Image.LANCZOS))

    # Adaptive icon: transparent foreground logo + rain background.
    fg_master = logo_only(fg_src.resize((1728, 1728), Image.LANCZOS))
    bg_master = rain(432, 432, seed=11)
    for name, k in densities.items():
        d = RES / f"mipmap-{name}"
        n = round(48 * k)
        shaped(icon_master.resize((n, n), Image.LANCZOS), False).save(d / "ic_launcher.png")
        shaped(icon_master.resize((n, n), Image.LANCZOS), True).save(d / "ic_launcher_round.png")
        if name != "ldpi":
            fg_master.resize((round(108 * k),) * 2, Image.LANCZOS).save(d / "ic_launcher_foreground.png")
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(exist_ok=True)
    bg_master.save(nodpi / "ic_launcher_background_matrix.png")
    shaped(icon_master.resize((512, 512), Image.LANCZOS), False).save(RES / "ic_launcher-web.png")

    # TV banner.
    banner = compose(Image.open(SRC / "app_banner.png"))
    banner.save(RES / "drawable/app_banner.png")
    for name, size in {"mdpi": 240, "hdpi": 160, "xhdpi": 320, "xxhdpi": 480, "xxxhdpi": 640}.items():
        # hdpi was historically 160x90; keep each file's existing size.
        banner.resize((size, size * 9 // 16), Image.LANCZOS).save(RES / f"drawable-{name}/app_banner.png")


if __name__ == "__main__":
    main()
