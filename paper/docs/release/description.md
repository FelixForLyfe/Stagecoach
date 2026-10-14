### "Hey you, you're finally awake..! You were caught crossing the border to skyrim."

A plugin that brings the **travelling system** from **Skyrim** to your Paper server, in a vanilla friendly way.
Players join with a plain Minecraft 1.21.11 client, no mods: the plugin sends them a small resource pack that
gives the coaches, their icons and the travel screen their look.

**Stagecoaches in eleven woods, and a road network between them.** Place a coach and name the stop. Discover the
stops other players have built, then pay a fare (a few XP levels, or items if you prefer) to ride the road to any of
them. While you travel, a scene of hills, trees and fence posts rolls past, with a countdown to your arrival.

"Uhm.. but isn't that just a warp plugin?"

## How it's different from warps and waystones

Warps are about teleporting. Stagecoach is about the road.

- **Travel takes time.** A journey takes time: 75 blocks a second, up to 10 minutes (by default). You ride it out
  behind a travel screen, and you can cancel on the way to get your fare back.
- **You set off from a coach, and nowhere else.** You have to stand within 12 blocks of a stagecoach to leave (by
  default). There are no commands for travelling from wherever you happen to be.
- **Long roads are travelled in hops** if you want: with a longest-trip limit, far stops are reached by changing
  coaches on the way.
- **Every stop is built by a player.** Each one is named by whoever placed it, and by default only its owner (or an
  admin) can take it down.
- **A network is earned.** At 27 diamonds a coach, a road network is something a server builds up over time.
- **No escaping a fight.** You can't set off within 10 seconds of being hit (by default).

## How it works

1. **Craft a stagecoach** in any of eleven woods: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry,
   bamboo, crimson or warped.
2. **Place it.** It needs a clear space 3 wide, 3 tall and 6 long, and it faces away from you. Hold one and aim at
   the ground to see a see-through coach where it would go: green means it fits, red means it doesn't, and anything
   in the way is shaded red. If it still won't place, the action bar tells you why. When you place it, you name the
   stop.
3. **Discover other stops** by right-clicking them.
4. **Travel.** Right-click a stop you know to open the destination list. It shows each stop's distance, cost and
   travel time, and you can search it by name. Pick one, confirm, and you're on the road.

## The journey

- You pay the fare up front: **1 level, plus 1 for every 5,000 blocks or part of that, up to 10 levels** (by
  default). Creative players travel free.
- You can charge **items instead of XP**: emeralds, any item, or a whole tag ("any log"). Every refund gives back the
  very items paid.
- The journey takes time: **75 blocks a second, at least 5 seconds and at most 10 minutes** (by default).
- While you're on the road, you stay safe where you set off, watching the road roll by with a countdown and a
  progress bar. **Cancel at any time** to get your fare back.
- You arrive on safe ground in front of the destination coach, in your own game mode.

## Built to be trusted on a server

- **Crash-safe.** If a player quits, the server stops, or the server crashes mid-journey, they come back at the start
  with their fare and their game mode restored. Every journey is written to disk before anything about the player
  changes, and a copy travels in the player's own save file.
- **Server-side checks.** Every click is checked again on the server: distance, cost, discovery and permissions.
- **Respects land protection.** Placing a coach is announced like placing blocks, one for every block of the coach,
  so a plugin that stops players building somewhere stops stagecoaches there too.
- **Owner-only breaking** (by default). Only the owner or an admin can take a coach down, and explosions, pistons and
  flowing water can't touch it.

## Recipe

Per coach, shaped:

