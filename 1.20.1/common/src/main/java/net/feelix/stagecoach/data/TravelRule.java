package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every condition in "applies_to" and every requirement is optional; the ones given must all hold. */
public record TravelRule(List<String> toStops, List<String> fromStops, @Nullable String toDimension,
                         @Nullable String fromDimension, @Nullable String stopFlag, @Nullable String advancement, int minLevel,
                         @Nullable String permission, @Nullable String item, @Nullable String stage,
                         @Nullable JsonElement denyMessage, double costMultiplier) {
    public static final List<String> FLAGS = List.of("public", "hidden", "locked", "free");

    public static TravelRule parse(JsonObject json) {
        JsonObject applies = object(json, "applies_to");
        JsonObject needs = object(json, "requirements");
        String flag = text(applies, "stop_flag");
        if (flag != null && !FLAGS.contains(flag)) throw new IllegalArgumentException("\"stop_flag\" must be one of " + FLAGS);
        int minLevel = needs.has("min_level") ? needs.get("min_level").getAsInt() : 0;
        if (minLevel < 0) throw new IllegalArgumentException("\"min_level\" can't be negative");
        double multiplier = json.has("cost_multiplier") ? json.get("cost_multiplier").getAsDouble() : 1.0;
        if (!(multiplier >= 0 && multiplier <= 1000)) throw new IllegalArgumentException("\"cost_multiplier\" must be 0 to 1000");
        return new TravelRule(stops(applies, "to_stops"), stops(applies, "from_stops"), dimension(applies, "to_dimension"),
                dimension(applies, "from_dimension"), flag, text(needs, "advancement"), minLevel, text(needs, "permission"),
                text(needs, "item"), text(needs, "stage"), json.get("deny_message"), multiplier);
    }

    public boolean hasRequirements() {
        return advancement != null || minLevel > 0 || permission != null || item != null || stage != null;
    }

    /** {@code flags} holds the destination's set flags by name. */
    public boolean appliesTo(long fromId, String fromName, String fromDim, long toId, String toName, String toDim, List<String> flags) {
        if (!toStops.isEmpty() && !matchesStop(toStops, toId, toName)) return false;
        if (!fromStops.isEmpty() && !matchesStop(fromStops, fromId, fromName)) return false;
        if (toDimension != null && !toDimension.equals(toDim)) return false;
        if (fromDimension != null && !fromDimension.equals(fromDim)) return false;
        return stopFlag == null || flags.contains(stopFlag);
    }

    /** "#12" (or "12") is an id; anything else is a name, ignoring case. */
    static boolean matchesStop(List<String> stops, long id, String name) {
        String number = Long.toString(id);
        for (String s : stops) {
            if (s.startsWith("#") ? s.substring(1).equals(number) : s.equalsIgnoreCase(name) || s.equals(number)) return true;
        }
        return false;
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null) return new JsonObject();
        if (!e.isJsonObject()) throw new IllegalArgumentException("\"" + key + "\" must be an object");
        return e.getAsJsonObject();
    }

    private static @Nullable String text(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null) return null;
        if (!e.isJsonPrimitive()) throw new IllegalArgumentException("\"" + key + "\" must be text");
        return e.getAsString();
    }

    private static @Nullable String dimension(JsonObject json, String key) {
        String d = text(json, key);
        if (d == null) return null;
        d = d.toLowerCase(Locale.ROOT);
        return d.contains(":") ? d : "minecraft:" + d;
    }

    private static List<String> stops(JsonObject json, String key) {
        JsonElement e = json.get(key);
        List<String> out = new ArrayList<>();
        if (e == null) return out;
        if (e.isJsonPrimitive()) out.add(e.getAsString());
        else if (e.isJsonArray()) for (JsonElement s : e.getAsJsonArray()) out.add(s.getAsString());
        else throw new IllegalArgumentException("\"" + key + "\" must be a stop name or a list of them");
        return List.copyOf(out);
    }
}
