# Renders the mod icon straight from the model: a small orthographic z-buffer, no game needed.
# Usage: python tools/render_icon.py [wood] [yaw] [pitch]   (defaults: oak 35 28)
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "1.20.1/common/src/main/resources/assets/stagecoach"
geo = json.loads((ASSETS / "geo/block/stagecoach.geo.json").read_text())["minecraft:geometry"][0]
TW, TH = geo["description"]["texture_width"], geo["description"]["texture_height"]


def rot(deg_xyz):
    rx, ry, rz = (math.radians(a) for a in deg_xyz)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def transform(points, pivot, rotation):
    """GeckoLib: mirror x of the pivot, negate x/y rotation, rotate about the pivot."""
    if not rotation or not any(rotation):
        return points
    p = np.array([-pivot[0], pivot[1], pivot[2]], dtype=float)
    m = rot((-rotation[0], -rotation[1], rotation[2]))
    return (points - p) @ m.T + p


# corner index = xi*4 + yi*2 + zi; faces list them top-left, top-right, bottom-right, bottom-left
FACES = {
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0), 0.80),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1), 0.80),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1), 0.62),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0), 0.62),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1), 1.00),
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0), 0.50),
}

def render(wood="oak", yaw_deg=35.0, pitch_deg=28.0, res=1400):
    """The coach in one wood, orthographic, cropped to its pixels (transparent background)."""
    RES = res
    tex = np.asarray(Image.open(ASSETS / f"textures/entity/stagecoach/{wood}.png").convert("RGBA")).astype(np.float32)
    SX, SY = tex.shape[1] / TW, tex.shape[0] / TH
    bones = {b["name"]: b for b in geo["bones"]}
    quads = []  # (4x3 world corners, (u0, v0, u1, v1), shade)
    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            o, s = cube["origin"], cube["size"]
            lo = np.array([-(o[0] + s[0]), o[1], o[2]], dtype=float)
            hi = lo + np.array(s, dtype=float)
            for name, (*corners, shade) in FACES.items():
                face = cube["uv"].get(name)
                if not face:
                    continue
                pts = np.array([[(lo, hi)[c[0]][0], (lo, hi)[c[1]][1], (lo, hi)[c[2]][2]] for c in corners])
                pts = transform(pts, cube.get("pivot", [0, 0, 0]), cube.get("rotation"))
                b = bone
                while b is not None:
                    pts = transform(pts, b.get("pivot", [0, 0, 0]), b.get("rotation"))
                    b = bones.get(b.get("parent"))
                u0, v0 = face["uv"]
                du, dv = face["uv_size"]
                quads.append((pts, (u0, v0, u0 + du, v0 + dv), shade))

    # View: yaw about y, then pitch about x; orthographic.
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    view = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    proj = [(q @ view.T, uv, sh) for q, uv, sh in quads]
    allp = np.concatenate([p for p, _, _ in proj])
    mn, mx = allp.min(0), allp.max(0)
    scale = (RES * 0.92) / max(mx[0] - mn[0], mx[1] - mn[1])
    cx, cyy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2

    color = np.zeros((RES, RES, 4), dtype=np.float32)
    depth = np.full((RES, RES), -np.inf, dtype=np.float32)
    for pts, (u0, v0, u1, v1), shade in proj:
        sx_ = (pts[:, 0] - cx) * scale + RES / 2
        sy_ = -(pts[:, 1] - cyy) * scale + RES / 2
        z = pts[:, 2]
        tl, tr, bl = np.array([sx_[0], sy_[0]]), np.array([sx_[1], sy_[1]]), np.array([sx_[3], sy_[3]])
        e1, e2 = tr - tl, bl - tl
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue  # edge-on
        x0, x1 = int(max(0, math.floor(sx_.min()))), int(min(RES - 1, math.ceil(sx_.max())))
        y0, y1 = int(max(0, math.floor(sy_.min()))), int(min(RES - 1, math.ceil(sy_.max())))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        dx, dy = gx - tl[0], gy - tl[1]
        a = (dx * e2[1] - dy * e2[0]) / det
        b = (e1[0] * dy - e1[1] * dx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        zz = z[0] + a * (z[1] - z[0]) + b * (z[3] - z[0])
        u = u0 + a * (u1 - u0)
        v = v0 + b * (v1 - v0)
        ti = np.clip((u * SX).astype(int), 0, tex.shape[1] - 1)
        tj = np.clip((v * SY).astype(int), 0, tex.shape[0] - 1)
        texel = tex[tj, ti]
        win = inside & (texel[..., 3] > 8) & (zz > depth[y0:y1 + 1, x0:x1 + 1])
        depth[y0:y1 + 1, x0:x1 + 1][win] = zz[win]
        lit = texel.copy()
        lit[..., :3] *= shade
        lit[..., 3] = 255
        color[y0:y1 + 1, x0:x1 + 1][win] = lit[win]

    img = Image.fromarray(color.clip(0, 255).astype(np.uint8), "RGBA")
    img = Image.fromarray(color.clip(0, 255).astype(np.uint8), "RGBA")
    return img.crop(img.getbbox())


if __name__ == "__main__":
    img = render(sys.argv[1] if len(sys.argv) > 1 else "oak",
                 float(sys.argv[2]) if len(sys.argv) > 2 else 35,
                 float(sys.argv[3]) if len(sys.argv) > 3 else 28)
    side = max(img.size)
    square = Image.new("RGBA", (int(side * 1.08), int(side * 1.08)), (0, 0, 0, 0))
    square.paste(img, ((square.width - img.width) // 2, (square.height - img.height) // 2))
    out = ROOT / "release"
    out.mkdir(parents=True, exist_ok=True)
    square.resize((1024, 1024), Image.LANCZOS).save(out / "logo-1024.png")
    square.resize((400, 400), Image.LANCZOS).save(out / "icon-400.png")
    square.resize((128, 128), Image.LANCZOS).save(ASSETS / "icon.png")
    print("icon.png 128x128, release/icon-400.png and logo-1024.png written")
