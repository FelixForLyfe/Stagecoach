# Stagecoach 1.0.3 for NeoForge 26.2

The first Stagecoach release for NeoForge 26.2. It has the same stagecoaches, recipes, road network and config as
the Fabric 1.20.1 version.

**Needs:** NeoForge 26.2+ and GeckoLib 5.5+, on both the client and the server.

### What's in it
- Stagecoaches in eleven woods. Each one is crafted from two planks of its wood, a chest, two leads, three blocks of
  diamond and a block of gold. The recipe book unlocks it the first time you hold a block of diamond.
- A see-through placement preview: green if the coach fits, red if it doesn't. If it won't place, the action bar
  says what's in the way.
- Place and name a stop, discover other players' stops by right-clicking them, and pay XP levels to ride the road to
  any stop you know. The journey takes time, and you can cancel it to get your levels back.
- Crash-safe: quitting, a server stop or a server crash mid-journey puts you back at the start with your levels and
  game mode restored.
- Owner-only breaking with blast protection (configurable), a combat lock, and a JSON config (`config/stagecoach.json`).
- Its own creative tab, "Stagecoaches".

### Optional integrations
Jade and JEI. FTB Chunks isn't out for 26.2 yet, so this build doesn't check claims when you place a coach.
