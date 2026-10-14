# Contributing to Stagecoach

Thanks for helping. This page covers how the repository fits together, how to build and test it, and the few rules
that keep fourteen jars in step.

## What you need

- **JDK 21** for `1.20.1/`, `1.21.1/`, `1.21.11/` and `paper/`, and **JDK 25** for `26.2/` and `26.3/`. Any vendor.
  Each folder's `gradle/gradle-daemon-jvm.properties` names the version, and Gradle finds an installed JDK that
  matches; you don't need to set `JAVA_HOME` for it.
- **Python 3** for the generators in `tools/` (`nbtlib` for the structure tools, `Pillow` for the renders).
- **Node.js** only for the Paper plugin's end-to-end tests.

## How it's laid out

Each Minecraft version is its own Gradle project with the same layout:

```
common/src/main      shared code and every asset, data file and mixin; no loader imports
common/src/test      JUnit tests (pure Java)
common/src/gametest  GameTests and their structures (never in a jar)
fabric/ neoforge/ forge/   the loader layers: entrypoint, events, Platform, client hooks, metadata
```

Shared code reaches the loader only through `platform/Platform`, `platform/Registrar` and `client/ClientPlatform`.
`1.20.1/` and `1.21.1/` use Yarn names; `1.21.11/`, `26.2/` and `26.3/` use Mojang names.

**A change to shared code goes into all five folders.** The folders are separate copies, not one source tree, because
Minecraft's API changes too much between them. Port your change to each one, in its own names, and build each.

## Building

From a version folder:

```
./gradlew build
```

The jars land in `<loader>/build/libs/`. Use the jar without `-sources`.

## Testing

Run the cheap checks first and stop at the first failure.

| Check | 1.20.1 | 1.21.1 | 1.21.11, 26.2, 26.3 |
|---|---|---|---|
| JUnit | `:fabric:test` | `:fabric:test` | `:fabric:test` |
| GameTests, Fabric | `:fabric:runGametest` | `:fabric:runGametest` | `:fabric:runGametest` |
| GameTests, NeoForge | | `:neoforge:runGametest` | `:neoforge:runGameTestServer` |
| GameTests, Forge | `:forge:runGametest` | `:forge:runGametest -Pgametests` | `:forge:runGameTestServer -Pgametests` |
| Client GameTest (hidden window) | | | `:fabric:runClientGameTest` |

On Forge 1.21.11, 26.2 and 26.3, run `python tools/make_forge_test_instances.py <folder>` after adding a GameTest.
Never build a release jar with `-Pgametests`.

Every new mechanic gets a GameTest; pure logic (fares, times, names, configs) gets a JUnit test in a class with no
Minecraft imports. A bug fix starts with a test that fails without it.

## Generated files

Don't edit these by hand; change the generator and run it.

| Output | Generator |
|---|---|
| `footprint/StagecoachFootprintData.java` (collision from the model) | `python tools/generate_footprint.py` |
| The API events and `platform/EventHooks` | `python tools/make_api_events.py <folder>` |
| Structure injections (`data/stagecoach/stagecoach/structure_injections/`) | `python tools/make_injections.py` |
| The village coach-stop pieces | `python tools/make_coach_stop.py` |
| GameTest structures | `python tools/make_test_structures.py` |
| Forge 26.x test instances | `python tools/make_forge_test_instances.py <folder>` |
| Per-file changelogs (`release/changelogs/<version>/`) | `python tools/make_changelogs.py` |

`StagecoachFootprintTest` fails while the footprint is stale, so a model change can't slip through without it.

## Code style

- Comment only what the code can't say: non-obvious maths, game logic or a workaround, in one short line. No file
  headers, no comments that restate the code.
- Direct, linear code; no factories, wrappers or interfaces that aren't needed.
- Community names: `stack`, `level` (or `world` in Yarn), `pos`, `state`, `player`, `be`.
- Explicit single-class imports.
- No ids or translation strings in logic: use the registry classes and `Stagecoach.MOD_ID`.
- Hot paths (ticks, rendering, per-block loops) allocate nothing: reuse fields, `BlockPos.Mutable` / `MutableBlockPos`.
- Client code stays behind the loader's client entrypoint.
- Every number a server owner might want to change belongs in `config/stagecoach.json`, not in code.

## Things that are easy to break

- **Journeys are crash-safe by the order of their steps** (the `JourneyManager` javadoc has it): the record is saved
  before the fare is taken, the player is restored before the record is dropped, and so on. Reorder them and a crash
  can leave a player in spectator or refund them twice.
- **Leaving is handled at the head of `PlayerManager#remove`** (`PlayerManagerMixin`), not in a disconnect event,
  which Fabric can fire on the network thread.
- **Forge 1.21.11 and 26.x decide a block break by its result** (`Result.DENY`), not by cancelling; Forge 1.20.1,
  1.21.1 and NeoForge cancel.
- **`StagecoachRenderState` overrides GeckoLib's three data-map methods together** on 1.21.11 and 26.x; overriding one
  crashes the first coach drawn. Only the client GameTest sees it.

## Versions

All fourteen jars share one version, `mod_version` in each folder's `gradle.properties`; bump it in every folder
together. Versions count like an odometer: 1.0.9 is followed by 1.1.0. A released version is never rebuilt or
renumbered, and its changelog entry stays as it was published.

## Pull requests

Say what you changed, which folders it's in, and which of the checks above you ran. If something couldn't be checked
(a look, a sound, a loader you don't have), say that too.
