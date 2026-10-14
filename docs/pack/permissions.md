---
title: Permissions
parent: Pack developer guide
nav_order: 3
---

# Permissions

Every action has a permission node. With a permissions mod installed (LuckPerms or any other), the nodes decide;
without one, the defaults in the table apply, which are what Stagecoach always did.

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
