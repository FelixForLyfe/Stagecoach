#!/usr/bin/env python3
# Precomputes the coach's footprint and per-cell collision from the GeckoLib model.
# Run after a model change: python tools/generate_footprint.py [--preview build/footprint.png]
# GeckoLib 4.8.4's chain: X mirrored on load, T(pivot)*Rz*Ry*Rx*T(-pivot) from root to leaf.
# "north" frame: the model turned by BASE_YAW and lifted by Y_OFFSET; Java rotates the rest.
# Boxes snap outward to whole pixels so neighbouring cells meet exactly.

import argparse
import hashlib
import json
import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GEO = ROOT / "1.20.1/common/src/main/resources/assets/stagecoach/geo/block/stagecoach.geo.json"
FOOTPRINT_JAVA = "common/src/main/java/net/feelix/stagecoach/footprint/StagecoachFootprintData.java"
OUT = ROOT / "1.20.1" / FOOTPRINT_JAVA
COPIES = [ROOT / version / FOOTPRINT_JAVA for version in ("1.21.1", "1.21.11", "26.2", "26.3")]

# The model's front is the "Front + Drawer" bone; BASE_YAW turns it north.
FRONT_BONE = "Front + Drawer"
BASE_YAW_DEGREES = 90
EPS = 1e-6


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def translate(x, y, z):
    m = identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def vec3(a):
    return list(a) if a is not None and len(a) == 3 else [0.0, 0.0, 0.0]


def pivot_rotation(pivot, rotation):
    """GeckoLib's T(p) * Rz * Ry * Rx * T(-p) with its X-mirrored pivot and (-rx, -ry, +rz) rotation."""
    px, py, pz = -pivot[0], pivot[1], pivot[2]
    rx, ry, rz = math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])
    m = translate(px, py, pz)
    m = mat_mul(m, rot_z(rz))
    m = mat_mul(m, rot_y(ry))
    m = mat_mul(m, rot_x(rx))
    return mat_mul(m, translate(-px, -py, -pz))


def model_boxes(geo):
    bones = geo["minecraft:geometry"][0]["bones"]
    by_name = {b["name"]: b for b in bones}

    def bone_matrix(bone):
        chain = []
        while bone is not None:
            chain.append(bone)
            bone = by_name.get(bone.get("parent"))
        m = identity()
        for b in reversed(chain):  # root first
            m = mat_mul(m, pivot_rotation(vec3(b.get("pivot")), vec3(b.get("rotation"))))
        return m

    out = []
    for bone in bones:
        bm = bone_matrix(bone)
        for cube in bone.get("cubes", []):
            if "inflate" in cube or bone.get("inflate"):
                sys.exit("inflate is not handled by this generator - extend it before using this model")
            ox, oy, oz = cube["origin"]
            sx, sy, sz = cube["size"]
            x0 = -(ox + sx)
            m = mat_mul(bm, pivot_rotation(vec3(cube.get("pivot")), vec3(cube.get("rotation"))))
            corners = [apply(m, (x, y, z)) for x in (x0, x0 + sx) for y in (oy, oy + sy) for z in (oz, oz + sz)]
            lo = tuple(min(c[i] for c in corners) for i in range(3))
            hi = tuple(max(c[i] for c in corners) for i in range(3))
            out.append((bone["name"], lo, hi))
    return out


