![Stagecoach](https://media.forgecdn.net/attachments/1976/919/banner-png.png)

### "Hey you, you're finally awake..! You were caught crossing the border to skyrim."

A mod that brings the **travelling system** from **Skyrim** into Minecraft, in a vanilla friendly way, and a bit of
extra flavour added to it.

**Stagecoaches in eleven woods, and a road network between them.** Every village has a coach stop waiting to be
found; place your own coach and name the stop. Discover the stops in villages and the ones other players have built,
then pay a fare (a few XP levels, or items if the server prefers) to ride the road to any of them. While you travel,
the road rolls past with your own figure walking along it, and a countdown to your arrival.

**[Player wiki](https://felixforlyfe.github.io/Stagecoach/players/)** · **[Source code on GitHub](https://github.com/FelixForLyfe/Stagecoach)** · **[Pack developer guide](https://felixforlyfe.github.io/Stagecoach/pack/)** · **[Modder guide](https://felixforlyfe.github.io/Stagecoach/modders/)** · **[Report a bug](https://github.com/FelixForLyfe/Stagecoach/issues)**

"Uhm.. but isn't that just Waystones?"

## How it's different from Waystones

Waystones is about teleporting. Stagecoach is about the road.

- **Travel takes time.** Waystones teleports you. A stagecoach journey takes time: 75 blocks a second, up to 10
  minutes (by default). You ride it out behind a travel screen, and you can cancel on the way to get your fare back.
- **You set off from a coach, and nowhere else.** You have to stand within 12 blocks of a stagecoach to leave (by
  default). There are no scrolls, stones or inventory buttons for travelling from wherever you happen to be.
- **Long roads are travelled in hops** if the server wants: with a longest-trip limit, far stops are reached by
  changing coaches on the way.
- **Stops are places.** Every village has one coach stop, built in the village's own wood and named to fit it
  ("Frostholm Crossing", "The Lucky Badger Inn"). Every other stop is built by a player, named by whoever placed it,
  and by default only its owner (or an operator) can take it down.
- **A network is earned.** At 27 diamonds a coach, a road network is something a server builds up over time.
- **No escaping a fight.** You can't set off within 10 seconds of being hit (by default).

## How it works

1. **Craft a stagecoach** in any of eleven woods: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry,
   bamboo, crimson or warped.
2. **Place it.** It needs a clear space 3 wide, 3 tall and 6 long, and it faces away from you. Hold one and aim at
   the ground to see a see-through preview: green means it fits, red means it doesn't, and anything in the way is
   shaded red. If it still won't place, the action bar tells you why. When you place it, you name the stop.
3. **Discover other stops** by right-clicking them: in villages, or the ones other players built. Finding one kicks up
   dust and lantern embers, sounds the coachman's horn and shows the stop's name on a little road sign.
4. **Travel.** Right-click a stop you know to open the destination list. It shows each stop's distance, cost and
   travel time, and you can search it by name. Pick one and you're on the road.

![Placement preview: something in the way](https://media.forgecdn.net/attachments/1976/852/preview_blocked-png.png) ![Placement preview: it fits](https://media.forgecdn.net/attachments/1976/853/preview_fits-png.png)

## The journey

- You pay the fare up front: **1 level, plus 1 for every 5,000 blocks or part of that, up to 10 levels** (by
  default). Creative players travel free.
- A server can charge **items instead of XP**: any item from any mod (emeralds, coins, a modded ingot) or a whole tag
  ("any log"). Every refund gives back the very items you paid.
- The journey takes time: **75 blocks a second, at least 5 seconds and at most 10 minutes** (by default). While you
  travel, the road rolls past with your own figure walking along the progress bar. Each player can switch to a
  parchment map of the route instead, or to a minimal screen.
- While you're on the road, you stay safe where you set off. **Cancel at any time** to get your fare back.
- You arrive on safe ground in front of the destination coach, in your own game mode.
- You can't set off within 10 seconds of being hit (by default).

![On the road: the travel screen](https://media.forgecdn.net/attachments/1976/854/travel-png.png)

## Built to be trusted on a server

- **Crash-safe.** If you quit, the server stops, or the server crashes mid-journey, you come back at the start with
  your fare and your game mode restored. Every journey is written to disk before anything about you changes.
- **Server-authoritative.** The server checks every request: distance, cost, discovery and permissions.
- **Owner-only breaking** (by default). Only the owner or an operator can take a coach down, and explosions can't.
- **Solid.** Every block of the coach has real collision, generated from the model itself.

## Compatibility

| Mod | What you get |
|---|---|
| **Jade** | Look at any part of a coach to see its name, its owner, and whether you've discovered it. |
| **JEI** | The recipes, and an information page on every coach. |
| **FTB Chunks** | A coach can't be placed with any part in a chunk you aren't allowed to build in. The action bar says why. (1.20.1, and Fabric and NeoForge 1.21.1: FTB Chunks isn't out for 1.21.11 or 26.x.) |
| **Open Parties and Claims** | The same claim check as FTB Chunks. (Every build.) |
| **JourneyMap**, **Xaero's Minimap** | The stops you know show up as a "Stagecoach" waypoint set. (Every build.) |
| **Village mods** | Repurposed Structures, ChoiceTheorem's Overhauled Village, Towns and Towers, Integrated Villages and Dungeons and Taverns villages get a coach stop too, in their own wood. |
| **LuckPerms** (or any permissions mod) | A permission node for every action, from placing a coach to using the admin commands. |
| **KubeJS** | Script events and the API from KubeJS. (1.20.1, and NeoForge 1.21.1: KubeJS isn't out for the others.) |
| **TerraFirmaCraft** | TFC recipes: lumber, steel double sheets, a gold sheet and a wooden chest. (Forge 1.20.1 and NeoForge 1.21.1, the versions TFC is out for.) |
| **Sodium + Iris** | Checked with both installed on Fabric 1.20.1 (no shader pack active). |
| **Mod Menu** | Name, description, icon and the client options (Fabric). |

All of these are optional. Stagecoach doesn't need any of them. Jade and JEI aren't out for Forge on 1.21.11 and 26.x,
so those builds go without them, and Forge 1.21.1 has JEI but not Jade.

## Recipe

Per coach, shaped:

| | | |
|---|---|---|
| Planks (of the coach's wood) | Chest | Planks |
| Lead | **Block of Diamond** | Lead |
| **Block of Diamond** | Block of Gold | **Block of Diamond** |

It's a late-game build: 27 diamonds and 9 gold ingots per coach. The recipe book unlocks it the first time you hold a
block of diamond.

## Installation

Download the file for your Minecraft version and loader, and put it in your `mods` folder with what it needs:

| Minecraft | Loader | Also install |
|---|---|---|
| 1.20.1 | Fabric (Loader 0.15+) | Fabric API, GeckoLib 4.8.4+ |
| 1.20.1 | Forge 47+ | GeckoLib 4.8.4+ |
| 1.21.1 | Fabric (Loader 0.17+) | Fabric API, GeckoLib 4.9+ |
| 1.21.1 | NeoForge 21.1+ | GeckoLib 4.9+ |
| 1.21.1 | Forge 52+ | GeckoLib 4.9+ |
| 1.21.11 | Fabric (Loader 0.18+) | Fabric API, GeckoLib 5.4+ |
| 1.21.11 | NeoForge 21.11+ | GeckoLib 5.4+ |
| 1.21.11 | Forge 61+ | GeckoLib 5.4+ |
| 26.2 | Fabric (Loader 0.19+) | Fabric API, GeckoLib 5.5+ |
| 26.2 | NeoForge 26.2+ | GeckoLib 5.5+ |
| 26.2 | Forge 65+ | GeckoLib 5.5+ |
| 26.3 | Fabric (Loader 0.19+) | Fabric API, GeckoLib 5.5+ |
| 26.3 | NeoForge 26.3+ | GeckoLib 5.5+ |
| 26.3 | Forge 66+ | GeckoLib 5.5+ |

On a server, install the same files on the server. Every player who joins needs them too.

## Configuration

`config/stagecoach.json` is created on first launch, and `/stagecoach reload` (operators) reads it again. A missing
setting uses its default; an invalid one also falls back to its default, with a warning in the log.

| Setting | Default | What it does |
|---|---|---|
| `baseCost` | 1 | Levels every journey costs |
| `blocksPerLevel` | 5000 | One more level for every this many blocks (or part of that) |
| `maxCost` | 10 | The most a journey can cost |
| `itemCostEnabled` | false | Charge items instead of XP levels |
| `costItem` | `"minecraft:emerald"` | The item: any item id from any mod, or a tag like `"#minecraft:logs"` |
| `itemBaseCost` | 1 | Items every journey costs |
| `blocksPerItem` | 1000 | One more item for every this many blocks (or part of that) |
| `maxItemCost` | 32 | The most items a journey can cost |
| `blocksPerSecond` | 75 | Travel speed |
| `minTravelSeconds` | 5 | Shortest journey |
| `maxTravelSeconds` | 600 | Longest journey |
| `maxDistance` | 0 | The longest single trip in blocks; farther stops need a change of coach (0 = no limit) |
| `instantTravel` | false | No time on the road: pay and arrive |
| `allowCrossDimension` | false | Allow journeys between dimensions |
| `useRange` | 12 | How close you must stand to a coach to use it |
| `combatLockSeconds` | 10 | How long after being hit you can't set off (0 turns it off) |
| `travelCooldownSeconds` | 0 | A rest after arriving before the next journey |
| `cancelRefundPercent` | 100 | How much of the fare comes back when you cancel on the road |
| `creativeTravelsFree` | true | Creative players travel free |
| `discoveryRequired` | true | Stops must be discovered before they show in your list |
| `onlyOwnerCanBreak` | true | Only the owner or an operator can break a coach, and explosions can't |
| `opsCanManageAnyCoach` | true | Operators may rename and take down anyone's coach |
| `maxCoachesPerPlayer` | 0 | How many coaches one player may own (0 = no limit; operators are never limited) |
| `maxNameLength` | 24 | The longest name a stop may have (up to 64) |
| `blockedDimensions` | `[]` | Dimensions where coaches can't be placed, like `["minecraft:the_nether"]` |
| `tfcRecipes` | true | Use the TerraFirmaCraft recipes when TFC is installed |
| `naturalGeneration` | true | A coach stop in every village (`false`: no coaches generate) |
| `maxGeneratedStopsPerStructure` | 1 | Stops per village |
| `generatedCoachesBreakable` | false | Whether village coaches can be broken |
| `autoNameFormat` | `"{biome} Stop"` | The name a coach gets when nothing else names it |
| `dimensionCostMultipliers` | `{}` | A fare multiplier per dimension, like `{"minecraft:the_nether": 2.0}` |
| `crossDimensionCostMultiplier` | 1.0 | A fare multiplier for journeys between dimensions |
| `crossDimensionDistance` | 10000 | The distance a journey between dimensions counts as |
| `adminCommandLevel` | 2 | The operator level the admin commands need |
| `builtinAdvancements` | true | The Stagecoach advancement tab |
| `defaultScene` | `"stagecoach:road"` | The travel scene a stop gets when nothing else picks one |
| `forceClientTravelStyle` | `"none"` | Show everyone the same travel screen: `"road"`, `"map"` or `"minimal"` |
| `generationWeightMultiplier` | 1.0 | How often taverns get a stop |

Each player also has `config/stagecoach-client.json`, or the **Config** button in Mod Menu or the Mods list: a
simplified loading screen (a map of the route instead of the road), a minimal one (only the sign, the bar and
Cancel), whether other stops show on the map, the placement preview, and the discovery and placement effects.

## For pack makers and server owners

- **Admin commands**: create, list, rename, move ownership of, flag (public, hidden, locked, free), price and remove
  stops; send players on journeys; export a whole network to a file and import it on another server.
- **Coaches in your own structures**: save a coach into a structure template and it becomes a stop wherever the
  template generates, with the name, flags, theme and wood you set.
- **Datapacks**: travel rules that lock destinations behind an advancement, a level, an item or a permission; themes,
  name pools and structure injections for your own villages and towns.
- **Resource packs**: your own travel scenes (layers or a single image), chosen by biome or dimension, and sounds.
- **Advancement triggers and statistics** for quests (FTB Quests can use them as advancement tasks).
- **A Java API and events**, the same on every loader.

The full reference is the pack developer guide and the modder guide:
**https://felixforlyfe.github.io/Stagecoach/**

## About this mod

Solo project by Feelix. I'm a designer first, not a full-time coder. Every mechanic, idea, and texture in this mod is
my own. The code was generated by AI using Linen, a framework I built for steering AI code generation. It's
the structure, rules, and reference codebase that make the output fit my design. The promo art is also AI-made.

AI wrote code, but it didn't make this mod. Designing the mechanics, building Linen, testing, fixing, and tuning
everything until it felt right took many hours of my own work. Give it a try and judge it by how it plays. If you
find a bug or have an idea, drop a comment or open an issue on GitHub. Feedback from players is what shapes the next
update.

Rendered with [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib).

## Licence

Open source, under the MIT licence with one condition: if you share Stagecoach or something built on it, credit me
(Feelix) and link to this page. Use it in your game, on your server and in any modpack. I'd prefer packs to download
Stagecoach and its addons from their own pages rather than bundle them, but that's a wish, not a rule.

Source code: **https://github.com/FelixForLyfe/Stagecoach**
