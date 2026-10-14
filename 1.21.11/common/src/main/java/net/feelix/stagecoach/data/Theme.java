package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.feelix.stagecoach.travel.Landscape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** {@code decorPalette} is the wood the piece's oak decor turns into; null keeps it oak. */
public record Theme(List<String> woods, @Nullable String decorPalette, @Nullable String namePool) {
    private static final int LAYER = 12;

    public static Theme parse(JsonObject json) {
        JsonElement w = json.get("wood");
        List<String> woods = new ArrayList<>();
        if (w != null && w.isJsonPrimitive()) woods.add(w.getAsString());
        else if (w != null && w.isJsonArray()) for (JsonElement e : w.getAsJsonArray()) woods.add(e.getAsString());
        if (woods.isEmpty()) throw new IllegalArgumentException("\"wood\" must be a wood name or a list of them");
        return new Theme(List.copyOf(woods), text(json, "decor_palette"), text(json, "name_pool"));
    }

    private static @Nullable String text(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public String pickWood(long seed) {
        return woods.get((int) Math.floorMod(Landscape.hash(seed, LAYER, 0), (long) woods.size()));
    }
}
