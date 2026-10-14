# Stagecoach 1.1.0 for Fabric 26.3

The first Stagecoach release for Fabric 26.3, with everything the other versions have.

**Needs:** Fabric Loader 0.19+, Fabric API and GeckoLib 5.5+, on both the client and the server.

### What it does
- Stagecoaches in eleven woods. Each one is crafted from two planks of its wood, a chest, two leads, three blocks of
  diamond and a block of gold; the recipe book unlocks it the first time you hold a block of diamond.
- A see-through placement preview: green if the coach fits, red if it doesn't. If it won't place, the action bar
  says what's in the way.
- Place and name a stop, discover other players' stops by right-clicking them, and pay a fare (XP levels, or items)
  to ride the road to any stop you know. The journey takes time, and you can cancel it to get your fare back.
- Crash-safe: quitting, a server stop or a server crash mid-journey puts you back at the start with your fare and
  game mode restored.
- Owner-only breaking with blast protection, a combat lock, and its own creative tab, "Stagecoaches".

### The travel screen
- While you travel, the road rolls past - hills, trees, fence posts and signposts under a sky that follows the
  world's time of day - with your own figure walking along the progress bar, under a sign with the destination and
  the time left.
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
- **Mod Menu** opens the client options.

FTB Chunks isn't out for 26.3, so this build doesn't check claims when you place a coach.
