# -*- coding: utf-8 -*-
"""由 art/logo_source.png 重新產生全部 launcher icon 資源。

用法（喺 repo 根目錄）：
    python3 scripts/gen_icon.py

輸出：
    art/icon_preview.png                          預覽（左：方形，右：adaptive 合成）
    app/src/main/res/mipmap-*/ic_launcher*.png    API<26
    app/src/main/res/drawable-nodpi/*.png         adaptive 前／背景
    app/src/main/res/mipmap-anydpi-v26/*.xml     API26+ adaptive icon
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "logo_source.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

img = Image.open(SRC).convert("RGB")
if img.width != img.height:
    raise SystemExit("logo_source.png 必須係正方形（目前 %s）" % (img.size,))

# --- 傳統 launcher icons ---
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for folder, size in DENSITIES.items():
    d = os.path.join(RES, "mipmap-" + folder)
    os.makedirs(d, exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))

    s = size * 4
    big = img.resize((s, s), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s - 1, s - 1), fill=255)
    big.putalpha(mask)
    big.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher_round.png"))

# --- adaptive icon (API 26+) ---
# 規格：前／背景皆為 108×108dp 畫布，但系統只顯示中央 72×72dp（66.7%）。
# 所以完整圖必須縮到 66.7% 再置中，機械人頭與車輪先唔會被遮罩裁走。
N = 512
SAFE = round(N * 72 / 108)          # 341，剛好等於可視區
OFF = (N - SAFE) // 2               # 85
nodpi = os.path.join(RES, "drawable-nodpi")
os.makedirs(nodpi, exist_ok=True)

# 背景：取原圖頂端一列純彩虹色帶拉伸滿版（四角被裁到時都係彩虹，唔會露白邊）
stripe = img.crop((0, 8, img.width, 9))
bg = stripe.resize((N, N), Image.LANCZOS)
bg.save(os.path.join(nodpi, "ic_launcher_background.png"))

# 前景：完整原圖等比縮到 SAFE×SAFE 置中，其餘透明。
# 可視區會被呢張圖完全填滿，所以唔會出現「縮小版彩虹」嘅接縫。
fg = Image.new("RGBA", (N, N), (0, 0, 0, 0))
fg.paste(img.resize((SAFE, SAFE), Image.LANCZOS), (OFF, OFF))
fg.save(os.path.join(nodpi, "ic_launcher_foreground.png"))

anydpi = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(anydpi, exist_ok=True)
ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(anydpi, name), "w", encoding="utf-8") as f:
        f.write(ADAPTIVE)

# 預覽：左＝legacy 方圖，右＝模擬 Android 圓形遮罩後的 adaptive 外觀
prev = Image.new("RGB", (N * 2 + 24, N), (255, 255, 255))
prev.paste(img.resize((N, N), Image.LANCZOS), (0, 0))
composite = bg.convert("RGBA")
composite.alpha_composite(fg)
round_mask = Image.new("L", (N, N), 0)
ImageDraw.Draw(round_mask).ellipse((OFF, OFF, OFF + SAFE - 1, OFF + SAFE - 1), fill=255)
canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
canvas.paste(composite, (0, 0), round_mask)
prev.paste(canvas.convert("RGB"), (N + 24, 0))
prev.save(os.path.join(ROOT, "art", "icon_preview.png"))

print("logo_source:", img.size)
print("legacy:", ", ".join("%s=%d" % kv for kv in DENSITIES.items()))
print("adaptive: %dx%d canvas, %dx%d safe area" % (N, N, SAFE, SAFE))
