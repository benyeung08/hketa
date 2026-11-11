#!/usr/bin/env python3
"""
Generate Android launcher icons from res/android/logo-source.png.

Output (all under res/android/icons/):
  <density>/ic_launcher.png              legacy square icon (48..192 px)
  <density>/ic_launcher_background.png   adaptive background (108dp * scale)
  <density>/ic_launcher_foreground.png  adaptive foreground, transparent (108dp * scale)
  <density>/ic_launcher_monochrome.png  Android 13+ themed icon (108dp * scale)

Plus:
  res/android/logo-playstore-512.png     512px store listing icon
  res/android/logo-preview.png           circle / rounded-square / squircle preview
  www/img/app-logo.png                   in-app logo shown in the Settings screen

Requires: Pillow, numpy, opencv-python (only needed to RE-generate; the PNGs
are committed, so a normal `cordova build` does not need Python at all).

    python3 scripts/generate-icons.py
"""

from pathlib import Path
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "res" / "android" / "logo-source.png"
OUT = ROOT / "res" / "android" / "icons"
WWW = ROOT / "www" / "img"

# (folder, scale, legacy px) — adaptive canvas is always 108dp * scale
DENSITIES = [
    ("mdpi", 1.0, 48),
    ("hdpi", 1.5, 72),
    ("xhdpi", 2.0, 96),
    ("xxhdpi", 3.0, 144),
    ("xxxhdpi", 4.0, 192),
]

FOREGROUND_RATIO = 0.78   # adaptive: subject height inside the 108dp canvas
LEGACY_RATIO = 0.86       # legacy square icon: subject height
DIFF_THRESHOLD = 45.0     # colour distance from the rainbow background


def build_layers():
    src = cv2.imread(str(SRC), cv2.IMREAD_COLOR)
    if src is None:
        raise SystemExit(f"Cannot read {SRC}")
    h, w = src.shape[:2]
    if h != w:
        raise SystemExit(f"Source must be square, got {w}x{h}")

    # The rainbow background is essentially a horizontal gradient: the top 70px
    # strip contains no subject, so each column's median is a clean sample.
    top = src[0:70]
    col = np.median(top, axis=0).astype(np.float32).reshape(1, w, 3)
    col = cv2.GaussianBlur(col, (0, 0), 6).reshape(w, 3)
    bg_bgr = np.tile(col.reshape(1, w, 3), (h, 1, 1)).astype(np.uint8)

    # Subject = pixels that differ from the reconstructed background.
    diff = np.linalg.norm(src.astype(np.float32) - bg_bgr.astype(np.float32), axis=2)
    mask = (diff > DIFF_THRESHOLD).astype(np.uint8) * 255
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))

    n, labels, stats, _ = cv2.connectedComponentsWithStats(mask)
    keep = np.zeros_like(mask)
    for i in sorted(range(1, n), key=lambda k: stats[k, cv2.CC_STAT_AREA], reverse=True)[:6]:
        if stats[i, cv2.CC_STAT_AREA] > 150:
            keep[labels == i] = 255
    keep = cv2.morphologyEx(keep, cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8))
    alpha = cv2.GaussianBlur(keep, (0, 0), 1.0)

    rgb = cv2.cvtColor(src, cv2.COLOR_BGR2RGB)
    bg_rgb = cv2.cvtColor(bg_bgr, cv2.COLOR_BGR2RGB)
    fg = np.dstack([rgb, alpha])

    ys, xs = np.where(alpha > 25)
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    return Image.fromarray(bg_rgb), Image.fromarray(fg[y0:y1, x0:x1], "RGBA")


def compose(bg, subject, n, ratio):
    """Return (background, foreground, monochrome, flattened legacy) at n×n."""
    background = bg.resize((n, n), Image.LANCZOS)
    h = max(1, round(n * ratio))
    w = max(1, round(subject.width * h / subject.height))
    art = subject.resize((w, h), Image.LANCZOS)

    foreground = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    foreground.alpha_composite(art, ((n - w) // 2, (n - h) // 2))

    mono = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    mono.putalpha(foreground.getchannel("A"))

    flat = background.convert("RGBA")
    flat.alpha_composite(foreground)
    return background, foreground, mono, flat.convert("RGB")


def preview(bg, subject):
    """Four launcher appearances side by side so the crop can be eyeballed."""
    n = 720
    _, fg, mono, _ = compose(bg, subject, n, FOREGROUND_RATIO)
    base = bg.resize((n, n), Image.LANCZOS).convert("RGBA")
    base.alpha_composite(fg)
    art = base.convert("RGB")

    canvas = Image.new("RGB", (n * 4 + 100, n + 60), (18, 24, 34))
    draw = ImageDraw.Draw(canvas)
    masks = []
    circle = Image.new("L", (n, n), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, n - 1, n - 1), fill=255)
    masks.append(circle)
    rounded = Image.new("L", (n, n), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, n - 1, n - 1), radius=n // 4, fill=255)
    masks.append(rounded)
    squircle = Image.new("L", (n, n), 0)
    ImageDraw.Draw(squircle).rounded_rectangle((0, 0, n - 1, n - 1), radius=n // 2, fill=255)
    masks.append(squircle)

    labels = ["circle", "rounded square", "squircle"]
    for i, m in enumerate(masks):
        x = 20 + i * (n + 20)
        canvas.paste(art, (x, 30), m)
        draw.text((x + 8, 10), labels[i], fill=(220, 228, 240))

    # Android 13+ themed icon: white silhouette tinted by the system.
    x = 20 + 3 * (n + 20)
    tile = Image.new("RGBA", (n, n), (32, 40, 58, 255))
    tile.alpha_composite(mono)
    canvas.paste(tile.convert("RGB"), (x, 30), masks[0])
    draw.text((x + 8, 10), "themed (mono)", fill=(220, 228, 240))
    canvas.save(ROOT / "res" / "android" / "logo-preview.png")


def main():
    if not SRC.exists():
        raise SystemExit(f"Missing {SRC}")
    OUT.mkdir(parents=True, exist_ok=True)
    WWW.mkdir(parents=True, exist_ok=True)
    bg, subject = build_layers()
    print(f"subject bbox (tight): {subject.width}x{subject.height}")

    for folder, scale, legacy_px in DENSITIES:
        d = OUT / folder
        d.mkdir(parents=True, exist_ok=True)
        n = round(108 * scale)
        background, foreground, mono, flat = compose(bg, subject, n, FOREGROUND_RATIO)
        background.save(d / "ic_launcher_background.png")
        foreground.save(d / "ic_launcher_foreground.png")
        mono.save(d / "ic_launcher_monochrome.png")

        _, _, _, legacy = compose(bg, subject, legacy_px, LEGACY_RATIO)
        legacy.save(d / "ic_launcher.png")
        print(f"{folder:8} legacy={legacy_px:3}px  adaptive={n:3}px")

    _, _, _, store = compose(bg, subject, 512, LEGACY_RATIO)
    store.save(ROOT / "res" / "android" / "logo-playstore-512.png")
    _, _, _, in_app = compose(bg, subject, 256, LEGACY_RATIO)
    in_app.save(WWW / "app-logo.png")
    preview(bg, subject)
    print("wrote logo-playstore-512.png, logo-preview.png, www/img/app-logo.png")


if __name__ == "__main__":
    main()
