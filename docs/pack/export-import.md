---
title: Export and import
parent: Pack developer guide
nav_order: 9
---

# Export and import

A whole stop network can be saved to a file and built again on another server or world: ship a ready-made road
network with your pack, or move one from a test world to the real server.

```
/stagecoach export kingdom
/stagecoach import kingdom
/stagecoach import kingdom replace
```

`export <name>` writes every stop to `config/stagecoach/exports/<name>.json`. `import <name>` reads that file and
builds each stop: it loads the stop's chunk, places the coach with `force` (clearing what's in the way) and gives it
its name, flags, cost multiplier and scene. Without `replace`, a stop whose name or position is already taken is
skipped; with `replace`, that stop is taken down first. The command reports how many were placed and lists the ones it
skipped.

Only the network travels: positions, woods, names and settings. Owners, discoveries and journeys stay with the world
they belong to. Imported stops belong to the server.

## The file

```json
{
  "format": 1,
  "stops": [
    {
      "name": "Kingsbridge",
      "dimension": "minecraft:overworld",
      "pos": [0, 64, 0],
      "facing": "north",
      "wood": "oak",
      "flags": ["public", "locked"],
      "cost": 1.0,
      "scene": "mypack:city"
    }
  ]
}
```

`scene` is left out when the stop has none. You can write or edit these files by hand; an entry that can't be read
(an unknown dimension or wood, a bad facing) is skipped with a line naming it.

## Shipping a network with a pack

Put the file in your pack's `config/stagecoach/exports/` folder and run `/stagecoach import <name>` once on the
server (by hand or from a function), after the world has been created.
