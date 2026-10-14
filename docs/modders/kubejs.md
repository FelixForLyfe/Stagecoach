---
title: KubeJS
parent: Modder guide
nav_order: 3
---

# KubeJS

With [KubeJS](https://kubejs.com/) installed, server scripts get every Stagecoach event and the whole API, with no mod
of your own. KubeJS works with Stagecoach on **1.20.1 (Fabric and Forge)** and **NeoForge 1.21.1**: the builds KubeJS
itself exists for.

## Events

Each [event](events.md) has a handler in the `StagecoachEvents` group. `e.event` is the Stagecoach event itself, so
its getters and setters read like properties: `e.event.cost`, `e.event.cost = 2`.

| Handler | Event | `e.cancel()` |
|---|---|---|
| `StagecoachEvents.preJourney` | `JourneyPreStartEvent` | refuses the journey |
| `StagecoachEvents.journeyStarted` | `JourneyStartedEvent` | |
| `StagecoachEvents.journeyCompleted` | `JourneyCompletedEvent` | |
| `StagecoachEvents.journeyEnded` | `JourneyEndedEvent` | |
| `StagecoachEvents.stopDiscover` | `StopDiscoverEvent` | keeps the stop unknown |
| `StagecoachEvents.coachPlace` | `CoachPlaceEvent` | refuses the placement |
| `StagecoachEvents.coachBreak` | `CoachBreakEvent` | refuses the break |
| `StagecoachEvents.destinationList` | `DestinationListEvent` | |

`kubejs/server_scripts/stagecoach.js`:

```js
StagecoachEvents.preJourney(e => {
  const journey = e.event
  if (journey.to.name() == 'Old Abbey' && journey.player.experienceLevel < 30) {
    journey.failMessage = Text.red('The abbey road is for the learned (level 30).')
    e.cancel()
  }
  // no journey takes longer than a minute
  journey.travelSeconds = Math.min(journey.travelSeconds, 60)
})

StagecoachEvents.journeyCompleted(e => {
  if (e.event.distance >= 1000) e.event.player.give('minecraft:bread')
})

StagecoachEvents.destinationList(e => {
  e.event.destinations.removeIf(d => d.stop().name() == "Smugglers' Cove")
})
```

A stop is a record, so its fields are methods: `stop.name()`, `stop.pos()`, `stop.wood()`. `/reload` reloads the
scripts.

On Forge 1.20.1 KubeJS's own Forge event listener reaches the same events, if you prefer it:

```js
ForgeEvents.onEvent('net.feelix.stagecoach.api.event.JourneyPreStartEvent', event => {
  if (event.to.name() == 'Old Abbey') event.setCanceled(true)
})
```

## The API

The binding `Stagecoach` is [`StagecoachAPI`](api.md):

```js
// a compass that reveals Kingsbridge
ItemEvents.rightClicked('minecraft:compass', e => {
  const stop = Stagecoach.findStopByName(e.server, 'Kingsbridge').orElse(null)
  if (stop == null || Stagecoach.isDiscovered(e.player, stop)) return
  Stagecoach.discover(e.player, stop)
  e.player.tell(Text.gold('You learn the road to Kingsbridge.'))
})
```

To start a journey, load the options class:

```js
const JourneyOptions = Java.loadClass('net.feelix.stagecoach.api.JourneyOptions')

// free, from where the player stands, to a stop they needn't have found
const result = Stagecoach.startJourney(player, stop, new JourneyOptions(true, false, true, true, false))
if (!result.started()) player.tell('Could not leave: ' + result)
```
