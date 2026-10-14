#!/usr/bin/env python3
# Builds pack/: the coach pieces, the ghost, the item icons and the travel popup's road and bar glyphs.
# Run after the mod's model or textures change: python tools/generate_pack.py [client jar]
# Piece i is cube i of the mod's geo.json: a 16px cube with that cube's UVs, squeezed into place.

import io
import json
import math
import pathlib
import shutil
import sys
import zipfile

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
MOD = ROOT.parent / "1.20.1"
GEO = MOD / "common/src/main/resources/assets/stagecoach/geo/block/stagecoach.geo.json"
MOD_TEX = MOD / "common/src/main/resources/assets/stagecoach/textures/entity/stagecoach"
PACK = ROOT / "pack"
NS = PACK / "assets/stagecoach"
DEFAULT_CLIENT = pathlib.Path.home() / ".gradle/caches/fabric-loom/1.21.11/minecraft-client.jar"

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]
PACK_FORMAT = 75  # 1.21.11 (version.json resource_major in the client jar)

# the mod's ghost colours: see-through green when it fits, red when it doesn't
GHOST = {"ghost_fits": (0x73, 0xFF, 0x80, 0x73), "ghost_blocked": (0xFF, 0x4D, 0x40, 0x73)}
BLOCKED_CELL = (0xFF, 0x4D, 0x40, 0x40)

# 192x108 leaves room for the bar, countdown and button on a 270px-tall GUI
PIC_W, PIC_H = 192, 108
FRAMES = 24
FRAME_STEP = 16.0          # road pixels per frame (the plugin shows one frame per refresh)
BAR_SEGMENTS = 16          # progress-bar glyphs, 10px wide + 1px advance gap = 176px
SEED = 0x57A6EC0AC4


def cubes():
    geo = json.loads(GEO.read_text())["minecraft:geometry"][0]
    out = []
    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            out.append(cube)
    return out, geo["description"]["texture_width"], geo["description"]["texture_height"]


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, separators=(",", ":")), encoding="utf-8")


