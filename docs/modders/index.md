---
title: Modder guide
nav_order: 4
has_children: true
permalink: /modders/
---

# Modder guide

For other mods: read the stop network, discover stops for players, send players on journeys, and change or refuse
what Stagecoach does through events. KubeJS scripts can do the same without a mod.

| You want to… | Read |
|---|---|
| Read stops, discover them, start or cancel journeys | [The Java API](api.md) |
| Change fares and travel times, refuse journeys, discoveries, placing or breaking, edit the destination list | [Events](events.md) |
| Do any of that from a script | [KubeJS](kubejs.md) |

The API and the events exist on all fourteen builds, with the same classes and methods. Only the way you listen to an
event depends on the loader.

## Adding Stagecoach to your build

Stagecoach is on CurseForge, so [Curse Maven](https://www.cursemaven.com/) can fetch any of its files. The project id
is on the Stagecoach CurseForge page (*About Project*), the file id is the number at the end of a file's address.

```groovy
repositories {
    maven {
        url "https://cursemaven.com"
        content { includeGroup "curse.maven" }
    }
}
```

Then add the file for your Minecraft version and loader with the configuration your toolchain uses for a mod you
compile against:

| Toolchain | Line |
|---|---|
| Fabric Loom, Architectury Loom (1.20.1, 1.21.1, 1.21.11) | `modCompileOnly "curse.maven:stagecoach-<project id>:<file id>"` |
| ForgeGradle 6 (Forge 1.20.1) | `compileOnly fg.deobf("curse.maven:stagecoach-<project id>:<file id>")` |
| Fabric Loom on 26.x, ModDevGradle, ForgeGradle 7 | `compileOnly "curse.maven:stagecoach-<project id>:<file id>"` |

Use `modLocalRuntime` (Loom) or `runtimeOnly` as well if you want Stagecoach in your dev client; it then also needs
[GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) at runtime. A jar you built from this repository
works the same way as a local file: `modCompileOnly files("libs/Stagecoach-fabric-1.20.1-1.2.0.jar")`.

Declare the dependency in your mod's metadata: `"depends": {"stagecoach": ">=1.2.0"}` (or `"suggests"`) in
`fabric.mod.json`, a `[[dependencies.<your mod id>]]` entry with `modId = "stagecoach"` in `mods.toml` or
`neoforge.mods.toml`.

## If Stagecoach is optional

Keep every class that mentions a Stagecoach type in a class of its own, and only touch that class after checking the
mod is loaded (`FabricLoader.getInstance().isModLoaded("stagecoach")`, `ModList.get().isLoaded("stagecoach")`).
The JVM checks the types a class uses when it loads the class, so an `if` around a Stagecoach call inside a class that
also runs without Stagecoach is not enough.

## Rules that hold everywhere

- **Server thread only.** Call the API, and expect every event, on the logical server's main thread. Nothing runs on
  the client.
- **Names follow your mappings.** Minecraft types in the API (`ServerPlayer` or `ServerPlayerEntity`, `Component` or
  `Text`) are remapped to whatever your project uses. Stagecoach's own method names don't change, with one exception:
  `CoachPlaceEvent` has `getWorld()` on 1.20.1 and 1.21.1 and `getLevel()` on 1.21.11 and 26.x.
- **The API version** is `StagecoachAPI.API_VERSION` (1). It goes up when a method changes or goes away; new methods
  can be added without it changing.

The examples on these pages use Mojang names. With Yarn, read `ServerPlayerEntity` for `ServerPlayer`, `Text` for
`Component` and `ServerWorld` for `ServerLevel`.

## Building Stagecoach itself

Each Minecraft version is its own Gradle project in its own folder of the repository. See
[CONTRIBUTING.md](https://github.com/FelixForLyfe/Stagecoach/blob/main/CONTRIBUTING.md) for the Java versions, the
build and test commands, and how a change reaches all five folders.
