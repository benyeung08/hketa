#!/usr/bin/env python3
"""Generates every Android launcher-icon asset from one square source image.

Source: app/icon-source.png (the user-supplied rainbow bus artwork, 1080x1080).

Outputs:
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png        legacy square
  mipmap-*/ic_launcher_round.png                                  legacy round
  mipmap-*/ic_launcher_background.png  (108dp)                    adaptive background
  mipmap-*/ic_launcher_foreground.png (108dp, transparent)        adaptive foreground
  mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml
  app/src/main/ic_launcher-playstore.png (512x512)

Adaptive note: the artwork is already a complete, centred composition, so the
WHOLE image is used as the adaptive background and the foreground is fully
transparent. Every launcher mask (circle / squircle / rounded square) therefore
shows the same artwork instead of a cropped "picture inside a picture".
There is no parallax layer by design; see README for how to add one.

Only Pillow is required. Re-run after replacing app/icon-source.png.
"""

from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "app/icon-source.png"

# dp -> px at each density (mdpi = 1x)
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def square_canvas(size: int):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def round_mask(size: int) -> Image.Image:
    """4x supersampled circular alpha mask to avoid jagged edges."""
    s = size * 4
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s - 1, s - 1), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def main() -> int:
    if not SOURCE.exists():
        print(f"missing source: {SOURCE}")
        return 2
    src = Image.open(SOURCE).convert("RGBA")
    if src.width != src.height:
        side = min(src.width, src.height)
        left = (src.width - side) // 2
        top = (src.height - side) // 2
        src = src.crop((left, top, left + side, top + side))
        print(f"source was not square -> centre-cropped to {side}x{side}")
    print(f"source: {SOURCE.name} {src.size}")

    for density, px in LEGACY.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = src.resize((px, px), Image.LANCZOS)

        icon.convert("RGB").save(folder / "ic_launcher.png", "PNG", optimize=True)

        rounded = square_canvas(px)
        rounded.paste(icon, (0, 0))
        rounded.putalpha(round_mask(px))
        rounded.save(folder / "ic_launcher_round.png", "PNG", optimize=True)
        print(f"  mipmap-{density}: ic_launcher {px}px, round {px}px")

        apx = ADAPTIVE[density]
        src.resize((apx, apx), Image.LANCZOS).convert("RGB").save(
            folder / "ic_launcher_background.png", "PNG", optimize=True
        )
        square_canvas(apx).save(
            folder / "ic_launcher_foreground.png", "PNG", optimize=True
        )
        print(f"  mipmap-{density}: adaptive layers {apx}px")

    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    print("  mipmap-anydpi-v26: ic_launcher.xml + ic_launcher_round.xml")

    store = src.resize((512, 512), Image.LANCZOS).convert("RGB")
    store.save(RES.parent / "ic_launcher-playstore.png", "PNG", optimize=True)
    print("  ic_launcher-playstore.png: 512x512")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
