"""Writes the Create mechanical-crafting recipes (stagecoach:create/<wood>_stagecoach), loaded only when Create is.

Create exists for Forge and Fabric 1.20.1 and NeoForge 1.21.1 only, so only those loader folders get them. The vanilla
recipe stays: this is a second way to build a coach, without the diamond and gold blocks.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]

PATTERN = [
    "  LFFFL  ",
    "WCPPPPPPP",
    "W PPPPPPP",
    "SPPPPPPPS",
    " G     G ",
]

# loader folder -> (data folder, condition entry, mirrored key, result id key)
TARGETS = {
    "1.20.1/forge": ("recipes", {"conditions": [{"type": "forge:mod_loaded", "modid": "create"}]}, "acceptMirrored", "item"),
    "1.20.1/fabric": ("recipes", {"fabric:load_conditions": [{"condition": "fabric:all_mods_loaded", "values": ["create"]}]},
                      "acceptMirrored", "item"),
    "1.21.1/neoforge": ("recipe", {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "create"}]},
                        "accept_mirrored", "id"),
}


def recipe(wood, condition, mirrored, result_key):
    r = dict(condition)
    r.update({
        "type": "create:mechanical_crafting",
        mirrored: True,
        "pattern": PATTERN,
        "key": {
            "W": {"item": "minecraft:lead"},
            "C": {"item": "minecraft:compass"},
            "L": {"item": "minecraft:leather"},
            "F": {"item": f"minecraft:{wood}_fence"},
            "P": {"item": f"minecraft:{wood}_planks"},
            "S": {"item": "create:shaft"},
            "G": {"item": "create:cogwheel"},
        },
        "result": {result_key: f"stagecoach:{wood}_stagecoach", "count": 1},
    })
    return r


for folder, (data, condition, mirrored, result_key) in TARGETS.items():
    out = ROOT / folder / "src/main/resources/data/stagecoach" / data / "create"
    out.mkdir(parents=True, exist_ok=True)
    for wood in WOODS:
        (out / f"{wood}_stagecoach.json").write_text(json.dumps(recipe(wood, condition, mirrored, result_key), indent=2) + "\n",
                                                     encoding="utf-8")
    print(folder, len(WOODS))