def to_north(lo, hi, y_offset):
    """Rotate a model-frame box by BASE_YAW about +Y (a quarter-turn multiple keeps it axis-aligned)."""
    m = rot_y(math.radians(BASE_YAW_DEGREES))
    corners = [apply(m, (x, y, z)) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    nlo = tuple(min(c[i] for c in corners) for i in range(3))
    nhi = tuple(max(c[i] for c in corners) for i in range(3))
    return (nlo[0], nlo[1] + y_offset, nlo[2]), (nhi[0], nhi[1] + y_offset, nhi[2])


def snap_out(lo, hi):
    """Pixel box relative to the controller block's corner (x+8, z+8), snapped outward to whole pixels."""
    def fl(v):
        return math.floor(round(v, 6))

    def ce(v):
        return math.ceil(round(v, 6))
    return (fl(lo[0] + 8), max(0, fl(lo[1])), fl(lo[2] + 8)), (ce(hi[0] + 8), ce(hi[1]), ce(hi[2] + 8))


def build_cells(px_boxes):
    cells = {}  # (cx, cy, cz) -> bytearray(16*16*16), index x + 16*(z + 16*y)
    for lo, hi in px_boxes:
        if any(hi[i] <= lo[i] for i in range(3)):
            continue
        for cy in range(lo[1] // 16, (hi[1] - 1) // 16 + 1):
            for cz in range(lo[2] // 16, (hi[2] - 1) // 16 + 1):
                for cx in range(lo[0] // 16, (hi[0] - 1) // 16 + 1):
                    grid = cells.setdefault((cx, cy, cz), bytearray(4096))
                    x1, x2 = max(lo[0] - cx * 16, 0), min(hi[0] - cx * 16, 16)
                    y1, y2 = max(lo[1] - cy * 16, 0), min(hi[1] - cy * 16, 16)
                    z1, z2 = max(lo[2] - cz * 16, 0), min(hi[2] - cz * 16, 16)
                    for y in range(y1, y2):
                        for z in range(z1, z2):
                            base = 16 * (z + 16 * y)
                            for x in range(x1, x2):
                                grid[base + x] = 1
    return {k: v for k, v in cells.items() if any(v)}


def greedy_boxes(grid):
    """Exact cover of a 16^3 voxel grid by boxes: extend in x, then z, then y."""
    def filled(x, y, z):
        return grid[x + 16 * (z + 16 * y)] == 1

    done = bytearray(4096)
    boxes = []
    for y in range(16):
        for z in range(16):
            for x in range(16):
                i = x + 16 * (z + 16 * y)
                if not grid[i] or done[i]:
                    continue
                x2 = x
                while x2 + 1 < 16 and filled(x2 + 1, y, z) and not done[x2 + 1 + 16 * (z + 16 * y)]:
                    x2 += 1

                def row_ok(zz, yy):
                    return all(filled(xx, yy, zz) and not done[xx + 16 * (zz + 16 * yy)] for xx in range(x, x2 + 1))
                z2 = z
                while z2 + 1 < 16 and row_ok(z2 + 1, y):
                    z2 += 1
                y2 = y
                while y2 + 1 < 16 and all(row_ok(zz, y2 + 1) for zz in range(z, z2 + 1)):
                    y2 += 1
                for yy in range(y, y2 + 1):
                    for zz in range(z, z2 + 1):
                        for xx in range(x, x2 + 1):
                            done[xx + 16 * (zz + 16 * yy)] = 1
                boxes.append((x, y, z, x2 + 1, y2 + 1, z2 + 1))
    return boxes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write a top + side PNG of the footprint (needs Pillow)")
    ap.add_argument("--check", action="store_true", help="print the report only, do not write Java")
    args = ap.parse_args()

    raw = GEO.read_bytes()
    geo = json.loads(raw)
    boxes = model_boxes(geo)

    min_y = min(lo[1] for _, lo, _ in boxes)
    y_offset = -min_y
    north = [(name,) + to_north(lo, hi, y_offset) for name, lo, hi in boxes]

    front = [(lo, hi) for name, lo, hi in north if name == FRONT_BONE]
    front_z = sum((lo[2] + hi[2]) / 2 for lo, hi in front) / len(front)
    if front_z >= 0:
        sys.exit(f"'{FRONT_BONE}' does not face north after BASE_YAW={BASE_YAW_DEGREES} (centre z={front_z:.2f}px)")

    px = [snap_out(lo, hi) for _, lo, hi in north]
    cells = build_cells(px)
    cells.setdefault((0, 0, 0), bytearray(4096))  # the controller always occupies its own cell
    order = sorted(cells, key=lambda c: (c != (0, 0, 0), c[1], c[2], c[0]))
    merged = {c: greedy_boxes(cells[c]) for c in order}

    lo_all = [min(b[0][i] for b in px) for i in range(3)]
    hi_all = [max(b[1][i] for b in px) for i in range(3)]
    span = [max(c[i] for c in order) - min(c[i] for c in order) + 1 for i in range(3)]
    print(f"model lowest vertex y = {min_y:.4f}px -> Y_OFFSET = {y_offset:.4f}px")
    print(f"north-frame bounds (px, from the controller's corner): x {lo_all[0]}..{hi_all[0]}  "
          f"y {lo_all[1]}..{hi_all[1]}  z {lo_all[2]}..{hi_all[2]}")
    print(f"footprint: {len(order)} cells ({len(order) - 1} parts), bounding {span[0]} x {span[1]} x {span[2]} "
          f"(W x H x L blocks), x {min(c[0] for c in order)}..{max(c[0] for c in order)} "
          f"y {min(c[1] for c in order)}..{max(c[1] for c in order)} z {min(c[2] for c in order)}..{max(c[2] for c in order)}")
    print(f"controller cell boxes: {len(merged[(0, 0, 0)])}; total boxes: {sum(len(v) for v in merged.values())}")

    if args.preview:
        write_preview(args.preview, px, order)
    if args.check:
        return

    sha1 = hashlib.sha1(raw).hexdigest()
    lines = [
        "package net.feelix.stagecoach.footprint;",
        "",
        "public final class StagecoachFootprintData {",
        "    private StagecoachFootprintData() {}",
        "",
        f"    /** SHA-1 of the geo.json this data was generated from. */",
        f"    public static final String MODEL_SHA1 = \"{sha1}\";",
        f"    /** Turns the model's front to face north (degrees about +Y). */",
        f"    public static final float BASE_YAW_DEGREES = {BASE_YAW_DEGREES}f;",
        f"    /** Pixels the model is lifted so its lowest vertex sits exactly on the controller's floor. */",
        f"    public static final float Y_OFFSET_PX = {y_offset:.6f}f;",
        "",
        "    /** NORTH-facing {dx, dy, dz} from the controller; index 0 is the controller. */",
        "    public static final int[][] CELLS = {",
    ]
    lines += [f"        {{{c[0]}, {c[1]}, {c[2]}}}," for c in order]
    lines += [
        "    };",
        "",
        "    /** Per cell, pixel boxes {x1, y1, z1, x2, y2, z2} inside that cell, NORTH-facing. */",
        "    public static final int[][][] BOXES = {",
    ]
    for c in order:
        inner = ", ".join("{" + ", ".join(str(v) for v in b) + "}" for b in merged[c])
        lines.append(f"        {{{inner}}},")
    lines += ["    };", "}", ""]
    for out in [OUT] + COPIES:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text("\n".join(lines), encoding="utf-8", newline="\n")
        print(f"wrote {out.relative_to(ROOT)}")


def write_preview(path, px, order):
    from PIL import Image, ImageDraw
    s = 4  # screen pixels per model pixel
    xs = [c[0] for c in order]
    ys = [c[1] for c in order]
    zs = [c[2] for c in order]
    x0, x1 = min(xs) * 16, (max(xs) + 1) * 16
    y0, y1 = 0, (max(ys) + 1) * 16
    z0, z1 = min(zs) * 16, (max(zs) + 1) * 16
    w_top, h_top = (x1 - x0) * s, (z1 - z0) * s
    w_side, h_side = (z1 - z0) * s, (y1 - y0) * s
    img = Image.new("RGB", (w_top + w_side + 30, max(h_top, h_side) + 20), (255, 255, 255))
    d = ImageDraw.Draw(img)
    for lo, hi in px:  # top view: x right, z down (north up)
        d.rectangle([10 + (lo[0] - x0) * s, 10 + (lo[2] - z0) * s, 10 + (hi[0] - x0) * s, 10 + (hi[2] - z0) * s],
                    fill=(180, 140, 90), outline=(90, 60, 30))
    for lo, hi in px:  # side view from the east: z right (north left), y up
        ox = w_top + 20
        d.rectangle([ox + (lo[2] - z0) * s, 10 + (y1 - hi[1]) * s, ox + (hi[2] - z0) * s, 10 + (y1 - lo[1]) * s],
                    fill=(180, 140, 90), outline=(90, 60, 30))
    for x in range(x0, x1 + 1, 16):
        d.line([10 + (x - x0) * s, 10, 10 + (x - x0) * s, 10 + h_top], fill=(0, 0, 255))
    for z in range(z0, z1 + 1, 16):
        d.line([10, 10 + (z - z0) * s, 10 + w_top, 10 + (z - z0) * s], fill=(0, 0, 255))
        d.line([w_top + 20 + (z - z0) * s, 10, w_top + 20 + (z - z0) * s, 10 + h_side], fill=(0, 0, 255))
    for y in range(y0, y1 + 1, 16):
        d.line([w_top + 20, 10 + (y1 - y) * s, w_top + 20 + w_side, 10 + (y1 - y) * s], fill=(0, 0, 255))
    cx, cz = 10 + (0 - x0) * s, 10 + (0 - z0) * s  # controller cell outline in red
    d.rectangle([cx, cz, cx + 16 * s, cz + 16 * s], outline=(255, 0, 0), width=3)
    img.save(path)


if __name__ == "__main__":
    main()
