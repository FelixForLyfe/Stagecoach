# Changelog

## 1.2.0 — a coach stop in every village, and a toolkit for pack makers

**Villages now come with a coach stop.** With natural generation on (the default), every village gets exactly one
stagecoach, built in the wood of its own houses, and finding it feels like a moment: road dust, lantern embers and a
road-sign card. Underneath, 1.2.0 is the pack-maker update: admin commands, coaches you can save into your own
structures, advancements and statistics, a Java API with events, KubeJS support, data-driven travel rules, travel
scenes from resource packs, permission nodes, claim and map-mod support, and export/import of whole stop networks.

**Update the server and every player to 1.2.0 together** — the network changed again, so 1.1.0 and 1.2.0 can't play
together. Worlds carry over: coaches, names, owners and discovered stops are kept, and the network file is upgraded in
place the first time 1.2.0 saves it. New settings are added to `config/stagecoach.json` with their defaults.
Setting `naturalGeneration` and `discoveryEffects` to `false` gives you 1.1.0's game exactly.

Stagecoach is now **open source** under the MIT licence with one condition: credit the original author and link back
to this project. The source, the pack developer guide and the modder guide are on GitHub.

### World content (phase 4)

**A coach stop in every village**
- Every village gets one coach stop, in the wood its houses are built from (counted from each village's own pieces):
  oak in the plains, jungle wood in the desert, acacia in the savanna, spruce in the snow and the taiga, and so on.
- Covered out of the box: the vanilla villages (zombie villages too), **Repurposed Structures**, **ChoiceTheorem's
  Overhauled Village**, **Towns and Towers**, **Integrated Villages** and the villages of **Dungeons and Taverns**.
  Dungeons and Taverns' taverns get a stop by chance instead (about nine in ten did in testing): they aren't villages, and the
  coach takes a tavern's event slot.
- Measured on real servers with all of those installed: every village type that has streets got exactly one stop,
  every time. On Minecraft 26.3: 40 of 40 vanilla villages, 45 of 45 Repurposed Structures, 62 of 62 Towns and Towers
  and 18 of 18 Dungeons and Taverns villages. On 1.20.1: 288 of 288 villages on Fabric (vanilla, Repurposed
  Structures, CTOV, Towns and Towers) and 93 of 93 on Forge (with Integrated Villages).
- Not covered: four tiny Towns and Towers settlements that are a single building with no street (the grove outpost,
  the snowy-slopes inn and the savanna-plateau tower) or whose houses stack onto a base plate (the nilotic village).
- A village's stop is a real stop: it has a name from a themed name pool ("Frostholm Crossing", "The Lucky Badger Inn"),
  belongs to the server, is locked (nobody breaks it) and never drops a coach item. One stop per settlement.
- Turn it off with `naturalGeneration: false`. Pack makers can add their own structures with a data file (below).

**Discovery and placement feedback**
- Discovering a stop now shows a small wooden road-sign card ("Stop discovered") with the stop's name, kicks up dust
  at the wheels, sends embers up from the corner pillars and sounds the coachman's horn.
- Placing a coach raises a little dust and a creak of tack for everyone nearby.
- All of it follows each player's own particle setting, and can be switched off in the client options:
  *Discovery effects*, *Discovery horn* and *Placement effects*.

### Pack essentials (phase 1)

**Stops have flags**
- **public** — everyone knows the stop without discovering it.
- **hidden** — left out of every destination list (you can still set off from it).
- **locked** — nobody can break it, operators included, and explosions don't touch it.
- **free** — journeys to it cost nothing.
- A **cost multiplier** and a **travel scene** per stop, and ownerless stops that belong to the server.

**Admin commands** (operator level 2 by default, `adminCommandLevel`, or the `stagecoach.admin.commands` node):
- `/stagecoach stop create <name> [pos] [facing] [wood] [force]` — places a server stop; `force` clears the way.
- `/stagecoach stop list [dimension] [page]` — ten per page, each line clickable.
- `/stagecoach stop info|rename|setowner|flag|cost|scene|remove|tp <stop>` — `<stop>` is a name or `#id`;
  `remove <stop> keepCoach` leaves the coach standing as an out-of-service decoration.
- `/stagecoach discover|forget <players> <stop|*>`.
- `/stagecoach journey send <players> <stop> [free] [instant]`, `journey cancel <players>`, `journey list`.
- `/stagecoach export <name>` and `/stagecoach import <name> [replace]`.
- `/stagecoach reload`.

**Coaches in your own structures**
- A coach saved into a structure template (a structure block around a placed coach) comes back as a new stop wherever
  the template is placed — by jigsaw, `/place` or a structure block — rotated or mirrored, whole.
- Template keys set its name, flags, cost multiplier, travel scene, theme and wood (`stagecoach:template_*`).
- Coaches without a template name are named from their biome: `autoNameFormat`, default `"{biome} Stop"`.
- A coach placed with `/setblock` now becomes a stop at once, with its parts.

**Recipes, tags, advancements and statistics**
- New tags: `stagecoach:stagecoaches` (blocks and items) and `stagecoach:placement_blocked_on` (blocks a coach can't
  stand on).
- Five advancement triggers for your own advancements and quests: `stagecoach:coach_placed`, `stop_discovered`,
  `journey_started`, `journey_completed` and `journey_cancelled`, each with optional conditions.
- A new Stagecoach advancement tab: *On the Road*, *Fare Paid*, *Well Travelled* and *The Long Road*
  (`builtinAdvancements: false` removes it).
- Four statistics: journeys, blocks travelled, stops discovered and coaches placed.

### Integration (phase 2)

- **Java API and events** for other mods: list and find stops, discover and forget, start and cancel journeys; events
  before a journey starts (change the fare or the time, or refuse it), when it starts, completes and ends, before a
  stop is discovered, before a coach is placed or broken, and before a destination list is shown. The same names on
  every loader.
- **Cost tuning**: `dimensionCostMultipliers`, `crossDimensionCostMultiplier`, `crossDimensionDistance` and each stop's
  own multiplier.
- **Travel scenes from resource packs**: replace the road with your own scrolling layers or a single image, pick
  scenes by biome, biome tag or dimension, and loop an ambient sound. The server can set a default scene
  (`defaultScene`) and force a travel screen style on everyone (`forceClientTravelStyle`).
- **New sounds** a resource pack can replace: departure, arrival, the travel loop, discovery and placement.
- **Permission nodes** for every action (`stagecoach.place`, `travel`, `discover`, `break.own`, `break.any`,
  `rename.own`, `admin.manage`, `admin.commands`, `bypass.limit`, `bypass.cost`), through LuckPerms or any other
  permissions mod; without one, the defaults are what 1.1.0 did.

### Polish (phase 3)

- **Travel rules**: datapack files that lock destinations behind an advancement, an experience level, an item, a
  permission or a GameStages stage, or change their fare — with your own message on the greyed-out entry.
- **KubeJS**: `StagecoachEvents.preJourney(e => ...)` and the rest, plus a `Stagecoach` binding for the API.
- **Open Parties and Claims**: coaches can't be placed into a claim you couldn't build in, like FTB Chunks.
- **JourneyMap and Xaero's Minimap**: the stops you know appear as a "Stagecoach" waypoint set.
- **Export and import**: save a whole stop network to `config/stagecoach/exports/<name>.json` and build it again on
  another server or world.

### Every new setting

Server (`config/stagecoach.json`): `adminCommandLevel` (2), `autoNameFormat` (`"{biome} Stop"`),
`builtinAdvancements` (true), `crossDimensionCostMultiplier` (1.0), `crossDimensionDistance` (10000),
`defaultScene` (`"stagecoach:road"`), `dimensionCostMultipliers` ({}), `forceClientTravelStyle` (`"none"`; or
`"road"`, `"map"`, `"minimal"`), `generatedCoachesBreakable` (false), `generationWeightMultiplier` (1.0),
`maxGeneratedStopsPerStructure` (1), `naturalGeneration` (true).

Client (`config/stagecoach-client.json`): `discoveryEffects` (true), `discoverySound` (true), `placementEffects`
(true).

### Changed and fixed
- A controller placed with `/setblock` becomes a stop with its parts straight away, instead of waiting for whoever
  right-clicked it first.
- Mirrored structures with a coach in them come out whole.
- Coaches from 1.1.0 that had no owner (placed by a dispenser) are now ownerless server stops.

### What exists on which build
Every feature is on all fourteen builds, except where the other mod doesn't exist for that version:

| | 1.20.1 | 1.21.1 | 1.21.11 | 26.2 | 26.3 |
|---|---|---|---|---|---|
| Open Parties and Claims, JourneyMap, Xaero's Minimap | all | all | all | all | all |
| FTB Chunks | Fabric, Forge | Fabric, NeoForge | — | — | — |
| KubeJS | Fabric, Forge | NeoForge | — | — | — |
| GameStages (travel rules) | Forge | — | — | — | — |
| Jade, JEI, TerraFirmaCraft | as in 1.1.0 | as in 1.1.0 | as in 1.1.0 | as in 1.1.0 | as in 1.1.0 |

Permissions use fabric-permissions-api on Fabric (when a permissions mod is installed) and the built-in permission API
on Forge and NeoForge.

## 1.1.0 — bugfixes and a few additions

**Now on Minecraft 26.3** (Fabric, NeoForge and Forge) **and 1.21.11** (Fabric, NeoForge and Forge), plus Forge for
1.21.1 and 26.2. Also new: a map travel screen, longer routes in hops, item fares, TerraFirmaCraft recipes and lots
of settings.

**Update the server and every player to 1.1.0 together** — the network changed, so 1.0.x and 1.1.0 can't play
together (a mixed client and server either won't connect or will disconnect when a journey starts). Worlds,
coaches, discovered stops and settings carry over untouched; new settings are added to `config/stagecoach.json` with
their defaults the first time 1.1.0 starts.

### New Minecraft versions and loaders
- **Minecraft 26.3** — new on Fabric, NeoForge and Forge.
- **Minecraft 1.21.11** — new on Fabric, NeoForge and Forge.
- **Forge** for Minecraft 1.21.1 and 26.2, next to the Fabric and NeoForge builds already there.

That makes fourteen builds:

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.3 | ✔ new | ✔ new | ✔ new |
| 26.2 | ✔ | ✔ | ✔ new |
| 1.21.11 | ✔ new | ✔ new | ✔ new |
| 1.21.1 | ✔ | ✔ | ✔ new |
| 1.20.1 | ✔ | — | ✔ |

### A new travel screen
- **The road on a map.** While you travel, an old parchment map shows where you set off and where you're going as
  two banners, with your marker moving along a winding dotted road between them. Other stops you know show as faint
  dots (you can turn them off).
- **Smooth.** The journey now moves with real time instead of game ticks, so it no longer stutters or jumps when the
  server lags, and it stays in step with the server's own clock.
- **Prefer it plain?** The new *Simple travel screen* option shows only the sign, the progress bar and Cancel.

### Longer journeys: change coaches on the way
- New setting **`maxDistance`**: the longest single trip, in blocks. Stops farther than that are greyed out in the
  list ("too far for one trip"), so long routes are travelled in hops, changing coaches at stops in between —
  just like a real coach road. 0 (the default) means no limit, as before.

### Pay with items instead of XP
- New settings **`itemCostEnabled`** and **`costItem`**: journeys cost items instead of XP levels. `costItem` takes
  any item id — vanilla or from any mod, like `"minecraft:emerald"` or `"create:brass_ingot"` — or a tag with a `#`
  in front (`"#minecraft:logs"`: any log pays).
- The price works like the XP price: **`itemBaseCost`** + one more item every **`blocksPerItem`** blocks, never more
  than **`maxItemCost`** (defaults 1, 1000, 32).
- Items are taken from your inventory and off hand, and every refund (cancelling, a broken destination, a crash)
  gives back **the very items you paid**, enchantments and names included.
- A `costItem` that isn't an item (a typo, or a mod that isn't installed) falls back to XP and says so in the log.

### TerraFirmaCraft
- With TerraFirmaCraft installed (Forge 1.20.1 and NeoForge 1.21.1 — the versions TFC is out for), stagecoaches
  get TFC recipes: TFC lumber instead of planks, steel double sheets instead of diamond blocks, a gold sheet and a
  wooden chest. Crimson and warped coaches keep their vanilla recipes. `tfcRecipes: false` turns this off.

### Lots of new settings
Server (`config/stagecoach.json`, reload with **`/stagecoach reload`**):
- **`instantTravel`** — no time on the road: pay and step out at the destination.
- **`discoveryRequired`** — turn it off and every stop shows in everyone's list without discovering it first.
- **`maxCoachesPerPlayer`** — how many stagecoaches one player may own (ops are never limited).
- **`useRange`** — how close you must stand to a coach to use it (default 12 blocks).
- **`travelCooldownSeconds`** — a rest between journeys.
- **`cancelRefundPercent`** — how much of the fare comes back when you cancel on the road (every other early end
  always refunds in full). The abandon screen and the message say how much.
- **`creativeTravelsFree`** — turn it off and creative players pay like everyone else.
- **`opsCanManageAnyCoach`** — whether operators may rename and take down anyone's coach.
- **`blockedDimensions`** — dimensions where coaches can't be placed, e.g. `["minecraft:the_nether"]`.
- **`maxNameLength`** — the longest name a stop may have (up to 64).

Client (`config/stagecoach-client.json`, or **Options** from Mod Menu / the Forge and NeoForge mod list):
- **Simple travel screen**, **Other stops on the map**, **Placement preview**.

### Fixes
- **26.2: stagecoaches were black and purple** (the missing-texture pattern) and nothing seemed to work. GeckoLib 5.5
  looks for models in a new folder; the coach's model is there now.
- **26.2: the game could crash when you clicked Travel** with some mods installed (Arrow in the Knee, for one). The
  travel screen no longer uses a stand-in copy of your player, which is what those mods tripped over.
- The travel screen's stutter and jumps (see above).

## 1.0.3 — Forge, NeoForge, 1.21.1 and 26.2

Stagecoach now runs on five more Minecraft versions and loaders, each as its own download:

| Minecraft | Loader | Also needs |
|---|---|---|
| 1.20.1 | Forge 47+ | GeckoLib 4.8.4+ |
| 1.21.1 | Fabric (Loader 0.17+) | Fabric API, GeckoLib 4.9+ |
| 1.21.1 | NeoForge 21.1+ | GeckoLib 4.9+ |
| 26.2 | Fabric (Loader 0.19+) | Fabric API, GeckoLib 5.5+ |
| 26.2 | NeoForge 26.2+ | GeckoLib 5.5+ |

Every build has the same stagecoaches, recipes, road network and config as 1.0.2 on Fabric 1.20.1. The one
difference: the 26.2 builds have no FTB Chunks integration, because FTB Chunks isn't out for 26.2 yet.

Nothing changed for Fabric 1.20.1, so it stays on 1.0.2.

## 1.0.2 — first public release

Minecraft 1.20.1 · Fabric Loader 0.15+ · Fabric API · GeckoLib 4.8.4+ · needed on both client and server.

### Survival
- **Crafting recipe** for every wood: two planks of that wood, a chest, two leads, three blocks of diamond and a
  block of gold. Unlocked in the recipe book the first time you hold a block of diamond.
- **Placement preview:** hold a stagecoach and aim at the ground to see a see-through coach where it would go, green
  if it fits and red if it doesn't. Every blocked space is shaded red.
- **It tells you why it won't place:** the action bar names what's in the way (a block, a mob, another stagecoach),
  says when you're too close to the edge of the world, or says when part of the coach would stand on protected land.
- **Item tooltip** explaining what a stagecoach does.
- **Own creative tab**, "Stagecoaches", with all eleven woods.

### Compatibility
- **Jade:** looking at any part of a coach shows the coach itself, its name, its owner (or "Your stagecoach") and,
  if you haven't discovered it yet, a hint to right-click it.
- **JEI:** the recipes, plus an information page on every coach: placing, discovering, travelling, costs and breaking.
- **FTB Chunks:** a coach can't be placed if any part of it would stand in a chunk you aren't allowed to build in.
- **Sodium + Iris:** checked with both installed (no shader pack active). The coach, the placement preview and the
  travel screen all render correctly.
- **Mod Menu:** name, description, icon and licence.

### The road network (from the pre-release builds)
- Place and name a stop. Discover other people's stops by right-clicking them. Travel between any two you know for
  XP levels, riding the road behind a travel screen until you arrive.
- Crash-safe: quitting, a server stop, or a server crash mid-journey puts you back at the start with your levels
  and game mode restored.
- Owner-only breaking with blast protection (configurable), a combat lock, and a JSON config.

### Changes from the pre-release builds
- Every stagecoach now has a recipe (before: creative and commands only).
- Coaches moved from the Functional Blocks tab to their own tab.
