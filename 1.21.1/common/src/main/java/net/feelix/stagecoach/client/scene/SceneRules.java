package net.feelix.stagecoach.client.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Which scene a destination gets; the first rule whose conditions all hold wins. */
public record SceneRules(List<Rule> rules) {
    public static final SceneRules NONE = new SceneRules(List.of());

    public record Rule(@Nullable String biomeTag, @Nullable String biome, @Nullable String dimension, String scene) {
    }

    public static SceneRules parse(JsonObject json) {
        List<Rule> rules = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("rules")) {
            JsonObject r = e.getAsJsonObject();
            if (!r.has("scene")) throw new IllegalArgumentException("every rule needs a \"scene\"");
            String tag = text(r, "biome_tag");
            if (tag != null && tag.startsWith("#")) tag = tag.substring(1);
            rules.add(new Rule(tag, text(r, "biome"), text(r, "dimension"), r.get("scene").getAsString()));
        }
        return new SceneRules(List.copyOf(rules));
    }

    public SceneRules then(SceneRules more) {
        List<Rule> all = new ArrayList<>(rules);
        all.addAll(more.rules);
        return new SceneRules(List.copyOf(all));
    }

    /** {@code inTag} answers whether the destination's biome is in a biome tag. */
    public @Nullable String pick(String biome, String dimension, Predicate<String> inTag) {
        for (Rule r : rules) {
            if (r.biome != null && !r.biome.equals(biome)) continue;
            if (r.dimension != null && !r.dimension.equals(dimension)) continue;
            if (r.biomeTag != null && !inTag.test(r.biomeTag)) continue;
            return r.scene;
        }
        return null;
    }

    private static @Nullable String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : null;
    }
}
