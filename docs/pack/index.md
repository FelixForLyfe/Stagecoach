---
title: Pack developer guide
nav_order: 2
has_children: true
permalink: /pack/
---

# Pack developer guide

For modpack makers and server owners. Nothing here needs code: it is all settings, commands, permissions, datapacks
and resource packs.

| You want to… | Read |
|---|---|
| Change fares, travel times, distances, protection or generation | [Configuration](config.md) |
| Build, edit, move or remove stops, send players on journeys | [Commands](commands.md) |
| Give or take away what players and staff may do | [Permissions](permissions.md) |
| Rebalance the recipe, or use the tags | [Recipes and tags](recipes-tags.md) |
| Put coaches in your own towns, or stops in another mod's villages | [Structures and villages](structures.md) |
| Lock destinations behind progress, or price them differently | [Travel rules](travel-rules.md) |
| Write quests and advancements around travel | [Advancements and statistics](advancements.md) |
| Replace the travel screen, its sounds or its text | [Theming](theming.md) |
| Ship a ready-made stop network with your pack | [Export and import](export-import.md) |
| Know what the licence allows | [Licence for modpacks](licence.md) |

## Where things live

| What | Where | Reloaded by |
|---|---|---|
| Server settings | `config/stagecoach.json` | `/stagecoach reload` |
| Each player's own settings | `config/stagecoach-client.json` (or the Config button in Mod Menu / the Mods list) | the config screen |
| Travel rules, themes, name pools, structure injections | datapacks: `data/<namespace>/stagecoach/<kind>/*.json` | `/reload` |
| Travel scenes and scene rules | resource packs: `assets/<namespace>/stagecoach/…` | F3+T |
| The stop network | `<world>/data/stagecoach_network.dat` (26.x: `<world>/data/stagecoach/stagecoach_network.dat`) | — |
| Exported networks | `config/stagecoach/exports/<name>.json` | `/stagecoach import` |

A bad entry in any of these is skipped with one warning in the log saying which file and why; everything else keeps
working. `/stagecoach reload` also prints the config's warnings to whoever ran it.
