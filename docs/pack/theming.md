---
title: Theming
parent: Pack developer guide
nav_order: 8
---

# Theming

The travel screen, its sounds and its text all come from resource packs, so a pack can make Stagecoach its own.

## Travel scenes

The travel screen's road (the default style) is a **scene**. A scene file lives at
`assets/<namespace>/stagecoach/scenes/<name>.json` and is used as `<namespace>:<name>`. There are three kinds.

**The built-in road** — the procedural hills, trees and fence posts. This is Stagecoach's own `stagecoach:road`:

```json
{"builtin": "road", "coach": true, "ambient_sound": "stagecoach:journey.loop"}
```

**One image**, filling the screen (`"fit": "cover"`, cropped) or shown whole on black (`"fit": "contain"`):

```json
{"image": "mypack:textures/scene/harbour.png", "fit": "cover", "coach": false}
```

**Scrolling layers** over a sky, drawn back to front:

```json
{
  "sky": {"top": "#3b5ba9", "bottom": "#e8b78c"},
  "coach": true,
  "ambient_sound": "mypack:wind",
  "layers": [
    {"texture": "mypack:textures/scene/mountains.png", "speed": 0.1, "y": 0.25, "scale": 2},
    {"texture": "mypack:textures/scene/forest.png", "speed": 0.5, "y": 0.5, "scale": 2, "fill": "#2f4a2a"},
    {"texture": "mypack:textures/scene/road.png", "speed": 1.0, "y": 0.7, "scale": 2, "fill": "#6b5137"}
  ]
}
```

| Field | Default | Meaning |
|---|---|---|
| `builtin` | | `"road"`: the built-in road |
| `image` | | A texture to show alone |
| `fit` | `"cover"` | With `image`: `"cover"` or `"contain"` |
| `sky.top`, `sky.bottom` | blue | The sky gradient behind the layers |
| `layers` | none | Each layer tiles one texture across the screen and scrolls it |
| `coach` | `true` | Show the player walking along the progress bar |
| `ambient_sound` | none | A sound event looped while the screen is open |

| Layer field | Default | Meaning |
|---|---|---|
| `texture` | required | The texture's full id, e.g. `"mypack:textures/scene/forest.png"` |
| `speed` | 1 | Scroll speed: 1 is the road's own speed (1.6 GUI pixels a tick); far layers go slower |
| `y` | 0.5 | Where the layer's top edge sits, as a share of the screen height (0 is the top) |
| `scale` | 1 | GUI pixels per texture pixel (above 0, at most 16) |
| `fill` | none | A colour painted from the layer's bottom edge to the bottom of the screen |

Colours are `"#rrggbb"` or `"#aarrggbb"`. A scene that fails to load is skipped with a warning and the next choice is
used. F3+T reloads them.

To change the road everyone sees, override `assets/stagecoach/stagecoach/scenes/road.json` with your own scene.

## Which scene a journey gets

1. The destination stop's own scene (`/stagecoach stop scene`, or `stagecoach:template_scene` in a structure).
2. The first matching **scene rule** from the player's resource packs.
3. The server's `defaultScene`.
4. `stagecoach:road`.

Scene rules are `assets/<namespace>/stagecoach/scene_rules.json`; rules from every pack and namespace are combined, and
the first one that matches the destination wins:

```json
{
  "rules": [
    {"dimension": "minecraft:the_nether", "scene": "mypack:nether_road"},
    {"biome_tag": "#minecraft:is_taiga", "scene": "mypack:snowy_pass"},
    {"biome": "minecraft:desert", "scene": "mypack:dunes"}
  ]
}
```

Scenes only change the road style. The map and minimal styles each player can pick stay as they are, unless the
server sets `forceClientTravelStyle`.

## Sounds

Replace any of these in your resource pack's `assets/stagecoach/sounds.json`:

| Sound event | Plays | By default |
|---|---|---|
| `stagecoach:journey.depart` | when a journey starts | a horse's gallop |
| `stagecoach:journey.arrive` | on arrival | a bell |
| `stagecoach:journey.loop` | looped during the journey (as the road scene's `ambient_sound`) | nothing |
| `stagecoach:stop.discover` | when a stop is discovered (the *Discovery horn*) | a goat horn |
| `stagecoach:coach.place` | when a coach is placed | a horse saddle |

## Text and textures

Every piece of text is a translation key in `assets/stagecoach/lang/en_us.json`; a resource pack's own `en_us.json`
(or any other language) can change any of them, for example `"stagecoach.toast.discovered": "Waystation found"`.

The discovery particles are `assets/stagecoach/textures/particle/road_dust_0.png` to `road_dust_3.png` and
`lantern_ember_0.png` and `lantern_ember_1.png`. The coaches' textures are
`assets/stagecoach/textures/entity/stagecoach/<wood>.png`.
