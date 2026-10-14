---
title: Travel rules
parent: Pack developer guide
nav_order: 6
---

# Travel rules

Travel rules lock destinations behind progress, or change what they cost, without any code. They are datapack files:
`data/<namespace>/stagecoach/travel_rules/<name>.json`, read again by `/reload`.

```json
{
  "applies_to": {
    "to_dimension": "minecraft:the_nether"
  },
  "requirements": {
    "advancement": "minecraft:story/enter_the_nether",
    "min_level": 20
  },
  "deny_message": {"text": "The Nether road opens once you've been there yourself.", "color": "red"},
  "cost_multiplier": 2.0
}
```

A rule **applies** to a journey when every condition in `applies_to` holds (a rule without `applies_to` applies to
every journey). When it applies:

- if the player meets every `requirements` entry, the journey's fare is multiplied by `cost_multiplier`;
- if not, the destination shows greyed out in the list with `deny_message` as its tooltip, and the server refuses the
  journey.

Every field is optional. Several rules can apply to the same journey: all their multipliers apply, and any failed one
locks it.

## Conditions (`applies_to`)

| Field | Matches |
|---|---|
| `to_stops` | The destination: a stop name (any capitals) or id (`"#12"` or `"12"`), or a list of them |
| `from_stops` | The stop the journey leaves from, the same way |
| `to_dimension` | The destination's dimension, e.g. `"minecraft:the_end"` |
| `from_dimension` | The dimension the journey leaves from |
| `stop_flag` | Destinations with that flag set: `"public"`, `"hidden"`, `"locked"` or `"free"` |

## Requirements

| Field | The player must |
|---|---|
| `advancement` | Have completed this advancement |
| `min_level` | Have at least this many XP levels (nothing is taken) |
| `permission` | Have this permission node (any node; false without a permissions mod) |
| `item` | Carry this item, or an item in this tag (`"#minecraft:boats"`), anywhere in their inventory, armour or off hand (nothing is taken) |
| `stage` | Have this Game Stages stage (Forge 1.20.1, the only build Game Stages exists for; elsewhere the rule can't be met) |

`deny_message` is a text component (the same JSON as `/tellraw`). `cost_multiplier` is 0 to 1000 (default 1).

## Examples

Members only, by permission:

```json
{
  "applies_to": {"to_stops": ["Guild Hall", "Guild Docks"]},
  "requirements": {"permission": "mypack.guild"},
  "deny_message": {"text": "Guild members only.", "color": "gold"}
}
```

A surcharge on every journey (a rule with no conditions and no requirements):

```json
{
  "cost_multiplier": 1.5
}
```

A map is needed to find the far stops:

```json
{
  "applies_to": {"to_stops": ["#40", "#41", "#42"]},
  "requirements": {"item": "minecraft:filled_map"},
  "deny_message": {"translate": "mypack.stagecoach.need_map"}
}
```

A bad file is skipped with one line in the log naming the file and what's wrong.