def piece_model(cube, tw, th, texture):
    faces = {}
    for name in ("north", "south", "east", "west", "up", "down"):
        f = cube["uv"].get(name)
        if not f:
            continue
        u0, v0 = f["uv"]
        du, dv = f["uv_size"]
        sx, sy = 16.0 / tw, 16.0 / th
        faces[name] = {"uv": [round(u0 * sx, 5), round(v0 * sy, 5), round((u0 + du) * sx, 5), round((v0 + dv) * sy, 5)],
                       "texture": "#t"}
    return {"textures": {"t": texture, "particle": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def item_def(model):
    return {"model": {"type": "minecraft:model", "model": model}}


# a port of the mod's RoadScene + Landscape at noon
M64 = (1 << 64) - 1


def lhash(seed, layer, cell):
    z = (seed ^ ((layer * 0x9E3779B97F4A7C15) & M64) ^ ((cell * 0xC2B2AE3D27D4EB4F) & M64)) & M64
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
    return z ^ (z >> 31)


def unit(seed, layer, cell):
    return (lhash(seed, layer, cell) >> 11) * 2.0 ** -53


def value_noise(seed, layer, t):
    i = math.floor(t)
    f = t - i
    w = (1 - math.cos(f * math.pi)) * 0.5
    v0, v1 = unit(seed, layer, i), unit(seed, layer, i + 1)
    return v0 + (v1 - v0) * w


def hill(seed, layer, x, wavelength):
    return (value_noise(seed, layer, x / wavelength) * 2.0 + value_noise(seed, layer + 100, x / (wavelength * 0.5))) / 3.0


class Canvas:
    def __init__(self, w, h, client):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 4), dtype=np.float32)
        self.client = client
        self.cache = {}

    def tex(self, name):
        if name not in self.cache:
            img = Image.open(io.BytesIO(self.client.read(f"assets/minecraft/textures/{name}.png"))).convert("RGBA")
            if img.height > img.width:
                img = img.crop((0, 0, img.width, img.width))
            self.cache[name] = np.asarray(img).astype(np.float32)
        return self.cache[name]

    def blend(self, x0, y0, rgba):
        """Alpha-blend an (h, w, 4) float block at (x0, y0), clipped."""
        h, w = rgba.shape[:2]
        x1, y1 = x0 + w, y0 + h
        cx0, cy0, cx1, cy1 = max(0, x0), max(0, y0), min(self.w, x1), min(self.h, y1)
        if cx1 <= cx0 or cy1 <= cy0:
            return
        src = rgba[cy0 - y0:cy1 - y0, cx0 - x0:cx1 - x0]
        dst = self.px[cy0:cy1, cx0:cx1]
        a = src[..., 3:4] / 255.0
        dst[..., :3] = src[..., :3] * a + dst[..., :3] * (1 - a)
        dst[..., 3:4] = np.maximum(dst[..., 3:4], src[..., 3:4])

    def tile(self, name, x, y, w, h, ox, oy, tint=(255, 255, 255, 255)):
        """Fill (x, y, w, h) with the texture tiled 1:1 from origin (ox, oy), multiplied by tint (ARGB-ish)."""
        x, y, w, h = int(math.floor(x)), int(math.floor(y)), int(round(w)), int(round(h))
        if w <= 0 or h <= 0:
            return
        t = self.tex(name)
        ys = (np.arange(y, y + h) - int(math.floor(oy))) % 16
        xs = (np.arange(x, x + w) - int(math.floor(ox))) % 16
        block = t[ys][:, xs].copy()
        block[..., :3] *= np.array(tint[:3], dtype=np.float32) / 255.0
        block[..., 3] *= tint[3] / 255.0
        self.blend(x, y, block)

    def fill(self, x0, y0, x1, y1, rgba):
        block = np.zeros((max(0, y1 - y0), max(0, x1 - x0), 4), dtype=np.float32)
        block[...] = rgba
        self.blend(x0, y0, block)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def road_frame(client, offset):
    w, h = PIC_W, PIC_H
    c = Canvas(w, h, client)
    horizon = int(h * 0.50)
    top, bottom = rgb(0x4F86D6), rgb(0xA8CCF0)
    sky_h = horizon + int(h * 0.12)
    for y in range(h):
        t = min(1.0, y / max(1, sky_h - 1))
        c.fill(0, y, w, y + 1, mix(top, bottom, t))
    # The sun, additive, right of centre as in the mod.
    size = max(16, int(h * 0.19) // 2 * 2)
    sun = np.asarray(Image.open(io.BytesIO(client.read("assets/minecraft/textures/environment/celestial/sun.png")))
                     .convert("RGBA").resize((size, size), Image.NEAREST)).astype(np.float32)
    sx, sy = int(w * 0.72) - size // 2, max(0, int(horizon - horizon * 0.75) - size // 2)
    region = c.px[sy:sy + size, sx:sx + size]
    region[..., :3] = np.minimum(255, region[..., :3] + sun[..., :3] * (sun[..., 3:4] / 255.0))

    far_base, near_base = horizon + int(h * 0.10), horizon + int(h * 0.17)
    road_top = int(h * 0.70)
    road_bottom = road_top + max(26, int(h * 0.11))
    haze = mix(rgb(0x7EA38A), bottom, 0.5)

    def hills(layer, tex, off, base, min_h, max_h, wavelength, tint):
        col = math.floor(off / 4)
        while col * 4 - off < w:
            x0 = col * 4 - off
            hgt = min_h + hill(SEED, layer, col * 4.0, wavelength) * (max_h - min_h)
            y_top = math.floor(base - hgt)
            c.tile(tex, x0, y_top, 4, road_top - y_top, x0 - (col * 4) % 16, math.floor(y_top / 16) * 16, tint)
            col += 1

    hills(1, "block/grass_block_top", offset * 0.12, far_base, h * 0.07, h * 0.20, 150, haze)
    hills(2, "block/moss_block", offset * 0.3, near_base, h * 0.03, h * 0.12, 85, rgb(0xB4BCA4))
    meadow_top = near_base - 2
    grass = mix(rgb(0x91BD59), (0, 0, 0, 255), 0.08)
    c.tile("block/grass_block_top", 0, meadow_top, w, road_top - meadow_top, -offset * 0.55, meadow_top, grass)

    toff = offset * 0.55
    ground = road_top - 4
    cell_w = 34
    first, last = math.floor((toff - 40) / cell_w), math.ceil((toff + w + 40) / cell_w)
    leaf_tint = {"oak": rgb(0x48B518), "spruce": rgb(0x619961), "birch": rgb(0x80A755)}
    for cell in range(first, last + 1):
        if not unit(SEED, 3, cell * 4) < 0.68:
            continue
        cx = cell * cell_w + int(unit(SEED, 3, cell * 4 + 1) * (cell_w // 2)) - toff
        height = 26 + int(unit(SEED, 3, cell * 4 + 3) * 20)
        g = ground + int(unit(SEED, 53, cell) * 5)
        u = unit(SEED, 3, cell * 4 + 2)
        kind = "oak" if u < 0.45 else "spruce" if u < 0.8 else "birch"

        def crown(leaves, bottom_y, widths, row_h, tint):
            y = bottom_y
            for width in widths:
                y -= row_h
                c.tile(leaves, cx - width / 2, y, width, row_h + 1, cx, bottom_y, tint)

        if kind == "oak":
            trunk = int(height * 0.42)
            c.tile("block/oak_log", cx - 2, g - trunk, 4, trunk, cx - 2, g)
            crown("block/oak_leaves", g - trunk + 3, [18, 22, 20, 12], 6, leaf_tint["oak"])
        elif kind == "spruce":
            c.tile("block/spruce_log", cx - 1.5, g - height, 3, height, cx, g)
            crown("block/spruce_leaves", g - 5, [20, 16, 13, 9, 5], (height - 5) // 5, leaf_tint["spruce"])
        else:
            trunk = int(height * 0.55)
            c.tile("block/birch_log", cx - 1.5, g - trunk, 3, trunk, cx, g)
            crown("block/birch_leaves", g - trunk + 4, [14, 18, 18, 14, 8], 6, leaf_tint["birch"])

    tile = math.floor(offset / 16)
    while tile * 16 - offset < w:
        x0 = tile * 16 - offset
        c.tile("block/dirt_path_top", x0, road_top, 16, road_bottom - road_top, x0, road_top)
        u = unit(SEED, 4, tile * 3 + 11)
        kind = 1 if u < 0.08 else 2 if u < 0.12 else 0
        if kind:
            c.tile("block/coarse_dirt" if kind == 1 else "block/gravel", x0, road_top + 2, 16,
                   road_bottom - road_top - 4, x0, road_top, (255, 255, 255, 0x70 if kind == 1 else 0x55))
        tile += 1
    c.tile("block/grass_block_top", 0, road_bottom, w, h - road_bottom, -offset, road_bottom,
           mix(rgb(0x91BD59), (0, 0, 0, 255), 0.15))
    post_w = 26
    cell = math.floor((offset - 20) / post_w)
    while cell * post_w - offset < w + 20:
        u = unit(SEED, 4, cell)
        kind = 2 if u < 0.06 else 1 if u < 0.36 else 0
        if kind:
            x = cell * post_w + int(unit(SEED, 4, cell + 7_777_777) * (post_w - 6)) - offset
            if kind == 1:
                c.tile("block/oak_planks", x, road_top - 11, 3, 12, x, road_top)
                c.tile("block/oak_planks", x - 5, road_top - 8, 13, 2, x, road_top, (0xD8, 0xC8, 0xB0, 255))
            else:
                c.tile("block/oak_log", x, road_top - 19, 3, 20, x, road_top)
                c.tile("block/oak_planks", x - 6, road_top - 18, 15, 7, x - 6, road_top - 18)
        cell += 1
    c.fill(0, road_top, w, road_top + 1, (0, 0, 0, 0x50))
    c.fill(0, road_bottom - 1, w, road_bottom, (0, 0, 0, 0x40))
    c.fill(0, 0, w, 1, (0x3B, 0x26, 0x14, 255)); c.fill(0, h - 1, w, h, (0x3B, 0x26, 0x14, 255))
    c.fill(0, 0, 1, h, (0x3B, 0x26, 0x14, 255)); c.fill(w - 1, 0, w, h, (0x3B, 0x26, 0x14, 255))
    out = c.px.clip(0, 255).astype(np.uint8)
    out[..., 3] = 255
    return Image.fromarray(out, "RGBA")


def bar_glyphs(client):
    """Two 10x8 progress-bar segments: empty (dark oak bark) and full (hay), as the mod's bark bar filled with hay."""
    def crop(name, dark):
        img = Image.open(io.BytesIO(client.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")
        a = np.asarray(img.crop((3, 4, 13, 12))).astype(np.float32)
        a[..., :3] *= dark
        a[0, :, :3] = a[-1, :, :3] = a[:, 0, :3] = a[:, -1, :3] = (0x2A, 0x1A, 0x0E)
        return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")
    sheet = Image.new("RGBA", (20, 8))
    sheet.paste(crop("dark_oak_log", 0.55), (0, 0))
    sheet.paste(crop("hay_block_side", 1.0), (10, 0))
    return sheet


def main():
    client_jar = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CLIENT
    client = zipfile.ZipFile(client_jar)
    if PACK.exists():
        shutil.rmtree(PACK)
    all_cubes, tw, th = cubes()

    write_json(PACK / "pack.mcmeta", {"pack": {
        "description": "Stagecoach - the coaches, the icons and the road",
        "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}})

    for wood in WOODS:
        (NS / "textures/block/coach").mkdir(parents=True, exist_ok=True)
        shutil.copy(MOD_TEX / f"{wood}.png", NS / f"textures/block/coach/{wood}.png")
    oak = np.asarray(Image.open(MOD_TEX / "oak.png").convert("RGBA")).astype(np.float32)
    for name, (r, g, b, a) in GHOST.items():
        t = oak.copy()
        lum = t[..., :3].mean(axis=2, keepdims=True) / 255.0
        t[..., :3] = np.array([r, g, b], dtype=np.float32) * (0.55 + 0.45 * lum)
        t[..., 3] = np.where(oak[..., 3] > 8, a, 0)
        Image.fromarray(t.clip(0, 255).astype(np.uint8), "RGBA").save(NS / f"textures/block/coach/{name}.png")
    cell = Image.new("RGBA", (16, 16), BLOCKED_CELL)
    cell.save(NS / "textures/block/coach/blocked_cell.png")

    for skin in WOODS + list(GHOST):
        for i, cube in enumerate(all_cubes):
            write_json(NS / f"models/coach/{skin}/{i}.json", piece_model(cube, tw, th, f"stagecoach:block/coach/{skin}"))
            write_json(NS / f"items/coach/{skin}/{i}.json", item_def(f"stagecoach:coach/{skin}/{i}"))
    write_json(NS / "models/coach/blocked_cell.json", {
        "textures": {"t": "stagecoach:block/coach/blocked_cell", "particle": "stagecoach:block/coach/blocked_cell"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                      "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#t"} for f in
                                ("north", "south", "east", "west", "up", "down")}}]})
    write_json(NS / "items/coach/blocked_cell.json", item_def("stagecoach:coach/blocked_cell"))

    sys.path.insert(0, str(ROOT.parent / "tools"))
    from render_icon import render  # noqa: E402  (the mod's own renderer)
    (NS / "textures/item").mkdir(parents=True, exist_ok=True)
    for wood in WOODS:
        img = render(wood, 35, 28, res=512)
        side = max(img.size)
        sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        sq.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
        sq.resize((64, 64), Image.LANCZOS).save(NS / f"textures/item/{wood}_stagecoach.png")
        write_json(NS / f"models/item/{wood}_stagecoach.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"stagecoach:item/{wood}_stagecoach"}})
        write_json(NS / f"items/{wood}_stagecoach.json", item_def(f"stagecoach:item/{wood}_stagecoach"))

    (NS / "textures/font").mkdir(parents=True, exist_ok=True)
    cols = 6
    rows = math.ceil(FRAMES / cols)
    sheet = Image.new("RGBA", (cols * PIC_W, rows * PIC_H), (0, 0, 0, 0))
    chars = []
    for f in range(FRAMES):
        sheet.paste(road_frame(client, f * FRAME_STEP), ((f % cols) * PIC_W, (f // cols) * PIC_H))
    for r in range(rows):
        chars.append("".join(chr(0xE100 + r * cols + k) if r * cols + k < FRAMES else "\u0000" for k in range(cols)))
    sheet.save(NS / "textures/font/road.png")
    bar_glyphs(client).save(NS / "textures/font/bar.png")
    write_json(NS / "font/ui.json", {"providers": [
        {"type": "bitmap", "file": "stagecoach:font/road.png", "height": PIC_H, "ascent": 7, "chars": chars},
        {"type": "bitmap", "file": "stagecoach:font/bar.png", "height": 8, "ascent": 7, "chars": [""]},
    ]})

    logo = ROOT / "docs/release/logo-400.png"
    if logo.exists():
        Image.open(logo).resize((128, 128), Image.LANCZOS).save(PACK / "pack.png")

    files = sum(1 for p in PACK.rglob("*") if p.is_file())
    print(f"pack/: {files} files, {len(all_cubes)} pieces x {len(WOODS) + len(GHOST)} skins, {FRAMES} road frames")


if __name__ == "__main__":
    main()
