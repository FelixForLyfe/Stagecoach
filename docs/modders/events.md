---
title: Events
parent: Modder guide
nav_order: 2
---

# Events

Eight events in `net.feelix.stagecoach.api.event`, with the same names, getters and setters on every loader. Each
fires on the server thread.

| Event | Fires | Cancel to |
|---|---|---|
| `JourneyPreStartEvent` | After every built-in check passed, before anything is paid | Refuse the journey |
| `JourneyStartedEvent` | After the fare was taken and the journey saved | — |
| `JourneyCompletedEvent` | After the player was set down at the destination | — |
| `JourneyEndedEvent` | Exactly once per journey, however it ended | — |
| `StopDiscoverEvent` | Before a player discovers a stop (by hand or by `/stagecoach discover`) | Keep the stop unknown to them |
| `CoachPlaceEvent` | Before a coach is placed, by a player or a command | Refuse the placement |
| `CoachBreakEvent` | Before a player takes a coach down | Refuse it |
| `DestinationListEvent` | Before a player's destination list is shown, and again when they pick from it | — (edit the list) |

`StagecoachAPI#discover` doesn't fire `StopDiscoverEvent`; every journey, whoever started it, fires the four journey
events.

## Listening on each loader

**Fabric** — each event has a static `EVENT`:

```java
JourneyPreStartEvent.EVENT.register(event -> {
    if (event.getTo().dimension() == Level.NETHER && !hasNetherPass(event.getPlayer())) {
        event.setFailMessage(Component.literal("You need a Nether pass."));
        event.setCanceled(true);
    }
});
```

**NeoForge** (1.21.1 and later) — the game bus:

```java
NeoForge.EVENT_BUS.addListener((JourneyPreStartEvent event) -> {
    if (event.getTo().dimension() == Level.NETHER && !hasNetherPass(event.getPlayer())) {
        event.setFailMessage(Component.literal("You need a Nether pass."));
        event.setCanceled(true);
    }
});
```

`@SubscribeEvent` methods in an `@EventBusSubscriber` class work too.

**Forge 1.20.1 and 1.21.1** — the same, on `MinecraftForge.EVENT_BUS`:

```java
MinecraftForge.EVENT_BUS.addListener((JourneyPreStartEvent event) -> { ... event.setCanceled(true); });
```

**Forge 1.21.11, 26.2 and 26.3** (EventBus 7) — each event has a static `BUS`. On the events you can cancel, a
listener that returns `true` cancels:

```java
JourneyPreStartEvent.BUS.addListener(event -> {
    if (event.getTo().dimension() != Level.NETHER || hasNetherPass(event.getPlayer())) return false;
    event.setFailMessage(Component.literal("You need a Nether pass."));
    return true;
});

JourneyCompletedEvent.BUS.addListener(event -> rewardTraveller(event.getPlayer(), event.getDistance()));
```

To only watch a cancellable event, pass a priority: `JourneyPreStartEvent.BUS.addListener(Priority.NORMAL, event -> {
... })`. Calling `event.setCanceled(true)` works on this loader as well.

## The events

Fields marked **settable** have a setter (`setCost`, `setFailMessage`…); the rest are read only. `from`, `here` and a
command's `player` can be null.

### JourneyPreStartEvent

| Field | Type | |
|---|---|---|
| `player` | `ServerPlayer` | |
| `from` | `StopView` | The stop the journey leaves from; null when it leaves from where the player stands (`ignoreRange`) |
| `to` | `StopView` | The destination |
| `cost` | `int` | **settable**. The fare in levels, or in items when `itemFare` |
| `itemFare` | `boolean` (`isItemFare()`) | The server charges items |
| `travelSeconds` | `int` | **settable**. The journey's length |
| `instant` | `boolean` (`isInstant()`) | No time on the road |
| `failMessage` | `Component` | **settable**. What the player is told when you cancel; a general message when you leave it null |

The fare you set is checked against what the player has after every listener ran, so lowering it can let a player go
who couldn't afford the list's price, and raising it can stop them. A negative value counts as 0.

### JourneyStartedEvent

`player`, `from`, `to`, `cost` (what was taken), `itemFare`, `travelSeconds`, `instant`. An instant journey fires this,
then the completed and ended events, in the same tick.

### JourneyCompletedEvent

`player`, `from`, `to`, `distance` (blocks, as the fare was worked out).

### JourneyEndedEvent

`player`, `from`, `to`, `reason`:

| `EndReason` | |
|---|---|
| `COMPLETED` | Arrived |
| `CANCELLED` | The player cancelled |
| `DISCONNECTED` | The player left the server |
| `SERVER_STOPPED` | The server shut down |
| `ADMIN` | `/stagecoach journey cancel` or `StagecoachAPI#cancelJourney` |
| `DESTINATION_GONE` | The destination coach was taken down |
| `GAME_MODE_CHANGED` | Something else changed the player's game mode |
| `DIED` | The player died on the road |
| `RECOVERED` | The server had crashed; the journey was settled when the player came back |

### StopDiscoverEvent

`player`, `stop`. Cancel and the player doesn't learn the stop; they get no message, so send your own.

### CoachPlaceEvent

| Field | Type | |
|---|---|---|
| `player` | `ServerPlayer` | null when a command placed it |
| `world` / `level` | `ServerLevel` | `getWorld()` on 1.20.1 and 1.21.1, `getLevel()` on 1.21.11 and 26.x |
| `pos` | `BlockPos` | The controller |
| `facing` | `Direction` | |
| `wood` | `String` | |
| `failMessage` | `Component` | **settable**. Shown to the player, or as the command's error, when you cancel |

It fires after the dimension, permission and coach-limit checks; a coach can still be refused afterwards for lack of
space or a claim. Coaches placed by structures don't fire it.

### CoachBreakEvent

`player`, `stop`, `failMessage` (**settable**). Fires after the lock and owner checks: Stagecoach's own rules let this
player break it, and you can still say no.

### DestinationListEvent

`player`, `here` (the stop they're at), `destinations`: a mutable `List<Destination>`, nearest first. Add, remove,
reorder or replace entries.

```java
DestinationListEvent.EVENT.register(event -> {
    // a toll road: the Old Abbey costs twice as much for anyone not in the guild
    event.getDestinations().replaceAll(d -> d.stop().name().equals("Old Abbey") && !inGuild(event.getPlayer())
            ? d.withCost(d.cost() * 2) : d);
    // and the smugglers' cove isn't listed at all
    event.getDestinations().removeIf(d -> d.stop().name().equals("Smugglers' Cove"));
});
```

A `Destination` is `stop`, `distance`, `cost`, `seconds`, `status` and `lockMessage`, with `withCost(int)` and
`withStatus(Status, Component)` to make changed copies.

- The list is built again when the player picks a stop, and only a stop in that new list with status `OK` can be
  travelled to; its `cost` is the fare `JourneyPreStartEvent` starts from. Removing a stop is enough to keep a player
  from going there from the list. Give the same answer both times.
- After your listener, an `OK` or `CANT_AFFORD` entry gets the status its cost calls for, so `withCost` is all a price
  change needs.
- `withStatus(Status.LOCKED, message)` greys a stop out with your message as its tooltip.
- An entry you add for a stop the player hasn't found can be travelled to.