| | | |
|---|---|---|
| Planks (of the coach's wood) | Chest | Planks |
| Lead | **Block of Diamond** | Lead |
| **Block of Diamond** | Block of Gold | **Block of Diamond** |

It's a late-game build: 27 diamonds and 9 gold ingots per coach. The recipe book unlocks it the first time a player
holds a block of diamond. Turn crafting off with `craftable: false` to hand coaches out with `/stagecoach give` only.

## Installation

Put the plugin jar in your server's `plugins` folder and restart.

| Server | Minecraft | Java |
|---|---|---|
| Paper, or a fork of it such as Purpur | 1.21.11 | 21 |

Plain Spigot isn't supported: Stagecoach uses Paper's screens and resource-pack features.

**The resource pack.** Players need Stagecoach's small resource pack (about 1.7 MB) to see the coaches. The plugin
serves it itself on **port 8163** and offers it to every player as they join, next to your server's own pack if you
have one. Open that port like your game port. If your host can't open another port, either:

- upload `plugins/Stagecoach/stagecoach-pack.zip` anywhere that gives a direct download link and put the link in
  `resourcePack.url`, or
- add Stagecoach to **your own** resource pack with the **Stagecoach Pack Merger** (a separate download on this page:
  double-click it, pick your pack, done), use the new zip as your server's pack, and set `resourcePack.send: false`.
  Run the merger again after updating Stagecoach.

## Commands and permissions

| Command | Permission | What it does |
|---|---|---|
| `/stagecoach` (or `/coach`) | | How it works |
| `/stagecoach cancel` | | Abandon your journey (asks first) |
| `/stagecoach give <player> <wood> [amount]` | `stagecoach.admin` | Give stagecoaches |
| `/stagecoach list [page]` | `stagecoach.admin` | Every stagecoach on the server |
| `/stagecoach remove <id>` | `stagecoach.admin` | Take a stagecoach down from anywhere |
| `/stagecoach reload` | `stagecoach.admin` | Reload `config.yml` and `messages.yml` |

| Permission | Default | Allows |
|---|---|---|
| `stagecoach.use` | everyone | Discovering, the destination list, travelling |
| `stagecoach.place` | everyone | Placing stagecoaches |
| `stagecoach.admin` | operators | Renaming and taking down anyone's coach, and the admin commands |

## Configuration

`plugins/Stagecoach/config.yml` is created on first start, with a comment on every setting. A missing setting uses
its default; an invalid one also falls back to its default, with a warning in the console. Every message is in
`messages.yml`, ready to translate or recolour.

| Setting | Default | What it does |
|---|---|---|
| `baseCost` | 1 | Levels every journey costs |
| `blocksPerLevel` | 5000 | One more level for every this many blocks (or part of that) |
| `maxCost` | 10 | The most a journey can cost |
| `itemCostEnabled` | false | Charge items instead of XP levels |
| `costItem` | `"minecraft:emerald"` | The item, or a tag like `"#minecraft:logs"` |
| `itemBaseCost` / `blocksPerItem` / `maxItemCost` | 1 / 1000 / 32 | The item fare, like the XP one |
| `creativeTravelsFree` | true | Creative players travel free |
| `cancelRefundPercent` | 100 | How much of the fare comes back when a traveller cancels |
| `blocksPerSecond` | 75 | Travel speed |
| `minTravelSeconds` | 5 | Shortest journey |
| `maxTravelSeconds` | 600 | Longest journey |
| `maxDistance` | 0 | The longest single trip; farther stops need a change of coach (0 = no limit) |
| `instantTravel` | false | No time on the road: pay and arrive |
| `allowCrossWorld` | false | Allow journeys between worlds (the Nether and the End count as worlds) |
| `useRange` | 12 | How close a player must stand to a coach to use it |
| `combatLockSeconds` | 10 | How long after being hit you can't set off (0 turns it off) |
| `travelCooldownSeconds` | 0 | A rest after arriving before the next journey |
| `discoveryRequired` | true | Stops must be discovered before they show in a player's list |
| `onlyOwnerCanBreak` | true | Only the owner or an admin can take a coach down |
| `maxCoachesPerPlayer` | 0 | How many coaches one player may own (0 = no limit; admins are never limited) |
| `maxNameLength` | 24 | The longest name a stop may have (up to 64) |
| `blockedWorlds` | `[]` | Worlds where coaches can't be placed, by name or key |
| `placementPreview` | true | Show the see-through coach while holding one |
| `craftable` | true | Whether coaches can be crafted |
| `resourcePack.send` | true | Send Stagecoach's pack to players (false: your own pack carries it) |
| `resourcePack.required` | true | Players must accept it to join |
| `resourcePack.port` | 8163 | The port the plugin serves the pack on |
| `resourcePack.host` | (blank) | The address players download it from; blank = the address they joined with |
| `resourcePack.url` | (blank) | A download link you host yourself instead |

## Also a mod

Stagecoach started as a mod for Fabric, Forge and NeoForge. This plugin is the same road network for servers where
players join without mods.

## About this plugin

Solo project by Feelix. I'm a designer first, not a full-time coder. Every mechanic and idea in this plugin is my own.
The code was generated by AI.

AI wrote code, but it didn't make this plugin. Designing the mechanics, testing, fixing, and tuning everything until
it felt right took many hours of my own work. Give it a try and judge it by how it plays. If you find a bug or have an
idea, drop a comment. Feedback from players is what shapes the next update.

## Licence

All Rights Reserved. You may run Stagecoach on any server you operate, and add its resource pack to your server's
own pack with the Pack Merger. See `LICENSE` in the download for the full terms.
