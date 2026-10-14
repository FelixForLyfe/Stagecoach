---
title: Commands
parent: Pack developer guide
nav_order: 2
---

# Commands

Every command is under `/stagecoach`. They need operator level 2 (`adminCommandLevel` in the
[config](config.md)) or, for players, the `stagecoach.admin.commands` [permission node](permissions.md). Players
without it don't see the command at all. The console, command blocks and functions are checked by level.

## Naming a stop

Wherever a command takes `<stop>`, give either its **name** (in double quotes when it has spaces; capitals don't
matter, but it must be the whole name) or its **id** with a `#` in front. If two stops share the name, the command
lists both with their ids and positions; use the id. Tab completion offers names and ids.

```
/stagecoach stop info "Market Cross"
/stagecoach stop info #12
```

Every stop has a numeric id that never changes and is never reused. `/stagecoach stop list` shows them.

## Stops

| Command | What it does |
|---|---|
| `stop create <name> [pos] [facing] [wood] [force]` | Places a server-owned stop. `facing` is `north`, `east`, `south` or `west` (default: the way you face; north from a command block). `wood` is one of the eleven woods. Without `force` it needs a clear 3×3×6 space and an allowed dimension; with `force` it replaces whatever is in the way and ignores the dimension list |
| `stop list [dimension] [page]` | Ten stops a page; click a line for its details, click the arrows for the next page |
| `stop info <stop>` | Name, id, position and facing, owner, how it was made (player, admin or structure), wood, flags, cost multiplier, scene, and for a generated stop its settlement |
| `stop rename <stop> <new name>` | Renames it (the rest of the line is the name) |
| `stop setowner <stop> <player>` / `stop setowner <stop> none` | Gives the stop to a player (online or known to the server), or to the server |
| `stop flag public\|hidden\|locked\|free <stop> <true\|false>` | Sets a flag (see below) |
| `stop cost <stop> <multiplier>` | Multiplies this stop's fare (0–1000; 0 is free) |
| `stop scene <stop> <scene id>` / `stop scene <stop> none` | Gives the stop its own [travel scene](theming.md) |
| `stop remove <stop> [keepCoach]` | Removes the stop and its coach. With `keepCoach` the coach stays as an out-of-service decoration that never becomes a stop again |
| `stop tp <stop>` | Teleports you to the front of the stop |

### Flags

| Flag | Effect |
|---|---|
| `public` | Everyone knows the stop without discovering it; right-clicking it opens the list straight away |
| `hidden` | Left out of every destination list, the owner's too; players can still set off from it |
| `locked` | Nobody can break it, operators in creative included, and explosions don't touch it. `stop remove` still works, and so do `/setblock` and `/fill` |
| `free` | Journeys to it cost nothing |

Coaches made by `stop create`, by `import` or by a structure never drop a coach item when taken down; only coaches
players placed do.

## Discovery

| Command | What it does |
|---|---|
| `discover <players> <stop>` / `discover <players> *` | The players now know that stop, or every stop |
| `forget <players> <stop>` / `forget <players> *` | The players forget it (owners always know their own coaches) |

## Journeys

| Command | What it does |
|---|---|
| `journey send <players> <stop> [free] [instant]` | Sends each player to the stop from wherever they stand. Without `free` they pay the normal fare for that distance. `instant` skips the road. Players who can't pay, are already travelling, are spectators or are dead are skipped, with a line saying why. Cancelling, a refund or crash recovery returns them to where they stood |
| `journey cancel <players>` | Ends their journeys with a full refund |
| `journey list` | Everyone on the road, where to, and how long is left |

## Networks and settings

| Command | What it does |
|---|---|
| `export <name>` | Writes every stop to `config/stagecoach/exports/<name>.json` ([format](export-import.md)) |
| `import <name> [replace]` | Builds the stops in that file. Without `replace`, a stop whose name or position is taken is skipped; with it, the old stop is removed first |
| `reload` | Reads `config/stagecoach.json` again and applies the structure injections again. Datapack files reload with `/reload`, resource-pack scenes with F3+T |

## Examples

A public, locked hub at spawn that costs nothing to reach:

```
/stagecoach stop create "Kingsbridge" 0 64 0 north oak
/stagecoach stop flag public "Kingsbridge" true
/stagecoach stop flag locked "Kingsbridge" true
/stagecoach stop flag free "Kingsbridge" true
```

A quest reward that takes the player to a hidden stop for free (from a function or command block):

```
/stagecoach discover @p "Old Mine"
/stagecoach journey send @p "Old Mine" free
```
