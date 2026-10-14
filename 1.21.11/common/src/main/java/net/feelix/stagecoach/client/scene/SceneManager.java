package net.feelix.stagecoach.client.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Scenes and scene rules from resource packs: assets/<namespace>/stagecoach/scenes/*.json and stagecoach/scene_rules.json. */
public final class SceneManager {
    private SceneManager() {}

    public record Loaded(Map<Identifier, Scene> scenes, Map<String, Texture> textures, SceneRules rules) {
        static final Loaded EMPTY = new Loaded(Map.of(), Map.of(), SceneRules.NONE);
    }

    public record Texture(Identifier id, int width, int height) {}

    public static final Identifier ID = Stagecoach.id("scenes");
    public static final Identifier ROAD = Stagecoach.id(Scene.ROAD);
    private static final String FOLDER = Stagecoach.MOD_ID + "/scenes";
    private static final String RULES = Stagecoach.MOD_ID + "/scene_rules.json";

    private static volatile Loaded current = Loaded.EMPTY;
    private static final Set<String> WARNED = new HashSet<>();

    public static final SimplePreparableReloadListener<Loaded> RELOADER = new SimplePreparableReloadListener<>() {
        @Override
        protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
            return load(manager);
        }

        @Override
        protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
            current = loaded;
            WARNED.clear();
        }
    };

    static Loaded load(ResourceManager manager) {
        Map<Identifier, Scene> scenes = new HashMap<>();
        Map<String, Texture> textures = new HashMap<>();
        for (Map.Entry<Identifier, Resource> e : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
            try (Reader reader = e.getValue().openAsReader()) {
                Scene scene = Scene.parse(JsonParser.parseReader(reader).getAsJsonObject());
                if (scene.image() != null) texture(manager, scene.image(), textures);
                for (Scene.Layer layer : scene.layers()) texture(manager, layer.texture(), textures);
                scenes.put(id, scene);
            } catch (Exception ex) {
                Stagecoach.LOGGER.warn("Skipping the travel scene {} ({}): {}", file, e.getValue().sourcePackId(), ex.getMessage());
            }
        }
        SceneRules rules = SceneRules.NONE;
        for (String namespace : manager.getNamespaces()) {
            List<Resource> files = manager.getResourceStack(Identifier.fromNamespaceAndPath(namespace, RULES));
            for (int i = files.size() - 1; i >= 0; i--) {
                try (Reader reader = files.get(i).openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    rules = rules.then(SceneRules.parse(json.getAsJsonObject()));
                } catch (Exception ex) {
                    Stagecoach.LOGGER.warn("Skipping {}:{} ({}): {}", namespace, RULES, files.get(i).sourcePackId(), ex.getMessage());
                }
            }
        }
        return new Loaded(Map.copyOf(scenes), Map.copyOf(textures), rules);
    }

    /** Layers tile at the texture's own size, so it is read once here rather than per frame. */
    private static void texture(ResourceManager manager, String texture, Map<String, Texture> textures) throws Exception {
        if (textures.containsKey(texture)) return;
        Identifier id = Identifier.tryParse(texture);
        if (id == null) throw new IllegalArgumentException("\"" + texture + "\" isn't a resource id");
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) throw new IllegalArgumentException("no texture " + id);
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            textures.put(texture, new Texture(id, image.getWidth(), image.getHeight()));
        }
    }

    public static @Nullable Texture texture(String texture) {
        return current.textures.get(texture);
    }

    /** The stop's own scene, else the first matching rule, else the server's default, else the road. */
    public static Scene pick(Packets.JourneyStart journey, @Nullable ClientLevel level) {
        Loaded loaded = current;
        Scene scene = find(loaded, journey.stopScene());
        if (scene == null) {
            String biome = journey.destBiome().toString(), dimension = journey.destDimension().toString();
            scene = find(loaded, loaded.rules.pick(biome, dimension, tag -> inTag(level, journey.destBiome(), tag)));
        }
        if (scene == null) scene = find(loaded, journey.defaultScene());
        if (scene == null) scene = loaded.scenes.get(ROAD);
        return scene == null ? Scene.BUILTIN_ROAD : scene;
    }

    private static @Nullable Scene find(Loaded loaded, @Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier key = Identifier.tryParse(id);
        Scene scene = key == null ? null : loaded.scenes.get(key);
        if (scene == null && WARNED.add(id)) Stagecoach.LOGGER.warn("No travel scene '{}' in the loaded resource packs; using the next one", id);
        return scene;
    }

    private static boolean inTag(@Nullable ClientLevel level, Identifier biome, String tag) {
        Identifier tagId = Identifier.tryParse(tag);
        if (level == null || tagId == null) return false;
        return level.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, biome))
                .map(e -> e.is(TagKey.create(Registries.BIOME, tagId))).orElse(false);
    }
}
