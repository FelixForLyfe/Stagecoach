"""Writes the placeholder coach-stop jigsaw pieces (stagecoach:village/coach_stop_*) for every version folder.

Three shapes, tried in this order: the coach facing the street (7 x 8), parked alongside it (8 x 5), and for the
tightest plots the coach alone beside the road with its sign and lantern (6 x 4). Each is a dirt-path pad with one
jigsaw at the street edge. Jigsaws match by name before any processor runs, so each connector needs its own copy:
vanilla's building_entrance (vanilla, Repurposed Structures, CTOV, Towns and Towers), Integrated Villages' house, each
Dungeons and Taverns village's house, and a Dungeons and Taverns event slot per tavern wood. The coach's block entity
carries no id: it registers as a new stop when its chunk generates, and the injection's processor adds the theme.
Hand-built pieces can replace these: save one with a structure block around a placed coach, with the same jigsaw.
"""
import re
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, Long, String

ROOT = Path(__file__).resolve().parent.parent
FOOTPRINT = ROOT / "1.20.1/common/src/main/java/net/feelix/stagecoach/footprint/StagecoachFootprintData.java"
DATA_VERSION = 3465  # 1.20.1; newer versions upgrade it on load
# folder -> the structures folder name ("structures" before 1.21)
FOLDERS = {"1.20.1": "structures", "1.21.1": "structure", "1.21.11": "structure", "26.2": "structure", "26.3": "structure"}

# shape -> size, controller, coach facing, jigsaw, hitching posts, lantern post, sign
SHAPES = {
    "": ((7, 5, 8), (3, 1, 4), "north", (3, 1, 0), [(1, 1, 2), (5, 1, 2)], (5, 1, 2), (5, 1, 0)),
    "_roadside": ((8, 5, 5), (3, 1, 2), "east", (4, 1, 0), [(7, 1, 2)], (0, 1, 0), (7, 1, 0)),
    "_compact": ((6, 5, 4), (2, 1, 2), "east", (3, 1, 0), [], (5, 1, 0), (0, 1, 0)),
}


def cells():
    src = FOOTPRINT.read_text(encoding="utf-8")
    block = src[src.index("CELLS = {"):src.index("};", src.index("CELLS = {"))]
    return [tuple(int(v) for v in m) for m in re.findall(r"\{(-?\d+), (-?\d+), (-?\d+)\}", block)]


TAVERN_WOODS = ["acacia", "birch", "cherry", "dappled", "dark_oak", "desert", "jungle", "mangrove", "oak", "pale", "snowy", "spruce", "swamp"]
# Dungeons and Taverns' villages join houses to paths by their own connector names (26.x)
DT_HOUSES = {"dt_birch": "nova_structures:birch_house", "dt_dappled": "nova_structures:dappled_house",
             "dt_jungle": "minecraft:jungle_house", "dt_swamp": "minecraft:swamp_house",
             "dt_remnant": "nova_structures:remnant_village_house"}
# file name -> (jigsaw name, jigsaw target, joint, shape)
VARIANTS = {}
for _shape in SHAPES:
    VARIANTS[f"coach_stop_small{_shape}"] = ("minecraft:building_entrance", "minecraft:building_entrance", "aligned", _shape)
    VARIANTS[f"coach_stop_integrated{_shape}"] = ("integrated_villages:house", "integrated_villages:house", "aligned", _shape)
    for _key, _name in DT_HOUSES.items():
        VARIANTS[f"coach_stop_{_key}{_shape}"] = (_name, "minecraft:empty", "aligned", _shape)
for _wood in TAVERN_WOODS:
    VARIANTS[f"coach_stop_tavern_{_wood}"] = (f"nova_structures:tavern_event_{_wood}", f"nova_structures:tavern_middle_{_wood}",
                                              "rollable", "")


def turned(dx, dz, facing):
    # the footprint is laid out facing north; each quarter turn clockwise maps (x, z) to (-z, x)
    for _ in range(["north", "east", "south", "west"].index(facing)):
        dx, dz = -dz, dx
    return dx, dz


def build(jigsaw_name, jigsaw_target, joint, shape):
    size, controller, facing, jigsaw, posts, lantern, sign = SHAPES[shape]
    palette, index, blocks = [], {}, []

    def state(name, props=None):
        key = (name, tuple(sorted((props or {}).items())))
        if key not in index:
            entry = {"Name": String(name)}
            if props:
                entry["Properties"] = Compound({k: String(v) for k, v in props.items()})
            index[key] = len(palette)
            palette.append(Compound(entry))
        return index[key]

    placed = {}
    cx, cy, cz = controller
    for i, (dx, dy, dz) in enumerate(cells()):
        tx, tz = turned(dx, dz, facing)
        pos = (cx + tx, cy + dy, cz + tz)
        assert all(0 <= v < limit for v, limit in zip(pos, size)), f"{shape}: cell {i} at {pos} is outside the piece"
        if i == 0:
            placed[pos] = (state("stagecoach:oak_stagecoach", {"facing": facing}),
                           Compound({"id": String("stagecoach:stagecoach"), "CoachId": Long(-1)}))
        else:
            placed[pos] = (state("stagecoach:stagecoach_part", {"facing": facing, "part": str(i), "wood": "oak"}), None)
    placed[jigsaw] = (state("minecraft:jigsaw", {"orientation": "north_up"}), Compound({
        "id": String("minecraft:jigsaw"), "name": String(jigsaw_name), "target": String(jigsaw_target),
        "pool": String("minecraft:empty"), "joint": String(joint), "final_state": String("minecraft:air")}))
    for post in posts:
        placed[post] = (state("minecraft:oak_fence"), None)
    lx, ly, lz = lantern
    placed[lantern] = (state("minecraft:oak_fence"), None)
    placed[(lx, ly + 1, lz)] = (state("minecraft:lantern", {"hanging": "false", "waterlogged": "false"}), None)
    placed[sign] = (state("minecraft:oak_sign", {"rotation": "8", "waterlogged": "false"}), Compound({"id": String("minecraft:sign")}))

    sx, sy, sz = size
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if (x, y, z) in placed:
                    s, nbt = placed[(x, y, z)]
                elif y == 0:
                    edge = x in (0, sx - 1) or z == sz - 1
                    s, nbt = state("minecraft:coarse_dirt" if edge else "minecraft:dirt_path"), None
                else:
                    s, nbt = state("minecraft:air"), None
                entry = {"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(s)}
                if nbt is not None:
                    entry["nbt"] = nbt
                blocks.append(Compound(entry))
    return nbtlib.File(Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(v) for v in size]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](blocks),
        "entities": List[Compound]([]),
    }), gzipped=True)


def main():
    for name, connector in VARIANTS.items():
        piece = build(*connector)
        for folder, structures in FOLDERS.items():
            if not (ROOT / folder / "common").exists():
                continue
            out = ROOT / folder / f"common/src/main/resources/data/stagecoach/{structures}/village/{name}.nbt"
            out.parent.mkdir(parents=True, exist_ok=True)
            piece.save(out)
    print("wrote", len(VARIANTS), "pieces into", len(FOLDERS), "folders")


if __name__ == "__main__":
    main()
