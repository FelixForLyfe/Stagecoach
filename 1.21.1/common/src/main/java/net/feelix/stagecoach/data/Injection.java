package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Pieces offered to other mods' pools: guaranteed ones first until the structure has a stop, else by weight; wood beats the theme's. */
public record Injection(List<String> requiresMods, List<String> targetPools, List<String> pieces, boolean guaranteed, int weight,
                        String theme, @Nullable String wood, String projection) {
    public static Injection parse(JsonObject json) {
        List<String> pools = list(json, "target_pools");
        if (pools.isEmpty()) throw new IllegalArgumentException("\"target_pools\" must list at least one template pool");
        List<String> pieces = list(json, "piece");
        if (pieces.isEmpty()) throw new IllegalArgumentException("\"piece\" (a structure template id, or a list of them) is required");
        if (!json.has("theme")) throw new IllegalArgumentException("\"theme\" is required");
        boolean guaranteed = !json.has("guaranteed") || json.get("guaranteed").getAsBoolean();
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        if (weight < 0 || weight > 150) throw new IllegalArgumentException("\"weight\" must be 0 to 150");
        String projection = json.has("projection") ? json.get("projection").getAsString() : "rigid";
        if (!projection.equals("rigid") && !projection.equals("terrain_matching")) {
            throw new IllegalArgumentException("\"projection\" must be rigid or terrain_matching");
        }
        String wood = json.has("wood") ? json.get("wood").getAsString() : null;
        return new Injection(list(json, "requires_mods"), pools, pieces, guaranteed, weight,
                json.get("theme").getAsString(), wood, projection);
    }

    private static List<String> list(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = json.get(key);
        if (e == null) return out;
        if (e.isJsonPrimitive()) out.add(e.getAsString());
        else for (JsonElement item : e.getAsJsonArray()) out.add(item.getAsString());
        return List.copyOf(out);
    }
}
