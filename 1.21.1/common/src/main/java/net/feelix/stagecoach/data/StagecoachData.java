package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.Stagecoach;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Datapack files under data/<namespace>/stagecoach/; replaced as a whole on every reload. */
public final class StagecoachData {
    private StagecoachData() {}

    public record Snapshot(Map<String, Theme> themes, Map<String, NamePool> namePools, Map<String, TravelRule> rules,
                           Map<String, Injection> injections) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static volatile Snapshot current = Snapshot.EMPTY;

    public static final Identifier ID = Stagecoach.id("data");
    public static final SinglePreparationResourceReloader<Snapshot> RELOADER = new SinglePreparationResourceReloader<>() {
        @Override
        protected Snapshot prepare(ResourceManager manager, Profiler profiler) {
            return load(manager);
        }

        @Override
        protected void apply(Snapshot snapshot, ResourceManager manager, Profiler profiler) {
            current = snapshot;
        }
    };

    public static Snapshot get() {
        return current;
    }

    /** For GameTests: swaps the loaded files for these until the next reload. */
    public static void use(Snapshot snapshot) {
        current = snapshot;
    }

    public static @Nullable Theme theme(String id) {
        return current.themes.get(id);
    }

    public static @Nullable NamePool namePool(String id) {
        return current.namePools.get(id);
    }

    static Snapshot load(ResourceManager manager) {
        Map<String, NamePool> pools = read(manager, "name_pools", NamePool::parse);
        Map<String, Theme> themes = read(manager, "themes", Theme::parse);
        for (Map.Entry<String, Theme> e : themes.entrySet()) {
            String pool = e.getValue().namePool();
            if (pool != null && !pools.containsKey(pool)) {
                Stagecoach.LOGGER.error("Stagecoach theme {} names the pool {}, which no datapack provides; it falls back to the autoNameFormat",
                        e.getKey(), pool);
            }
        }
        Map<String, Injection> injections = read(manager, "structure_injections", Injection::parse);
        for (Map.Entry<String, Injection> e : injections.entrySet()) {
            if (!themes.containsKey(e.getValue().theme())) {
                Stagecoach.LOGGER.error("Stagecoach structure injection {} uses the theme {}, which no datapack provides", e.getKey(),
                        e.getValue().theme());
            }
        }
        return new Snapshot(themes, pools, read(manager, "travel_rules", TravelRule::parse), injections);
    }

    /** Sorted by id, so rules and injections apply in a fixed order. */
    private static <T> Map<String, T> read(ResourceManager manager, String folder, Function<JsonObject, T> parser) {
        String root = Stagecoach.MOD_ID + "/" + folder;
        Map<String, T> out = new TreeMap<>();
        for (Map.Entry<Identifier, Resource> e : manager.findResources(root, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath();
            String id = file.getNamespace() + ":" + path.substring(root.length() + 1, path.length() - ".json".length());
            try (Reader reader = e.getValue().getReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!json.isJsonObject()) throw new IllegalArgumentException("the file must hold one JSON object");
                out.put(id, parser.apply(json.getAsJsonObject()));
            } catch (Exception ex) {
                Stagecoach.LOGGER.error("Skipping {} ({}): {}", file, e.getValue().getPackId(), ex.getMessage());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(out));
    }
}
