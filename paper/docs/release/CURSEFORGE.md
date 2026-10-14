# Uploading the Stagecoach plugin to CurseForge

Everything in this folder (`docs/release/`), in the order the CurseForge author console asks for it.

**Where things stand (2026-09-28):** nothing is uploaded yet. The plugin needs its **own project**: CurseForge keeps
Bukkit plugins in a separate section ("Bukkit Plugins") from Minecraft mods, so it can't be a file on the existing
Stagecoach mod project.

## 1. Create the project

Author console → **Create Project** → game **Minecraft**, class **Bukkit Plugins**.

| Field | Value |
|---|---|
| Name | `Stagecoach` if CurseForge accepts it next to the mod; otherwise `Stagecoach (Plugin)` |
| Summary | `Craftable stagecoaches in eleven woods for Paper servers: build a road network, discover stops, pay XP to travel.` |
| Main category | Teleportation |
| Other categories | Fun, Mechanics |
| Logo | `logo-1024.png` |
| Description | Switch to the Markdown editor and paste the WHOLE of `description.md` |
| License | **Custom License**: the whole of `../../LICENSE` (matches `LICENSE_Stagecoach` inside both jars) |
| Comments | Enabled: the description asks players for bug reports and ideas there |

## 2. Upload the two files

On the project's **Files** tab, one file each (paths from the plugin root):

| File | Display name | Release type | Game version | Changelog |
|---|---|---|---|---|
| `build/libs/Stagecoach-paper-1.21.11-1.1.0.jar` | `Stagecoach 1.1.0 (Paper 1.21.11)` | Release | 1.21.11 | the 1.1.0 section of `../../CHANGELOG.md` |
| `build/libs/Stagecoach-pack-merger-1.1.0.jar` | `Stagecoach Pack Merger 1.1.0` | Release | 1.21.11 | `For server owners who put Stagecoach into their own resource pack: double-click, pick your pack, done. Run it again after updating Stagecoach.` |

Optionally, also attach `build/libs/Stagecoach-resourcepack-1.21.11-1.1.0.zip` (the resource pack on its own) as an
**Additional File** under the plugin file, for owners who want to host it themselves (`resourcePack.url`).

Changelog type Markdown for both. No relations (the plugin needs no other plugin). If the Bukkit version list has no
1.21.11 entry, pick the closest 1.21 entry it offers. Don't tick neighbouring versions (1.21.10, 26.x): the jar
declares `api-version: 1.21.11`, and nobody has run it on 26.x.

## 3. Images

Upload on the **Images** tab:

| File | Title | Description |
|---|---|---|
| `gallery/eleven_woods.png` | Eleven woods | Every stagecoach, rendered from its model. |

Worth taking in game (the dev client, GUI hidden with F1 where it helps): the coach on a road, the green and red
placement preview, the destination list, the travel screen.

## Later

- Link the two projects to each other in their descriptions once this one has its URL.
