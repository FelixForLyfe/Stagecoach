---
title: The Java API
parent: Modder guide
nav_order: 1
---

# The Java API

Everything goes through one interface, `net.feelix.stagecoach.api.StagecoachAPI`:

```java
StagecoachAPI api = StagecoachAPI.get();
```

| Method | Does |
|---|---|
| `getStops(MinecraftServer server)` | Every stop on the server |
| `getStops(ServerLevel level)` | Every stop in one dimension |
| `getStop(MinecraftServer server, long id)` | One stop by id, or empty |
| `findStopByName(MinecraftServer server, String name)` | The stop with that name, any capitals; empty when there is none, or more than one |
| `isDiscovered(ServerPlayer player, StopView stop)` | Whether the player knows it: discovered, their own, public, or `discoveryRequired` is off |
| `discover(ServerPlayer player, StopView stop)` | Adds it to the player's known stops, quietly: no message, no effect, no event, no advancement |
| `forget(ServerPlayer player, StopView stop)` | Removes it from the player's known stops |
| `isTravelling(ServerPlayer player)` | Whether the player is on the road |
| `startJourney(ServerPlayer player, StopView destination, JourneyOptions options)` | Sends the player on a journey; returns a `JourneyResult` |
| `cancelJourney(ServerPlayer player)` | Ends the player's journey at its origin, with the whole fare back (reason `ADMIN`) |

The collections are copies: changing them changes nothing.

## Stops

A `StopView` is a snapshot of a stop at the moment you asked for it:

| Field | Type | Meaning |
|---|---|---|
| `id` | `long` | Its id; stays the same for the stop's whole life, never reused |
| `name` | `String` | Its name |
| `dimension` | `ResourceKey<Level>` | The dimension it stands in |
| `pos` | `BlockPos` | The controller block |
| `facing` | `Direction` | The way the coach faces |
| `wood` | `String` | The wood, e.g. `"spruce"` |
| `owner` | `Optional<UUID>` | The player who owns it; empty for stops placed by commands or structures |
| `isPublic`, `hidden`, `locked`, `free` | `boolean` | Its [flags](../pack/commands.md) |
| `costMultiplier` | `double` | Its own fare multiplier |
| `scene` | `Optional<Identifier>` | Its travel scene, if it has one (`ResourceLocation` in Mojang names before 1.21.11) |
| `createdBy` | `CreatedBy` | `PLAYER`, `ADMIN` or `STRUCTURE` |

A stop placed by a structure joins the network once the chunk it's in has generated.

## Starting a journey

```java
StopView abbey = api.findStopByName(server, "Old Abbey").orElseThrow();
JourneyResult result = api.startJourney(player, abbey, JourneyOptions.NORMAL);
if (!result.started()) {
    player.sendSystemMessage(Component.literal("Can't go: " + result));
}
```

`JourneyOptions.NORMAL` checks everything a player's own journey checks: the player must stand within `useRange` of a
stop they know (the journey leaves from the nearest one), know the destination, be out of combat and off cooldown, and
pay the fare. The other options switch checks off:

```java
// a free ride from wherever the player stands, even to a stop they haven't found
new JourneyOptions(true, false, true, true, false)
```

| Option | When `true` |
|---|---|
| `free` | No fare |
| `instant` | No time on the road: the player pays and steps out at the destination at once |
| `ignoreRange` | The journey leaves from where the player stands, not from a stop; they come back there if it ends early |
| `ignoreDiscovery` | The destination (and, without `ignoreRange`, the stop they leave from) needn't be known |
| `ignoreCombatLock` | A player hit moments ago can still leave |

The permission to travel, the cooldown, the server's dimension and distance limits, and travel rules still apply, and
`JourneyPreStartEvent` still fires, so other mods can refuse.

| `JourneyResult` | Why |
|---|---|
| `STARTED` | On the road (or arrived, for an instant journey) |
| `NOT_ENOUGH_LEVELS`, `NOT_ENOUGH_ITEMS` | Can't pay the fare |
| `TOO_FAR` | Farther than `maxDistance` |
| `CROSS_DIMENSION_DISABLED` | Another dimension, and the server doesn't allow it |
| `COMBAT_LOCKED` | Hit within `combatLockSeconds` |
| `COOLDOWN` | Arrived within `travelCooldownSeconds` |
| `NOT_DISCOVERED` | Doesn't know the destination |
| `OUT_OF_RANGE` | Not near a stop they know |
| `CANCELLED_BY_EVENT` | A `JourneyPreStartEvent` listener (or a script) refused |
| `ALREADY_TRAVELLING` | Already on the road |
| `LOCKED_BY_RULE` | A [travel rule](../pack/travel-rules.md) locks it |
| `NO_PERMISSION` | Lacks `stagecoach.travel` |
| `UNAVAILABLE` | Anything else: the stop is gone, the player is a spectator or dead, or the destination is the stop they're at |

`result.started()` is true only for `STARTED`.

## Discovering for a reason

`discover` is silent so you can choose the feedback. A quest reward that reveals a stop:

```java
api.findStopByName(server, "Hollow Pass").ifPresent(stop -> {
    if (api.isDiscovered(player, stop)) return;
    api.discover(player, stop);
    player.sendSystemMessage(Component.literal("You learn the road to " + stop.name() + "."));
});
```

`/stagecoach discover <player> <stop>` is the other way: it fires `StopDiscoverEvent` (so listeners can refuse) and
the `stagecoach:stop_discovered` advancement trigger, without the message or the effect.
