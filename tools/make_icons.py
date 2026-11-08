"""由 assets/logo-source.png 產生 Android launcher icon 與 PWA icon 全套資源。

用法：python3 tools/make_icons.py [來源圖路徑]
來源圖建議為正方形 PNG；輸出會覆蓋 res/mipmap-* 與 web/icon-*.png。
"""
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'assets/logo-source.png')
RES = os.path.join(ROOT, 'android/app/src/main/res')
WEB = os.path.join(ROOT, 'web')
PREVIEW = os.path.join(ROOT, 'assets/icon-preview.png')

src = Image.open(SRC).convert('RGB')
if src.width != src.height:
    # 非正方形先置中裁成正方形，避免變形
    s = min(src.size)
    src = src.crop(((src.width - s) // 2, (src.height - s) // 2,
                    (src.width + s) // 2, (src.height + s) // 2))
S = 1080
src = src.resize((S, S), Image.LANCZOS)
a = np.asarray(src).astype(np.int16)

# 1) 純彩虹背景：彩虹以水平方向變化為主，逐「列」用上／下邊緣線性插值重建，
#    抹掉中央主體後再柔化接縫（左右插值會因四角皆紅橙而失真，不能用）。
bg = a.copy()
for x in range(S):
    top = np.median(a[0:16, x], axis=0)
    bottom = np.median(a[S - 16:S, x], axis=0)
    for y in range(S):
        w = y / (S - 1)
        bg[y, x] = top * (1 - w) + bottom * w
bg_img = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4))
bga = np.asarray(bg_img).astype(np.int16)

# 2) 差分摳出主體，只留最大連通區（小人 + 白色巴士），透明底
diff = np.max(np.abs(a - bga), axis=2)
mask = ndimage.binary_fill_holes(diff > 40)
lab, n = ndimage.label(mask)
keep = np.zeros_like(mask)
for i in range(1, n + 1):
    if (lab == i).sum() > S * S * 0.002:
        keep |= (lab == i)
keep = ndimage.binary_erosion(ndimage.binary_dilation(keep, iterations=2), iterations=1)
alpha = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.1))
subject = src.convert('RGBA')
subject.putalpha(alpha)
subject = subject.crop(alpha.getbbox())

# 3) 背景層／前景層（108dp 基準，主體縮到安全區內）
def background_layer(size):
    return bg_img.resize((size, size), Image.LANCZOS)

def foreground_layer(size):
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    target_h = int(size * 0.56)
    w = max(1, round(subject.width * target_h / subject.height))
    s = subject.resize((w, target_h), Image.LANCZOS)
    canvas.paste(s, ((size - w) // 2, (size - target_h) // 2), s)
    return canvas

def legacy(size, round_=False):
    im = src.resize((size, size), Image.LANCZOS).convert('RGBA')
    if round_:
        m = Image.new('L', (size, size), 0)
        ImageDraw.Draw(m).ellipse((0, 0, size - 1, size - 1), fill=255)
        im.putalpha(m)
    return im

def adaptive_composite(size):
    return Image.alpha_composite(background_layer(size).convert('RGBA'), foreground_layer(size))

# 4) 各密度輸出
DENS = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
ADP = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
for d, px in DENS.items():
    out = os.path.join(RES, 'mipmap-' + d)
    os.makedirs(out, exist_ok=True)
    legacy(px).save(os.path.join(out, 'ic_launcher.png'))
    legacy(px, True).save(os.path.join(out, 'ic_launcher_round.png'))
    background_layer(ADP[d]).convert('RGB').save(os.path.join(out, 'ic_launcher_background.png'))
    foreground_layer(ADP[d]).save(os.path.join(out, 'ic_launcher_foreground.png'))

anydpi = os.path.join(RES, 'mipmap-anydpi-v26')
os.makedirs(anydpi, exist_ok=True)
XML = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''
for f in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    with open(os.path.join(anydpi, f), 'w', encoding='utf-8') as fh:
        fh.write(XML)

# 5) PWA / favicon
legacy(192).convert('RGB').save(os.path.join(WEB, 'icon-192.png'))
legacy(512).convert('RGB').save(os.path.join(WEB, 'icon-512.png'))
legacy(96).convert('RGB').save(os.path.join(WEB, 'favicon.png'))
adaptive_composite(512).convert('RGB').save(os.path.join(WEB, 'icon-maskable-512.png'))

# 6) 預覽圖
PAD, CELL, TOP = 28, 240, 56
prev = Image.new('RGB', (PAD * 4 + CELL * 3, TOP + CELL + 74), (16, 21, 27))
dr = ImageDraw.Draw(prev)
try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
except Exception:
    font = ImageFont.load_default()
for i, label in enumerate(['Square (legacy)', 'Round icon', 'Adaptive / maskable']):
    x = PAD + i * (CELL + PAD)
    im = legacy(CELL) if i == 0 else (legacy(CELL, True) if i == 1 else adaptive_composite(CELL))
    if i == 2:
        m = Image.new('L', (CELL, CELL), 0)
        ImageDraw.Draw(m).ellipse((0, 0, CELL - 1, CELL - 1), fill=255)
        im.putalpha(m)
    prev.paste(im, (x, TOP), im)
    dr.text((x, TOP + CELL + 18), label, fill=(220, 228, 236), font=font)
dr.text((PAD, 16), 'HKETA Hybrid — launcher icon preview', fill=(255, 204, 51), font=font)
prev.save(PREVIEW)
print('icons generated from', SRC, '->', PREVIEW)
