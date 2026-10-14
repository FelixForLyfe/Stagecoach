"""Writes the structure injection files into every version folder.

Every village gets one guaranteed coach stop, in the wood its houses are built from (counted from the mods' own templates
with tools/count_village_woods.py). Taverns get theirs by chance. Pool ids were read from the mods' jars (2026-10-13):
Repurposed Structures 7.1.25-7.8.4, CTOV 3.4.14-4.1.0, Towns and Towers 1.12-1.13.12, Integrated Villages 1.3.2-1.3.3,
Dungeons and Taverns 3.0.3-6.0.2. A pool a version doesn't have is skipped at runtime, so every folder ships the same files.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDERS = ["1.20.1", "1.21.1", "1.21.11", "26.2", "26.3"]
TAVERN_WEIGHT = 6
# the standard stop first, then smaller ones for plots it doesn't fit
HOUSE = ["stagecoach:village/coach_stop_small", "stagecoach:village/coach_stop_small_roadside", "stagecoach:village/coach_stop_small_compact"]
INTEGRATED = ["stagecoach:village/coach_stop_integrated", "stagecoach:village/coach_stop_integrated_roadside",
              "stagecoach:village/coach_stop_integrated_compact"]

# (file id, required mod or None, pools, theme, piece); the first pool names the village in WOOD
POOLS = [
    (f"vanilla/{kind}", None, [f"minecraft:village/{kind}/houses", f"minecraft:village/{kind}/zombie/houses"], kind, HOUSE)
    for kind in ("plains", "desert", "savanna", "snowy", "taiga")
]

# The wood most of each village's houses are built from (tools/count_village_woods.py). Pale oak and the 26.x dappled
# villages use woods no coach comes in, so those get the nearest one.
WOOD = {
    "minecraft:village/plains/houses": "oak", "minecraft:village/desert/houses": "jungle",
    "minecraft:village/savanna/houses": "acacia", "minecraft:village/snowy/houses": "spruce",
    "minecraft:village/taiga/houses": "spruce",
    "repurposed_structures:villages/badlands/houses": "dark_oak", "repurposed_structures:villages/bamboo/houses": "bamboo",
    "repurposed_structures:villages/birch/houses": "birch", "repurposed_structures:villages/cherry/houses": "cherry",
    "repurposed_structures:villages/crimson/houses": "crimson", "repurposed_structures:villages/dappled/houses": "oak",
    "repurposed_structures:villages/dark_forest/houses": "dark_oak", "repurposed_structures:villages/giant_taiga/houses": "spruce",
    "repurposed_structures:villages/jungle/houses": "jungle", "repurposed_structures:villages/mountains/houses": "spruce",
    "repurposed_structures:villages/mushroom/houses": "birch", "repurposed_structures:villages/oak/houses": "oak",
    "repurposed_structures:villages/ocean/houses": "oak", "repurposed_structures:villages/pale_garden/houses": "birch",
    "repurposed_structures:villages/swamp/houses": "mangrove", "repurposed_structures:villages/warped/houses": "warped",
    "ctov:village/beach/house": "jungle", "ctov:village/christmas/house": "dark_oak", "ctov:village/desert/house": "spruce",
    "ctov:village/desert_oasis/house": "spruce", "ctov:village/halloween/house": "dark_oak", "ctov:village/jungle/house": "jungle",
    "ctov:village/jungle_tree/house": "jungle", "ctov:village/mesa/house": "spruce", "ctov:village/mesa_fortified/house": "spruce",
    "ctov:village/mountain/house": "birch", "ctov:village/mountain_alpine/house": "dark_oak", "ctov:village/mushroom/house": "spruce",
    "ctov:village/plains/house": "oak", "ctov:village/plains_fortified/house": "spruce", "ctov:village/savanna/house": "acacia",
    "ctov:village/savanna_na/house": "spruce", "ctov:village/snowy_igloo/house": "spruce", "ctov:village/swamp/house": "spruce",
    "ctov:village/swamp_fortified/house": "mangrove", "ctov:village/taiga/house": "spruce",
    "ctov:village/taiga_fortified/house": "spruce",
    "kaisyn:village/badlands_pueblo/houses": "spruce", "kaisyn:village/beach_lighthouse/side/house": "spruce",
    "kaisyn:village/birch_forest_romanian/houses": "birch", "kaisyn:village/exclusives/classic/houses": "oak",
    "kaisyn:village/exclusives/iberian/houses": "spruce", "kaisyn:village/exclusives/mediterranean/houses/corner": "spruce",
    "kaisyn:village/exclusives/mediterranean/houses/regular": "spruce", "kaisyn:village/exclusives/rustic/houses": "spruce",
    "kaisyn:village/exclusives/swedish/houses": "dark_oak", "kaisyn:village/exclusives/tudor/houses": "spruce",
    "kaisyn:village/flower_forest_japanese/houses": "spruce", "kaisyn:village/jungle_tribal/houses": "jungle",
    "kaisyn:village/meadow_swiss/houses": "dark_oak", "kaisyn:village/mushroom_fields_fantasy/houses": "oak",
    "kaisyn:village/old_growth_taiga_polish/houses": "dark_oak", "kaisyn:village/snowy_taiga_viking/houses": "spruce",
    "kaisyn:village/sparse_jungle_polynesian/houses": "spruce", "kaisyn:village/swamp_boat/houses": "spruce",
    "kaisyn:village/wooded_badlands_tipi/houses": "spruce", "kaisyn:village/forest_ruins/houses": "oak",
    "kaisyn:village/exclusives/piglin/houses": "oak", "kaisyn:village/exclusives/wandering_trader_camp/houses": "jungle",
    "kaisyn:village/sunflower_plains_farm/side/farms": "spruce",
    "nova_structures:birch_village/birch_house": "birch", "nova_structures:dappled_village/dappled_house": "spruce",
    "minecraft:jungle_village/jungle_house": "jungle", "minecraft:swamp_village/swamp_house": "oak",
    "nova_structures:remnant/village_house": "oak",
    "integrated_villages:cabin_village/house/farmer": "spruce", "integrated_villages:clockwork_village/house/cartographer": "spruce",
    "integrated_villages:kutcha_village/house/farmhouse": "spruce", "integrated_villages:marketstead_village/house/house1": "oak",
    "integrated_villages:mediterranean_village/house/bakery": "spruce", "integrated_villages:minka_village/house/cleric": "spruce",
    "integrated_villages:mossy_mounds/house/armorer": "spruce", "integrated_villages:oasis_village/house/house_large": "spruce",
    "integrated_villages:pirate_village/house/blacksmith": "dark_oak", "integrated_villages:swamp_village/house/house": "oak",
    "integrated_villages:tavern_village/animalhouse": "dark_oak",
}
RS = {"badlands": "badlands", "bamboo": "jungle", "birch": "plains", "cherry": "cherry",
      "crimson": "crimson", "dappled": "plains", "dark_forest": "dark_forest", "giant_taiga": "taiga",
      "jungle": "jungle", "mountains": "taiga", "mushroom": "dark_forest", "oak": "plains",
      "ocean": "beach", "pale_garden": "dark_forest", "swamp": "swamp", "warped": "warped"}
for name, theme in RS.items():
    POOLS.append((f"repurposed_structures/{name}", "repurposed_structures", [f"repurposed_structures:villages/{name}/houses"], theme, HOUSE))
CTOV = {"beach": "beach", "christmas": "snowy", "desert": "desert", "desert_oasis": "desert",
        "halloween": "dark_forest", "jungle": "jungle", "jungle_tree": "jungle", "mesa": "badlands",
        "mesa_fortified": "badlands", "mountain": "taiga", "mountain_alpine": "snowy",
        "mushroom": "dark_forest", "plains": "plains", "plains_fortified": "plains",
        "savanna": "savanna", "savanna_na": "savanna", "snowy_igloo": "snowy", "swamp": "swamp",
        "swamp_fortified": "swamp", "taiga": "taiga", "taiga_fortified": "taiga"}
for name, theme in CTOV.items():
    POOLS.append((f"ctov/{name}", "ctov", [f"ctov:village/{name}/house"], theme, HOUSE))
TT = {"badlands_pueblo/houses": "badlands", "beach_lighthouse/side/house": "beach",
      "birch_forest_romanian/houses": "plains", "exclusives/classic/houses": "plains",
      "exclusives/iberian/houses": "savanna", "exclusives/mediterranean/houses/corner": "beach",
      "exclusives/mediterranean/houses/regular": "beach", "exclusives/rustic/houses": "plains",
      "exclusives/swedish/houses": "taiga", "exclusives/tudor/houses": "plains",
      "flower_forest_japanese/houses": "cherry", "jungle_tribal/houses": "jungle",
      "meadow_swiss/houses": "taiga", "mushroom_fields_fantasy/houses": "dark_forest",
      "old_growth_taiga_polish/houses": "taiga", "snowy_taiga_viking/houses": "snowy",
      "sparse_jungle_polynesian/houses": "jungle", "swamp_boat/houses": "swamp",
      "wooded_badlands_tipi/houses": "badlands", "forest_ruins/houses": "plains",
      "exclusives/piglin/houses": "crimson", "exclusives/wandering_trader_camp/houses": "desert",
      "sunflower_plains_farm/side/farms": "plains"}
for path, theme in TT.items():
    file = path.replace("/houses", "").replace("/house", "").replace("/", "_")
    POOLS.append((f"towns_and_towers/{file}", "t_and_t", [f"kaisyn:village/{path}"], theme, HOUSE))
# Dungeons and Taverns' own villages (26.x): one house pool each, joined by its own connector
DT = {"village_birch": ("nova_structures:birch_village/birch_house", "dt_birch", "plains"),
      "village_dappled": ("nova_structures:dappled_village/dappled_house", "dt_dappled", "plains"),
      "village_jungle": ("minecraft:jungle_village/jungle_house", "dt_jungle", "jungle"),
      "village_swamp": ("minecraft:swamp_village/swamp_house", "dt_swamp", "swamp"),
      "remnant_village": ("nova_structures:remnant/village_house", "dt_remnant", "plains")}
for name, (pool, piece, theme) in DT.items():
    POOLS.append((f"dungeons_and_taverns/{name}", None, [pool], theme,
                  [f"stagecoach:village/coach_stop_{piece}{shape}" for shape in ("", "_roadside", "_compact")]))
# Integrated Villages: every house pool its streets use, in 1.20.1 and 1.21.1. The airship village is ships in the sky
# (no road), and the sunken village is the swamp village's pools.
IV = {"cabin_village": ("taiga", ["house/farmer", "house/house"]),
      "clockwork_village": ("plains", ["house/cartographer", "house/clock", "house/house1", "house/house2", "house/leatherworker"]),
      "kutcha_village": ("savanna", ["house/farmhouse", "house/farmhouse1", "house/farmhouse2", "house/house"]),
      "marketstead_village": ("plains", ["house/house1", "house/house2"]),
      "mediterranean_village": ("beach", ["house/bakery", "house/farmhouse", "house/house", "house/library", "house/windmill"]),
      "minka_village": ("cherry", ["house/cleric", "house/house", "house/manor"]),
      "mossy_mounds": ("plains", ["house/armorer", "house/farmhouse", "house/house", "house/house1", "house/house2", "house/house_large",
                                  "house/house_small", "house/house_small1", "house/house_small2", "house/tavern"]),
      "oasis_village": ("desert", ["house/house_large", "house/house_small", "house/tower"]),
      "pirate_village": ("beach", ["house/blacksmith", "house/house", "house/leatherworker", "house/observatory", "house/tavern"]),
      "swamp_village": ("swamp", ["house/house"]),
      "tavern_village": ("tavern", ["animalhouse", "blacksmith", "farmhouse", "house"])}
for village, (theme, paths) in IV.items():
    POOLS.append((f"integrated_villages/{village}", "integrated_villages", [f"integrated_villages:{village}/{p}" for p in paths], theme, INTEGRATED))
# Dungeons and Taverns tavern variant -> the wood its tavern is built from.
TAVERNS = {"acacia": "acacia", "birch": "birch", "cherry": "cherry", "dappled": "spruce", "dark_oak": "dark_oak",
           "desert": "jungle", "jungle": "jungle", "mangrove": "mangrove", "oak": "oak", "pale": "birch", "snowy": "spruce",
           "spruce": "spruce", "swamp": "oak"}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    for folder in FOLDERS:
        base = ROOT / folder / "common/src/main/resources/data/stagecoach/stagecoach/structure_injections"
        if not (ROOT / folder / "common").exists():
            continue
        for old in base.rglob("*.json") if base.exists() else []:
            old.unlink()
        for file_id, mod, pools, theme, piece in POOLS:
            write(base / f"{file_id}.json", {"requires_mods": [mod] if mod else [], "target_pools": pools, "piece": piece,
                                             "guaranteed": True, "theme": f"stagecoach:{theme}", "wood": WOOD[pools[0]],
                                             "projection": "rigid"})
        # Dungeons and Taverns is a datapack in its later versions, so it is recognised by its pool, not a mod id
        for variant, wood in TAVERNS.items():
            write(base / f"dungeons_and_taverns/{variant}.json", {"requires_mods": [], "target_pools": ["nova_structures:tavern"],
                                                                   "piece": f"stagecoach:village/coach_stop_tavern_{variant}",
                                                                   "guaranteed": False, "weight": TAVERN_WEIGHT,
                                                                   "theme": "stagecoach:tavern", "wood": wood, "projection": "rigid"})
        print(folder, len(POOLS) + len(TAVERNS), "files")


if __name__ == "__main__":
    main()
