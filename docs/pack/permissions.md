---
title: Permissions
parent: Pack developer guide
nav_order: 3
---

# Permissions

Every action has a permission node. With a permissions mod installed (LuckPerms, FTB Ranks or any other), the nodes
decide; without one, the defaults in the table apply, which are what Stagecoach always did.

- **FTB Ranks** is asked first wherever it is installed (Fabric and NeoForge, and Forge 1.20.1): a node set in
  `ranks.snbt` wins. A parent node counts, so `stagecoach.admin: true` also grants `stagecoach.admin.manage`.
- **Forge and NeoForge** register the nodes with their built-in permission API, so any permissions mod sees them.
- **Fabric** uses fabric-permissions-api when a permissions mod has installed it.

| Node | Default | Allows |
|---|---|---|
| `stagecoach.place` | everyone | Placing a coach |
| `stagecoach.travel` | everyone | Setting off on a journey |
| `stagecoach.discover` | everyone | Discovering a stop by right-clicking it |
| `stagecoach.break.own` | everyone | Taking down your own coach |
| `stagecoach.rename.own` | everyone | Renaming your own stop |
| `stagecoach.break.any` | operators (level 2) | Taking down anyone's coach |
| `stagecoach.admin.manage` | operators (level 2) while `opsCanManageAnyCoach` is on; nobody when it's off | Renaming and taking down anyone's coach |
| `stagecoach.admin.commands` | operators at `adminCommandLevel` (2) | The [`/stagecoach` commands](commands.md) |
| `stagecoach.bypass.limit` | operators (level 2) | Owning more coaches than `maxCoachesPerPlayer` |
| `stagecoach.bypass.cost` | nobody | Travelling free |
| `stagecoach.global` | nobody, unless `anyoneCanMakeGlobal` is on | Making your own stops global. Operators (`stagecoach.admin.manage`) can make any stop global |

[Travel rules](travel-rules.md) can also require any node, your own included.

## Examples (LuckPerms)

Only staff place coaches; players travel and discover:

```
/lp group default permission set stagecoach.place false
/lp group staff permission set stagecoach.place true
```

A VIP rank that travels free:

```
/lp group vip permission set stagecoach.bypass.cost true
```

Builders who may make their own stops global:

```
/lp group builder permission set stagecoach.global true
```

## Per-player settings

Every setting in [`config/stagecoach.json`](config.md) can be set differently for a group or a player. The option is
`stagecoach.` and the setting's name in snake case: `maxCoachesPerPlayer` is `stagecoach.max_coaches_per_player`,
`baseCost` is `stagecoach.base_cost`. `/stagecoach config <setting>` shows the name. A value that isn't valid for the
setting is ignored with a warning in the log, and the server's value applies.

With **LuckPerms** the options are meta values:

```
/lp group vip meta set stagecoach.max_coaches_per_player 10
/lp group vip meta set stagecoach.max_cost 3
/lp group default meta set stagecoach.travel_cooldown_seconds 60
/lp group staff meta set stagecoach.instant_travel true
```

With **FTB Ranks**, add them to a rank in `ranks.snbt`, next to its other nodes:

```
vip: {
    name: "VIP"
    power: 50
    "stagecoach.max_coaches_per_player": 10
    "stagecoach.max_cost": 3
    "stagecoach.global": true
}
```

The settings a player's actions use follow the player: fares, travel time, `maxDistance`, `instantTravel`,
`allowCrossDimension`, `useRange`, the combat lock and cooldown, `cancelRefundPercent`, `creativeTravelsFree`,
`discoveryRequired`, `onlyOwnerCanBreak` (for who breaks), `maxCoachesPerPlayer`, `maxNameLength`,
`blockedDimensions` (for who places), `anyoneCanMakeGlobal` and `opsCanManageAnyCoach`. Settings about the world
itself (village generation, recipes, advancements, the travel scene, `adminCommandLevel` for the console) stay
server-wide.
