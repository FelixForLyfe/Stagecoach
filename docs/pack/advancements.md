---
title: Advancements and statistics
parent: Pack developer guide
nav_order: 7
---

# Advancements and statistics

## Triggers

Five advancement triggers, for your own advancements and for quests. Every condition is optional; a criterion with
none fires every time.

| Trigger | Fires when a player… | Conditions |
|---|---|---|
| `stagecoach:coach_placed` | places a coach | `wood`, `dimension` |
| `stagecoach:stop_discovered` | discovers a stop | `stop_name`, `stop_id`, `total_discovered`, `dimension` |
| `stagecoach:journey_started` | sets off | `from`, `to`, `distance`, `cost`, `cross_dimension` |
| `stagecoach:journey_completed` | arrives | `from`, `to`, `distance`, `total_journeys`, `cross_dimension` |
| `stagecoach:journey_cancelled` | cancels on the road | `to` |

| Condition | Value |
|---|---|
| `wood` | A wood name, e.g. `"cherry"` |
| `dimension` | A dimension id, e.g. `"minecraft:the_nether"` |
| `stop_name`, `from`, `to` | A stop's name (any capitals) |
| `stop_id` | A stop's id number |
| `distance` | Blocks (straight line; `crossDimensionDistance` between dimensions) |
| `cost` | What was paid, in levels or items |
| `total_discovered` | How many stops the player knows, counting this one |
| `total_journeys` | How many journeys the player has completed, counting this one |
| `cross_dimension` | `true` or `false` |

Number conditions take a number (exactly that) or `{"min": …, "max": …}` (either end optional). Vanilla's own
`player` condition works too.

### Examples

Arrive at a particular stop (1.21 and later: `data/mypack/advancement/`; 1.20.1: `data/mypack/advancements/`):

```json
{
  "parent": "stagecoach:main/root",
  "display": {
    "icon": {"id": "minecraft:bell"},
    "title": "Pilgrim",
    "description": "Ride to the Old Abbey"
  },
  "criteria": {
    "abbey": {
      "trigger": "stagecoach:journey_completed",
      "conditions": {"to": "Old Abbey"}
    }
  }
}
```

(On 1.20.1 the icon is `{"item": "minecraft:bell"}`.)

Discover 25 stops, or travel 5,000 blocks or more in one go:

```json
"criteria": {
  "explorer": {"trigger": "stagecoach:stop_discovered", "conditions": {"total_discovered": {"min": 25}}},
  "far": {"trigger": "stagecoach:journey_completed", "conditions": {"distance": {"min": 5000}}}
}
```

## The built-in tab

A **Stagecoach** advancement tab: *On the Road* (place a stagecoach), *Fare Paid* (finish a journey), *Well
Travelled* (discover 10 stops) and *The Long Road* (one journey of 10,000 blocks or more). `builtinAdvancements: false`
removes it; the triggers keep working. Their ids are `stagecoach:main/root`, `main/first_journey`, `main/well_travelled`
and `main/long_road`, so your own advancements can hang off them, or replace them with a datapack.

## Statistics

| Statistic | Counts |
|---|---|
| `stagecoach:journeys_completed` | Journeys completed |
| `stagecoach:blocks_travelled` | Blocks travelled by stagecoach (added on arrival) |
| `stagecoach:stops_discovered` | Stops discovered |
| `stagecoach:coaches_placed` | Coaches placed |

They're custom statistics (`minecraft.custom:stagecoach.journeys_completed` in a scoreboard objective) and show in the
statistics screen.

## FTB Quests

Use an **Advancement** task with any of the advancements above, or your own built on the triggers; or a **Stat** task
with one of the statistics.
