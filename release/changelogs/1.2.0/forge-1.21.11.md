## 1.2.0 — a coach stop in every village, and a toolkit for pack makers

**Villages now come with a coach stop.** With natural generation on (the default), every village gets exactly one
stagecoach, built in the wood of its own houses, and finding it feels like a moment: road dust, lantern embers and a
road-sign card. Underneath, 1.2.0 is the pack-maker update: admin commands, coaches you can save into your own
structures, advancements and statistics, a Java API with events, data-driven travel rules, travel
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

- **Travel rules**: datapack files that lock destinations behind an advancement, an experience level, an item or a
  permission, or change their fare — with your own message on the greyed-out entry.
- **Open Parties and Claims**: coaches can't be placed into a claim you couldn't build in.
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

### On this build (Forge 1.21.11)
Works with Open Parties and Claims, JourneyMap and Xaero's Minimap when they are installed; none of them is required.
