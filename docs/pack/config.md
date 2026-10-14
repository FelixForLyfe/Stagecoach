---
title: Configuration
parent: Pack developer guide
nav_order: 1
---

# Configuration

`config/stagecoach.json` is written with every setting the first time the server starts. Edit it and run
`/stagecoach reload`. A missing setting takes its default; a setting with a wrong type or outside its range also
takes its default, with a warning. If a minimum is larger than its maximum, both are reset.

## Fares

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `baseCost` | 1 | 0–1000 | XP levels every journey costs |
| `blocksPerLevel` | 5000 | 1–10,000,000 | One more level for every this many blocks, or part of that |
| `maxCost` | 10 | 0–1000 | The most levels a journey can cost |
| `itemCostEnabled` | false | | Charge items instead of XP levels |
| `costItem` | `"minecraft:emerald"` | | The item: any item id (`"create:brass_ingot"`) or a tag (`"#minecraft:logs"`). An unknown id falls back to XP with a warning |
| `itemBaseCost` | 1 | 0–10,000 | Items every journey costs |
| `blocksPerItem` | 1000 | 1–10,000,000 | One more item for every this many blocks, or part of that |
| `maxItemCost` | 32 | 0–10,000 | The most items a journey can cost |
| `dimensionCostMultipliers` | `{}` | each 0–1000 | A multiplier per destination dimension, e.g. `{"minecraft:the_nether": 2.0}` |
| `crossDimensionCostMultiplier` | 1.0 | 0–1000 | An extra multiplier when the journey changes dimension |
| `creativeTravelsFree` | true | | Creative players pay nothing |
| `cancelRefundPercent` | 100 | 0–100 | How much of the fare comes back when a player cancels on the road |

The fare, in order: the base fare from the distance → × the stop's own cost multiplier → × the dimension multiplier →
× the cross-dimension multiplier → × every matching [travel rule](travel-rules.md)'s multiplier → rounded up →
clamped to `maxCost` / `maxItemCost` → 0 for a free stop, a creative player or anyone with `stagecoach.bypass.cost`.
Mods and scripts can change it last, in the `JourneyPreStart` event. The destination list shows the same number the
server charges.

## Time and distance

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `blocksPerSecond` | 75 | above 0 | Travel speed |
| `minTravelSeconds` | 5 | 0–86,400 | Shortest journey |
| `maxTravelSeconds` | 600 | 1–86,400 | Longest journey |
| `instantTravel` | false | | No time on the road: pay and arrive |
| `maxDistance` | 0 | 0–100,000,000 | The longest single trip in blocks; farther stops are greyed out ("too far for one trip"). 0 = no limit |
| `allowCrossDimension` | false | | Allow journeys between dimensions |
| `crossDimensionDistance` | 10000 | 0–100,000,000 | The distance a journey between dimensions counts as, for its fare, its time and `maxDistance` |

## Using coaches

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `useRange` | 12 | 1–256 | How close a player must stand to a coach to use it |
| `discoveryRequired` | true | | Stops must be discovered (right-clicked) before they show in a player's list |
| `combatLockSeconds` | 10 | 0–3600 | How long after being hurt a player can't set off |
| `travelCooldownSeconds` | 0 | 0–86,400 | A rest after arriving before the next journey |
| `maxNameLength` | 24 | 1–64 | The longest stop name |
| `autoNameFormat` | `"{biome} Stop"` | | The name of a coach nothing else names; `{biome}` is the biome's English name |

## Protection and ownership

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `onlyOwnerCanBreak` | true | | Only the owner or an operator can take a coach down, and explosions can't |
| `opsCanManageAnyCoach` | true | | Operators may rename and take down anyone's coach |
| `maxCoachesPerPlayer` | 0 | 0–100,000 | How many coaches one player may own; 0 = no limit. Operators and `stagecoach.bypass.limit` aren't limited |
| `blockedDimensions` | `[]` | | Dimensions where coaches can't be placed, e.g. `["minecraft:the_end"]` |
| `adminCommandLevel` | 2 | 0–4 | The operator level the [admin commands](commands.md) need (or the `stagecoach.admin.commands` node) |

## Villages and generated coaches

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `naturalGeneration` | true | | Coach stops generate in villages (and wherever your [structure injections](structures.md) put them) |
| `maxGeneratedStopsPerStructure` | 1 | 0–1000 | Stops per village or town |
| `generationWeightMultiplier` | 1.0 | 0–100 | Scales the chance of the non-guaranteed injections (the taverns) |
| `generatedCoachesBreakable` | false | | Whether generated coaches can be broken (they are locked otherwise) |

## Travel screen and content

| Setting | Default | Meaning |
|---|---|---|
| `defaultScene` | `"stagecoach:road"` | The [travel scene](theming.md) a stop gets when nothing else picks one |
| `forceClientTravelStyle` | `"none"` | `"road"`, `"map"` or `"minimal"` shows every player that screen; `"none"` lets each player choose |
| `builtinAdvancements` | true | The Stagecoach advancement tab (the [triggers](advancements.md) work either way) |
| `tfcRecipes` | true | TerraFirmaCraft recipes when TFC is installed (Forge 1.20.1, NeoForge 1.21.1) |

## Each player's settings

`config/stagecoach-client.json`, or the **Config** button in Mod Menu (Fabric) or the Mods list (Forge, NeoForge):

| Setting | Default | Meaning |
|---|---|---|
| `simplifiedLoadingScreen` | false | A map of the route instead of the road while travelling |
| `minimalLoadingScreen` | false | Only the sign, the progress bar and Cancel (wins over the map) |
| `showOtherStopsOnMap` | true | Other known stops as dots on the map |
| `showPlacementPreview` | true | The see-through ghost while holding a coach |
| `discoveryEffects` | true | Dust, embers and the road-sign card when discovering a stop |
| `discoverySound` | true | The coachman's horn on discovery |
| `placementEffects` | true | Dust and a creak of tack when a coach is placed nearby |

When the server sets `forceClientTravelStyle`, the two screen rows are greyed out with a note.
