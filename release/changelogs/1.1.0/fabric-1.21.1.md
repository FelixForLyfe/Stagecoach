# Stagecoach 1.1.0 for Fabric 1.21.1

The big update: a smoother travel screen with two new looks, longer routes, item fares, lots of new settings.

**Update the server and every player to 1.1.0 together**: the network changed, so 1.1.0 and older versions can't play together. Worlds, coaches and settings carry over.

**Needs:** Fabric Loader 0.17+, Fabric API and GeckoLib 4.9+, on both the client and the server.

### The travel screen
- **Smoother.** The road and your walking figure move with real time instead of game ticks, so they don't stutter or
  jump when the server lags, and they stay in step with the server's own clock. The road stays the default.
- **New option: Simplified loading screen.** Instead of the road, an old parchment map shows where you set off and
  where you're going as two banners, with your marker moving along a winding dotted road between them. Other stops
  you know show as faint dots (you can turn them off).
- **New option: Minimal loading screen.** Only the sign, the progress bar and Cancel.

### Longer journeys: change coaches on the way
- **`maxDistance`**: the longest single trip, in blocks. Stops farther than that are greyed out in the list ("too far
  for one trip"), so long routes are travelled in hops, changing coaches at stops in between. 0 (the default) means
  no limit.

### Pay with items instead of XP
- **`itemCostEnabled`** and **`costItem`**: journeys cost items instead of XP levels. `costItem` takes any item id,
  vanilla or from any mod (`"minecraft:emerald"`, `"create:brass_ingot"`), or a tag with a `#` in front
  (`"#minecraft:logs"`: any log pays).
- The price works like the XP price: **`itemBaseCost`** + one more item every **`blocksPerItem`** blocks, never more
  than **`maxItemCost`** (defaults 1, 1000, 32).
- Items are taken from your inventory and off hand, and every refund gives back the very items you paid,
  enchantments and names included.
- A `costItem` that isn't an item falls back to XP and says so in the log.

### Settings
Server (`config/stagecoach.json`, reload with **`/stagecoach reload`**): `instantTravel` (no time on the road),
`discoveryRequired`, `maxCoachesPerPlayer`, `useRange`, `travelCooldownSeconds`, `cancelRefundPercent` (how much of
the fare comes back when you cancel), `creativeTravelsFree`, `opsCanManageAnyCoach`, `blockedDimensions` and
`maxNameLength`, next to the cost, speed and protection settings.

Client (`config/stagecoach-client.json`, or Stagecoach's **Config** button in Mod Menu): *Simplified loading
screen*, *Minimal loading screen*, *Other stops on the map*, *Placement preview*.

### Optional integrations
- **Jade** shows the coach, its name and owner, and whether you have discovered it.
- **JEI** shows the recipes and an information page on every coach.
- **FTB Chunks**: a coach can't be placed where you aren't allowed to build.
- **Mod Menu** opens the client options.
