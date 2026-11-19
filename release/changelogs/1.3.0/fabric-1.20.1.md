## 1.3.0 — global stops, in-game settings and per-player settings

**Update the server and every player to 1.3.0 together**: the destination list carries new information, so 1.2.x and
1.3.0 can't play together. Worlds and configs carry over unchanged. A stop that was `public` before is a global stop
now (they are the same thing).

**Global stops**
- A global stop is one every player can travel to from any stagecoach, without discovering it first, like a global
  waystone. Old stops and new ones can both be made global.
- Global stops can't be discovered: right-clicking one just opens the destination list, and it never joins anyone's
  discovered stops. Turn a stop back into a normal one and only the players who had discovered it before still know it.
- Operators turn it on or off with the new button at the top left of the destination list (an eye of ender when the
  stop is global, an ender pearl when it isn't). The list marks global stops with an eye of ender too.
- Ordinary players can't, unless the server allows it: with `anyoneCanMakeGlobal` (or the new permission node
  `stagecoach.global`) owners can make their own stops global.
- `/stagecoach stop flag global <stop> true|false` does the same from a command (`public` still works).

**Settings in game**
- `/stagecoach config` lists every server setting; `/stagecoach config <setting> <value>` changes one, saves it to
  `config/stagecoach.json` and applies it at once. A bad value is refused with the reason, and the file is left alone.
  For example, `/stagecoach config anyoneCanMakeGlobal true` lets every player make their own stops global.

**Per-player settings and permissions**
- Every setting can now be different for a group or a player through your permissions mod: the option is the
  setting's name in snake case, e.g. `stagecoach.max_coaches_per_player`, `stagecoach.max_cost` or
  `stagecoach.travel_cooldown_seconds`. With LuckPerms they are meta values
  (`/lp group vip meta set stagecoach.max_cost 3`); fares, travel time, distance limits, cooldowns, refunds, the coach
  limit and the rest follow the player who is travelling or placing.
- **FTB Ranks** is supported: the nodes and the per-player settings can be set in `ranks.snbt`, and FTB Ranks answers
  before any other permission system.

**Under the hood**
- The deprecation warnings in every build are gone: calls the game has a successor for use it (the chunk-loaded check,
  the block atlas, the advancement trigger registry on Fabric 1.20.1), and the rest are marked as intended. Nothing
  changes in play.

### On this build (Fabric 1.20.1)
Works with Jade, JEI, FTB Chunks, Open Parties and Claims, FTB Ranks, JourneyMap, Xaero's Minimap, KubeJS and Mod Menu when they are installed; none of them is required.
