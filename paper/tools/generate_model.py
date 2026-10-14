#!/usr/bin/env python3
# Turns the mod's model into display matrices, one per cube, plus the footprint's barrier cells.
# Run after the mod's model or footprint changes: python tools/generate_model.py
# Reuses the mod's generate_footprint.py transforms, so display and collision never disagree.
# each matrix: S(1/16) * T(0, Y_OFFSET, 0) * Ry(BASE_YAW) * M_cube * T(x0, oy, oz) * S(sx, sy, sz)

import hashlib
import importlib.util
import json
import math
import pathlib
import re
import sys

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parent.parent
MOD = ROOT.parent / "1.20.1"
GEO = MOD / "common/src/main/resources/assets/stagecoach/geo/block/stagecoach.geo.json"
FOOTPRINT = MOD / "common/src/main/java/net/feelix/stagecoach/footprint/StagecoachFootprintData.java"
OUT = ROOT / "src/main/java/net/feelix/stagecoach/bukkit/model/CoachModelData.java"

# below this share of model a cell stays air, so a sliver of wheel rim isn't an invisible wall
BARRIER_FILL = 0.04

spec = importlib.util.spec_from_file_location("generate_footprint", ROOT.parent / "tools/generate_footprint.py")
gf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gf)


def footprint_constants():
    src = FOOTPRINT.read_text(encoding="utf-8")
    sha = re.search(r'MODEL_SHA1 = "([0-9a-f]+)"', src).group(1)
    yaw = float(re.search(r"BASE_YAW_DEGREES = ([-0-9.]+)f", src).group(1))
    lift = float(re.search(r"Y_OFFSET_PX = ([-0-9.]+)f", src).group(1))
    cells_src = src[src.index("CELLS = {"):src.index("};", src.index("CELLS = {"))]
    cells = [tuple(int(v) for v in m) for m in re.findall(r"\{(-?\d+), (-?\d+), (-?\d+)\}", cells_src)]
    boxes_src = src[src.index("BOXES = {"):]
    per_cell = []
    for block in re.findall(r"\{((?:\s*\{[-0-9, ]+\},?)+)\s*\}", boxes_src):
        vols = 0
        for b in re.findall(r"\{(\d+), (\d+), (\d+), (\d+), (\d+), (\d+)\}", block):
            x1, y1, z1, x2, y2, z2 = map(int, b)
            vols += (x2 - x1) * (y2 - y1) * (z2 - z1)
        per_cell.append(vols / 4096.0)
    if len(per_cell) != len(cells):
        sys.exit(f"parsed {len(per_cell)} box lists for {len(cells)} cells - the data file format changed")
    return sha, yaw, lift, cells, per_cell


def main():
    sha = hashlib.sha1(GEO.read_bytes()).hexdigest()
    fp_sha, base_yaw, lift, cells, fills = footprint_constants()
    if sha != fp_sha:
        sys.exit(f"the mod's footprint was generated from another model ({fp_sha}, model is {sha}) - "
                 f"run the mod's tools/generate_footprint.py first")

    geo = json.loads(GEO.read_text())
    g = geo["minecraft:geometry"][0]
    bones = g["bones"]
    by_name = {b["name"]: b for b in bones}

    def bone_matrix(bone):
        chain = []
        while bone is not None:
            chain.append(bone)
            bone = by_name.get(bone.get("parent"))
        m = gf.identity()
        for b in reversed(chain):
            m = gf.mat_mul(m, gf.pivot_rotation(gf.vec3(b.get("pivot")), gf.vec3(b.get("rotation"))))
        return m

    frame = gf.mat_mul(gf.translate(0, lift, 0), gf.rot_y(math.radians(base_yaw)))
    frame = gf.mat_mul([[1 / 16, 0, 0, 0], [0, 1 / 16, 0, 0], [0, 0, 1 / 16, 0], [0, 0, 0, 1]], frame)

    rows = []
    lo_all = [math.inf] * 3
    hi_all = [-math.inf] * 3
    for bone in bones:
        bm = bone_matrix(bone)
        for cube in bone.get("cubes", []):
            ox, oy, oz = cube["origin"]
            sx, sy, sz = cube["size"]
            # A flat plane (size 0 on one axis) is drawn as a sliver so the display is never singular.
            sx, sy, sz = (max(v, 0.05) for v in (sx, sy, sz))
            x0 = -(ox + cube["size"][0])
            m = gf.mat_mul(bm, gf.pivot_rotation(gf.vec3(cube.get("pivot")), gf.vec3(cube.get("rotation"))))
            m = gf.mat_mul(m, gf.translate(x0, oy, oz))
            m = gf.mat_mul(m, [[sx, 0, 0, 0], [0, sy, 0, 0], [0, 0, sz, 0], [0, 0, 0, 1]])
            m = gf.mat_mul(frame, m)
            det = np.linalg.det(np.array(m)[:3, :3])
            if det <= 0:
                sys.exit(f"cube in '{bone['name']}' has a mirrored transform (det {det}) - a display can't draw that")
            for x in (0, 1):
                for y in (0, 1):
                    for z in (0, 1):
                        p = gf.apply(m, (x, y, z))
                        for i in range(3):
                            lo_all[i] = min(lo_all[i], p[i])
                            hi_all[i] = max(hi_all[i], p[i])
            rows.append((bone["name"], [m[r][c] for r in range(3) for c in range(4)]))

    barrier = [f >= BARRIER_FILL for f in fills]
    lines = []
    for i, (bone, vals) in enumerate(rows):
        nums = ", ".join(f"{v:.6f}f" for v in vals)
        lines.append(f"        new Cube(new float[] {{{nums}}}),")
    cell_lines = []
    for (dx, dy, dz), fill, bar in zip(cells, fills, barrier):
        cell_lines.append(f"        {{{dx}, {dy}, {dz}, {1 if bar else 0}}},")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(f"""package net.feelix.stagecoach.bukkit.model;

public final class CoachModelData {{
    private CoachModelData() {{}}

    /** SHA-1 of the mod's geo.json this data was generated from. */
    public static final String MODEL_SHA1 = "{sha}";

    /** Row-major 3x4 matrix taking the unit cube to cube i in the NORTH frame (blocks). */
    public record Cube(float[] m) {{}}

    public static final Cube[] CUBES = {{
{chr(10).join(lines)}
    }};

    /** {{dx, dy, dz, barrier}}; barrier = 1 where at least {BARRIER_FILL * 100:.0f}% of the cell is model. */
    public static final int[][] CELLS = {{
{chr(10).join(cell_lines)}
    }};

    /** Model bounds in the NORTH frame (blocks): min x, y, z, max x, y, z. */
    public static final float[] BOUNDS = {{{", ".join(f"{v:.4f}f" for v in lo_all + hi_all)}}};
}}
""", encoding="utf-8", newline="\n")
    print(f"{len(rows)} cubes, {sum(barrier)}/{len(cells)} barrier cells, bounds {lo_all} .. {hi_all}")
    for (dx, dy, dz), fill in zip(cells, fills):
        if fill < BARRIER_FILL:
            print(f"  air cell {dx},{dy},{dz}: {fill * 100:.2f}%")


if __name__ == "__main__":
    main()
