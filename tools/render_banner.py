# Renders release/banner.png in Minecraft's own font, read from the cached vanilla jar.
import math
import sys
import zipfile
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from render_icon import ROOT, render  # noqa: E402

W, H = 1600, 560
JAR = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
jar = zipfile.ZipFile(JAR)


def vanilla(path):
    return Image.open(BytesIO(jar.read(f"assets/minecraft/textures/{path}"))).convert("RGBA")


# ascii.png is a 16x16 grid of 8x8 glyphs
FONT = np.asarray(vanilla("font/ascii.png"))


def glyph(ch):
    code = ord(ch)
    gx, gy = (code % 16) * 8, (code // 16) * 8
    cell = FONT[gy:gy + 8, gx:gx + 8, 3] > 0
    cols = np.where(cell.any(0))[0]
    width = 4 if ch == " " else (cols.max() + 1 if len(cols) else 0)
    return cell, width


def text(s, scale, rgb):
    """Minecraft-style text: 1 px spacing, a drop shadow at 1/4 brightness offset by one font pixel."""
    glyphs = [glyph(c) for c in s]
    wpx = sum(w + 1 for _, w in glyphs) + 1
    img = Image.new("RGBA", (wpx * scale, 9 * scale), (0, 0, 0, 0))
    px = img.load()
    shadow = tuple(c // 4 for c in rgb)
    for dx, col in ((1, shadow), (0, rgb)):
        x = 0
        for cell, w in glyphs:
            for gy, gx in zip(*np.where(cell)):
                for sy in range(scale):
                    for sx in range(scale):
                        px[(x + gx + dx) * scale + sx, (gy + dx) * scale + sy] = (*col, 255)
            x += w + 1
    return img


def outlined(s, scale, rgb, edge):
    """Glyphs grown by one font pixel for an outline: plain Minecraft shadows break big letters apart."""
    glyphs = [glyph(c) for c in s]
    wpx = sum(w + 1 for _, w in glyphs) + 3
    mask = np.zeros((11, wpx), dtype=bool)
    x = 1
    for cell, w in glyphs:
        mask[1:9, x:x + 8] |= cell
        x += w + 1
    grown = mask.copy()
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            grown |= np.roll(np.roll(mask, dy, 0), dx, 1)
    shadow = np.roll(np.roll(grown, 1, 0), 1, 1) & ~grown
    rgba = np.zeros((11, wpx, 4), dtype=np.uint8)
    rgba[shadow] = (0, 0, 0, 90)
    rgba[grown] = (*edge, 255)
    rgba[mask] = (*rgb, 255)
    return Image.fromarray(rgba, "RGBA").resize((wpx * scale, 11 * scale), Image.NEAREST)


bg = np.zeros((H, W, 4), dtype=np.uint8)
top, horizon = np.array([96, 156, 236]), np.array([196, 226, 250])
for y in range(H):
    t = min(1.0, y / 360)
    bg[y, :, :3] = top + (horizon - top) * t
bg[..., 3] = 255
img = Image.fromarray(bg, "RGBA")


def hills(base, amp, waves, rgb, step=8):
    """A blocky hill silhouette: heights snapped to `step` px so it reads as pixel art."""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    a = np.zeros((H, W, 4), dtype=np.uint8)
    for x in range(0, W, step):
        h = base - sum(A * math.sin(x / L + P) for A, L, P in waves) * amp
        h = int(round(h / step) * step)
        a[h:, x:x + step, :3] = rgb
        a[h:, x:x + step, 3] = 255
    layer = Image.fromarray(a, "RGBA")
    return layer


img.alpha_composite(hills(330, 40, [(1.0, 170, 0.3), (0.5, 70, 1.7)], (150, 190, 150)))
img.alpha_composite(hills(380, 34, [(1.0, 120, 2.1), (0.4, 45, 0.4)], (104, 158, 80)))

# vanilla textures at 4x (64 px blocks)
grass = np.asarray(vanilla("block/grass_block_top.png")).astype(np.float32)
grass[..., :3] *= np.array([145, 189, 89]) / 255.0  # plains grass colour
grass = Image.fromarray(grass.clip(0, 255).astype(np.uint8), "RGBA").resize((64, 64), Image.NEAREST)
road = vanilla("block/dirt_path_top.png").resize((64, 64), Image.NEAREST)
for x in range(0, W, 64):
    for y in range(416, H, 64):
        img.alpha_composite(grass if y < 480 else road, (x, y))


def coach(wood, width, yaw, pitch, mirror=False):
    c = render(wood, yaw, pitch, res=1400)
    if mirror:
        c = c.transpose(Image.FLIP_LEFT_RIGHT)
    return c.resize((width, round(c.height * width / c.width)), Image.LANCZOS)


def place(c, cx, bottom):
    img.alpha_composite(c, (cx - c.width // 2, bottom - c.height))


place(coach("spruce", 400, 24, 12), 250, 528)
place(coach("cherry", 400, 24, 12, mirror=True), W - 250, 528)
place(coach("oak", 560, 18, 14), W // 2, 552)

title = outlined("Stagecoach", 10, (255, 190, 40), (74, 42, 10))
sub = text("A road network for your world", 4, (255, 255, 255))
img.alpha_composite(title, ((W - title.width) // 2, 24))
img.alpha_composite(sub, ((W - sub.width) // 2, 24 + title.height + 8))

out = ROOT / "release/banner.png"
img.convert("RGB").save(out)
print("written", out)
