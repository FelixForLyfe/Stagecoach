---
title: Structures and villages
parent: Pack developer guide
nav_order: 5
---

# Structures and villages

A stagecoach can be part of any structure: your own towns, another mod's villages, or anything you place with
`/place` or a structure block. Wherever a coach turns up without having been placed by a player, it becomes a stop on
the next server tick, with its parts laid around it, whole, however the structure was rotated or mirrored.

## A coach in your own structure

1. Place a coach where it should stand (any wood; the theme can change it later).
2. Optionally give it template keys (below), for example:
   ```
   /data merge block <x> <y> <z> {"stagecoach:template_name": "Riverford", "stagecoach:template_flags": ["public"]}
   ```
   `<x> <y> <z>` is the coach's controller, the cell you placed it at (`/data get block` there shows `CoachId`).
3. Save the structure with a structure block around the whole coach (3 wide, 3 tall, 6 long) and whatever goes with it.

Every place the template generates gets its own stop. The coach belongs to the server, never drops a coach item when
taken down, and is named from its keys (or its theme's name pool, or `autoNameFormat`). An automatic or template name
that is already taken gets " (2)", " (3)" and so on; names players type are never changed.

### Template keys

| Key | Type | Effect |
|---|---|---|
| `stagecoach:template_name` | string | The stop's name |
| `stagecoach:template_flags` | list of strings | Any of `public`, `hidden`, `locked`, `free` ([what they do](commands.md#flags)) |
| `stagecoach:template_cost` | number | The stop's cost multiplier |
| `stagecoach:template_scene` | id | The stop's [travel scene](theming.md) |
| `stagecoach:template_theme` | id | A [theme](#themes): the coach's wood and its name pool. A themed coach counts as generated: it is locked unless `generatedCoachesBreakable` is on, and only `maxGeneratedStopsPerStructure` of them become stops in one structure |
| `stagecoach:template_wood` | string | The coach's wood, over the theme's |

The keys are read once, when the coach becomes a stop, and then removed. A coach that is already a stop somewhere else
(say, copied with a structure block) becomes a new, separate stop.

`/setblock` and `/fill` make a controller too: it becomes an ownerless stop with its parts straight away.

## Coach stops in villages

With `naturalGeneration` on (the default), every village gets exactly one coach stop. Stagecoach doesn't change any
template pool: while a village is being assembled, it offers its coach-stop pieces to each house slot of that
village, first in line, until the village has its stop. The stop is built in the wood the village's houses are made
of, and named from a name pool that suits the village ("Frostholm Crossing" in the snow, "Ochrereach Stop" in the
desert).

Out of the box this covers the vanilla villages (and zombie villages), Repurposed Structures, ChoiceTheorem's
Overhauled Village, Towns and Towers, Integrated Villages and Dungeons and Taverns' villages. Dungeons and Taverns'
taverns get a stop by chance instead.

The pieces come in three shapes, tried in order: the coach facing the street (7 × 8), parked alongside it (8 × 5), and
the coach alone beside the road (6 × 4) for the tightest plots.

## Adding a structure: injection files

Each structure gets its stops from an injection file in a datapack:
`data/<namespace>/stagecoach/structure_injections/<name>.json`. A file with the same name as one of Stagecoach's
replaces it (Stagecoach's own are under the `stagecoach` namespace, e.g.
`data/stagecoach/stagecoach/structure_injections/vanilla/plains.json`).

```json
{
  "requires_mods": ["mytowns"],
  "target_pools": ["mytowns:town/houses"],
  "piece": [
    "stagecoach:village/coach_stop_small",
    "stagecoach:village/coach_stop_small_roadside",
    "stagecoach:village/coach_stop_small_compact"
  ],
  "guaranteed": true,
  "theme": "stagecoach:plains",
  "wood": "spruce",
  "projection": "rigid"
}
```

| Field | Default | Meaning |
|---|---|---|
| `requires_mods` | `[]` | Mod ids that must all be loaded; otherwise the file is skipped |
| `target_pools` | required | The template pools whose slots get the pieces (the pool of the structure's houses) |
| `piece` | required | One piece id, or a list tried in order |
| `guaranteed` | `true` | `true`: offered first at every slot until the structure has a stop. `false`: competes with the pool's own pieces by `weight` |
| `weight` | | The weight of each piece when `guaranteed` is `false` (scaled by `generationWeightMultiplier`) |
| `theme` | required | The [theme](#themes) the coach gets |
| `wood` | the theme's | The coach's wood, and the wood its decoration turns into |
| `projection` | `"rigid"` | `"rigid"` or `"terrain_matching"` |

A pool that doesn't exist (a mod not installed, or a different version) is skipped. `/reload` reads the files again;
the change applies to structures generated after it.

### Which piece fits

A piece joins a house slot by its jigsaw. The pieces Stagecoach ships:

| Pieces | Jigsaw | Fits |
|---|---|---|
| `stagecoach:village/coach_stop_small` (+ `_roadside`, `_compact`) | `minecraft:building_entrance` | Vanilla, Repurposed Structures, CTOV, Towns and Towers, and most village mods |
| `stagecoach:village/coach_stop_integrated` (+ `_roadside`, `_compact`) | `integrated_villages:house` | Integrated Villages |
| `stagecoach:village/coach_stop_dt_birch`, `_dt_dappled`, `_dt_jungle`, `_dt_swamp`, `_dt_remnant` (+ `_roadside`, `_compact`) | Dungeons and Taverns' own house connectors | Dungeons and Taverns villages |

If your town's streets use another connector name, build your own piece: a dirt path pad with a coach on it and a
jigsaw block at the street edge whose name matches the street's target, saved with a structure block. Put its id in
`piece`.

## Themes

`data/<namespace>/stagecoach/themes/<name>.json`, used as `<namespace>:<name>`:

```json
{
  "wood": ["spruce", "dark_oak"],
  "decor_palette": "spruce",
  "name_pool": "stagecoach:northern"
}
```

| Field | Meaning |
|---|---|
| `wood` | A wood, or a list of them; each coach picks one from its position |
| `decor_palette` | The wood the piece's oak decoration (posts, fences, signs) turns into; leave it out to keep oak |
| `name_pool` | The [name pool](#name-pools) its names come from |

Stagecoach ships `badlands`, `beach`, `cherry`, `crimson`, `dark_forest`, `desert`, `jungle`, `plains`, `savanna`,
`snowy`, `swamp`, `taiga`, `tavern` and `warped`.

## Name pools

`data/<namespace>/stagecoach/name_pools/<name>.json`:

```json
{
  "patterns": ["{prefix}{suffix}", "{prefix}{suffix} Crossing", "{prefix}{suffix} Stop"],
  "prefix": ["Frost", "Pine", "Wolf"],
  "suffix": ["holm", "vik", "stad"]
}
```

Each pattern fills `{word list}` from the list of that name in the same file; a pattern may use any number of lists.
The same world seed and position always give the same name. Stagecoach ships `blossom`, `coastal`, `desert`,
`frontier`, `infernal`, `jungle`, `marsh`, `northern`, `pastoral`, `savanna`, `shadow` and `tavern`.

## Settings

`naturalGeneration`, `maxGeneratedStopsPerStructure`, `generationWeightMultiplier`, `generatedCoachesBreakable` and
`autoNameFormat` are in the [configuration](config.md#villages-and-generated-coaches).
