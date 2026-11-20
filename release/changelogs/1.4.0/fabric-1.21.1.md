## 1.4.0 — horses, companions, roads and bandits

Worlds and configs carry over unchanged; the new settings are written into `config/stagecoach.json` with their
defaults. A 1.3.0 player can still join a 1.4.0 server.

**Fixes**
- Coaches no longer vanish at the edge of the screen or while you stand on one, on Forge and NeoForge. Those loaders
  only drew a coach while its middle block was on screen; Fabric
  was never affected.
- JourneyMap no longer piles up a new copy of every stop's waypoint each time someone uses a coach. The old copies
  are cleared the next time the list updates.
- The map and minimal travel screens now cover the whole screen, so the world (and the mobs walking past) no longer
  shows around the edges.
- Village stops in villages that don't level the ground under their houses no longer hang in the air: the ground
  under the stop's path is filled in down to the terrain.

**Hitch your horses**
- Right-click a coach with a horse (or anything else) on a lead to tie it to the coach, the same as tying it to a
  fence. Sneak and right-click with an empty hand to take the leads back.

**Companions travel with you** (`companionsTravel`, on by default)
- Mobs on your lead, your own pets that aren't sitting, and any animal standing on the coach arrive with you.
  `companionRange` (10 blocks) sets how far away they may be. Between dimensions they stay behind.

**Roads** (`requireRoad`, off by default)
- With it on, you can only travel between stops a road joins: an unbroken line of dirt paths, gravel, cobblestone,
  stone bricks, planks and the like, starting within 8 blocks of each coach. Pack makers choose the road blocks with
  the `stagecoach:road_blocks` block tag.

**Bandits** (`ambushChancePercent`, off by default)
- A journey can be stopped by bandits part-way. You are set down on the road with the fare for the rest of the
  journey returned, and the bandits attack. Choose which mobs come (`ambushMobs`, modded ones too), how many
  (`ambushMobCount`) and the shortest trip that can be ambushed (`ambushMinDistance`). Never in peaceful or creative.

**Antique Atlas** (Fabric 1.20.1 and 1.21.1)
- The stops you know show on your Antique Atlas as stagecoach markers.

### On this build (Fabric 1.21.1)
Works with Jade, JEI, FTB Chunks, Open Parties and Claims, FTB Ranks, JourneyMap, Xaero's Minimap, Antique Atlas and Mod Menu when they are installed; none of them is required.
