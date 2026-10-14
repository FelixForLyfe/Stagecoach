# Changelog — Stagecoach (Paper plugin)

## 1.1.0 — first release

Paper or Purpur 1.21.11 · Java 21 · players join with a plain vanilla client (the plugin sends them a small resource
pack). Same rules and settings as the Stagecoach mod 1.1.0.

The Stagecoach road network as a server plugin:

- **Eleven woods** of stagecoach, the same model and textures as the Stagecoach mod. Solid: it stands in the world as
  barrier blocks, so explosions, pistons and water can't touch it.
- **Crafting recipe** (two planks, a chest, two leads, three blocks of diamond, a block of gold), unlocked in the
  recipe book the first time you hold a block of diamond. `craftable: false` turns it off.
- **Placement preview:** hold a coach to see a see-through coach where it would go, green if it fits and red if it
  doesn't, with every blocked space shaded red. The action bar says why a coach won't place.
- **Naming screen** when you place a coach; rename it from its destination list.
- **Discover** other players' stops by right-clicking them.
- **Destination list**: every stop you know, nearest first, with travel time and cost, searchable by name. Pick one
  and confirm to set off.
- **Travel screen**: the road rolls by with a countdown and a progress bar. Cancel any time to get your fare back.
- **Crash-safe**: quitting, stopping or crashing the server mid-journey puts you back where you set off, with your
  fare and game mode.
- **Respects land protection**: placing a coach is announced like placing blocks.
- **The resource pack** is served by the plugin itself (port 8163) and offered as players join; or add it to your own
  server pack with the **Stagecoach Pack Merger**, a separate download.
- **Commands** for admins: give, list, remove, reload. Every message is in `messages.yml`.

Server settings (`config.yml`, all off or at the mod's defaults until you change them):

- **Item fares**: `itemCostEnabled` + `costItem` — any item id, or a `#tag` — with `itemBaseCost`, `blocksPerItem`,
  `maxItemCost`. Refunds give back the very items paid. An id that isn't an item falls back to XP and says so.
- **Longer routes in hops**: `maxDistance` greys out stops beyond one trip; travellers change coaches on the way.
- **`instantTravel`**: no time on the road.
- **`travelCooldownSeconds`**, **`cancelRefundPercent`**, **`creativeTravelsFree`**, **`useRange`**.
- **`discoveryRequired`** (off: every stop is listed at once), **`maxCoachesPerPlayer`**, **`maxNameLength`**,
  **`blockedWorlds`**.
