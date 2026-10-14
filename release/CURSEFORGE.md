# Uploading Stagecoach to CurseForge

Everything in this folder (`release/`), in the order the CurseForge author console asks for it.

**Where things stand (2026-10-14):** the project has 1.0.2 (Fabric 1.20.1) and the 1.0.3 files. 1.1.0 was built
(fourteen files, `changelogs/1.1.0/`) but never uploaded. **1.2.0 is fourteen files**, one per Minecraft version and
loader, all built from the same release.

⚠ Decide before uploading: either upload 1.1.0 first and then 1.2.0, or skip 1.1.0. If you skip it, the players who
update from 1.0.3 get both releases' changes at once, so paste `CHANGELOG.md`'s 1.2.0 *and* 1.1.0 sections (or the
two per-file changelogs one after the other) into each 1.2.0 file.

## 1. Upload the fourteen 1.2.0 files

On the project's **Files** tab, upload each jar as its own file (**never** the `-sources` jars). For every one:
Release type **Release**, Environment **Client and Server**, Changelog type **Markdown**, and paste that file's own
changelog from `changelogs/1.2.0/` (each one stands on its own and lists only what exists on that build;
`../CHANGELOG.md` has the combined one). All fourteen jars are in `minecraftmodding/Stagecoach Master/`
(`python tools/collect_jars.py` refreshes it after a build).

| File | Display name | Game version | Loader | Java | Required | Optional |
|---|---|---|---|---|---|---|
| `Stagecoach-fabric-1.20.1-1.2.0.jar` | Stagecoach 1.2.0 (Fabric 1.20.1) | 1.20.1 | Fabric | 17 | Fabric API · GeckoLib | Jade · JEI · FTB Chunks · OPAC · JourneyMap · Xaero's Minimap · KubeJS · Mod Menu |
| `Stagecoach-forge-1.20.1-1.2.0.jar` | Stagecoach 1.2.0 (Forge 1.20.1) | 1.20.1 | Forge | 17 | GeckoLib | Jade · JEI · FTB Chunks · OPAC · JourneyMap · Xaero's Minimap · KubeJS · Game Stages · TerraFirmaCraft |
| `Stagecoach-fabric-1.21.1-1.2.0.jar` | Stagecoach 1.2.0 (Fabric 1.21.1) | 1.21.1 | Fabric | 21 | Fabric API · GeckoLib | Jade · JEI · FTB Chunks · OPAC · JourneyMap · Xaero's Minimap · Mod Menu |
| `Stagecoach-neoforge-1.21.1-1.2.0.jar` | Stagecoach 1.2.0 (NeoForge 1.21.1) | 1.21.1 | NeoForge | 21 | GeckoLib | Jade · JEI · FTB Chunks · OPAC · JourneyMap · Xaero's Minimap · KubeJS · TerraFirmaCraft |
| `Stagecoach-forge-1.21.1-1.2.0.jar` | Stagecoach 1.2.0 (Forge 1.21.1) | 1.21.1 | Forge | 21 | GeckoLib | JEI · OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-fabric-1.21.11-1.2.0.jar` | Stagecoach 1.2.0 (Fabric 1.21.11) | 1.21.11 | Fabric | 21 | Fabric API · GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap · Mod Menu |
| `Stagecoach-neoforge-1.21.11-1.2.0.jar` | Stagecoach 1.2.0 (NeoForge 1.21.11) | 1.21.11 | NeoForge | 21 | GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-forge-1.21.11-1.2.0.jar` | Stagecoach 1.2.0 (Forge 1.21.11) | 1.21.11 | Forge | 21 | GeckoLib | OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-fabric-26.2-1.2.0.jar` | Stagecoach 1.2.0 (Fabric 26.2) | 26.2 | Fabric | 25 | Fabric API · GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap · Mod Menu |
| `Stagecoach-neoforge-26.2-1.2.0.jar` | Stagecoach 1.2.0 (NeoForge 26.2) | 26.2 | NeoForge | 25 | GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-forge-26.2-1.2.0.jar` | Stagecoach 1.2.0 (Forge 26.2) | 26.2 | Forge | 25 | GeckoLib | OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-fabric-26.3-1.2.0.jar` | Stagecoach 1.2.0 (Fabric 26.3) | 26.3 | Fabric | 25 | Fabric API · GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap · Mod Menu |
| `Stagecoach-neoforge-26.3-1.2.0.jar` | Stagecoach 1.2.0 (NeoForge 26.3) | 26.3 | NeoForge | 25 | GeckoLib | Jade · JEI · OPAC · JourneyMap · Xaero's Minimap |
| `Stagecoach-forge-26.3-1.2.0.jar` | Stagecoach 1.2.0 (Forge 26.3) | 26.3 | Forge | 25 | GeckoLib | OPAC · JourneyMap · Xaero's Minimap |

The changelog for each is `changelogs/1.2.0/<loader>-<minecraft>.md` (e.g. `forge-26.3.md`). Only tick the one game
version and loader listed for each file: none of the jars has been tried on a neighbouring version.

## 2. Images

Nothing new is required; the travel and preview pictures still show 1.2.0 correctly. A picture of a village coach
stop and of the discovery card would show off what's new, once you take them in game.

## 3. Update the description

On the project's **Description**, switch to the Markdown editor and replace the WHOLE text with `description.md`. It
now says every village has a stop, lists the new integrations, the new settings and a "For pack makers and server
owners" section, and the new licence.

## 4. Relations

Add as optional dependencies, next to the existing GeckoLib (required), Fabric API (required), Jade, JEI, FTB Chunks,
Mod Menu and TerraFirmaCraft: **Open Parties and Claims**, **JourneyMap**, **Xaero's Minimap**, **KubeJS** and
**Game Stages**. The village mods (Repurposed Structures, ChoiceTheorem's Overhauled Village, Towns and Towers,
Integrated Villages, Dungeons and Taverns) can go in as optional too: each gets coach stops when installed.

## 5. Licence

Change the project's **License** to **Custom License** with the whole of `../LICENSE` (MIT with the credit
condition; the same file is inside every jar as `LICENSE_Stagecoach`).

## Project settings (set when the project was created)

| Field | Value |
|---|---|
| Name | `Stagecoach` |
| Summary | `Craftable stagecoaches in eleven woods: build a road network, discover stops and pay XP to travel between them.` |
| Main category | Player Transport |
| Other categories | Server Utility |
| Logo | `logo-1024.png` |
| Source | `https://github.com/FelixForLyfe/Stagecoach` |
| Issues | `https://github.com/FelixForLyfe/Stagecoach/issues` |
| Wiki | `https://felixforlyfe.github.io/Stagecoach/` |
| Comments | Enabled: the description's "About this mod" asks players for bug reports and ideas there |

With villages and item fares, a summary like `Stagecoaches in eleven woods and a coach stop in every village: build a
road network, discover stops and pay a fare to travel between them.` would fit better, if you want to change it.
