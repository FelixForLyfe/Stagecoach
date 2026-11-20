---
title: Recipes and tags
parent: Pack developer guide
nav_order: 4
---

# Recipes and tags

## Recipes

Each wood has one shaped recipe, `stagecoach:<wood>_stagecoach`, for the woods `oak`, `spruce`, `birch`, `jungle`,
`acacia`, `dark_oak`, `mangrove`, `cherry`, `bamboo`, `crimson` and `warped`:

```
P C P      P  the coach's own planks     L  lead
L D L      C  chest                      D  block of diamond
D G D      G  block of gold
```

The recipe book unlocks them the first time a player holds a block of diamond (advancements
`stagecoach:recipes/transportation/<wood>_stagecoach`).

With TerraFirmaCraft (Forge 1.20.1 and NeoForge 1.21.1) and `tfcRecipes` on, nine woods also get
`stagecoach:tfc/<wood>_stagecoach`: TFC lumber, a wooden chest, a lead, steel double sheets and a gold sheet.

With Create (Forge and Fabric 1.20.1, NeoForge 1.21.1) every wood also gets `stagecoach:create/<wood>_stagecoach`, a
`create:mechanical_crafting` recipe on a 9×5 grid:

```
  L F F F L        W  lead          C  compass      L  leather
W C P P P P P P P  F  the wood's fence              P  the wood's planks
W   P P P P P P P  S  Create shaft  G  Create cogwheel
S P P P P P P P S
  G           G
```

It only loads when Create is installed. Override or remove it like any other recipe.

### Changing a recipe

Put a file with the same id in a datapack. The recipe format follows the Minecraft version:

**1.20.1** (`data/stagecoach/recipes/oak_stagecoach.json`):

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["PCP", "LIL", "PPP"],
  "key": {
    "P": {"item": "minecraft:oak_planks"},
    "C": {"item": "minecraft:chest"},
    "L": {"item": "minecraft:lead"},
    "I": {"item": "minecraft:iron_block"}
  },
  "result": {"item": "stagecoach:oak_stagecoach"}
}
```

**1.21.1** (`data/stagecoach/recipe/oak_stagecoach.json`): the same, with `"result": {"id": "stagecoach:oak_stagecoach",
"count": 1}`.

**1.21.11, 26.2 and 26.3** (`data/stagecoach/recipe/oak_stagecoach.json`): the keys are plain ids,
`"P": "minecraft:oak_planks"`, and the result is `{"id": "stagecoach:oak_stagecoach", "count": 1}`.

To remove a recipe, use your loader's or KubeJS's recipe removal, e.g. in a KubeJS server script:

```js
ServerEvents.recipes(event => {
  event.remove({ id: 'stagecoach:crimson_stagecoach' })
})
```

## Tags

| Tag | Type | Holds |
|---|---|---|
| `stagecoach:stagecoaches` | block | The eleven coaches and the invisible part block |
| `stagecoach:stagecoaches` | item | The eleven coach items |
| `stagecoach:placement_blocked_on` | block | Blocks a coach can't stand on (empty by default) |
| `stagecoach:road_blocks` | block | What counts as road for `requireRoad`: dirt paths, gravel, cobblestone (and mossy), stone bricks (mossy, cracked), cobbled deepslate, deepslate bricks, smooth and cut sandstone, packed mud, mud bricks, bricks, `#minecraft:planks`, `#minecraft:wooden_slabs` |

`placement_blocked_on` is checked against the block under each cell of the coach's bottom layer. To keep coaches off
ice and leaves, for example, add `placement_blocked_on.json` to a datapack (1.20.1: in `data/stagecoach/tags/blocks/`,
1.21 and later: in `data/stagecoach/tags/block/`):

```json
{
  "replace": false,
  "values": ["minecraft:ice", "minecraft:packed_ice", "#minecraft:leaves"]
}
```

`road_blocks` works the same way: add your mod's paths to it (or replace it with `"replace": true`) to change what
`requireRoad` accepts. A road may start anywhere within 8 blocks of a coach, step diagonally and climb or drop one block
at a time.
