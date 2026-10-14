# Writes the GameTest structures as plain .nbt files, which Fabric and Forge both load.
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

ROOT = Path(__file__).resolve().parent.parent / "1.20.1"
OUT = ROOT / "common/src/gametest/resources/data/stagecoach/structures/gametest"
DATA_VERSION = 3465  # Minecraft 1.20.1


def structure(size, block_at):
    """block_at(x, y, z) -> a block id; every position is written, so placing it always overwrites."""
    palette, index, blocks = [], {}, []
    sx, sy, sz = size
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                name = block_at(x, y, z)
                if name not in index:
                    index[name] = len(palette)
                    palette.append(Compound({"Name": String(name)}))
                blocks.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[name])}))
    return nbtlib.File(Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(v) for v in size]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](blocks),
        "entities": List[Compound]([]),
    }), gzipped=True)


OUT.mkdir(parents=True, exist_ok=True)
structure((8, 8, 8), lambda x, y, z: "minecraft:air").save(OUT / "empty.nbt")
structure((24, 6, 24), lambda x, y, z: "minecraft:stone" if y == 0 else "minecraft:air").save(OUT / "road.nbt")
print("written", sorted(p.name for p in OUT.iterdir()))
