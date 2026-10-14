package net.feelix.stagecoach.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The optional conditions of the five Stagecoach advancement triggers; an omitted one always matches. */
public record TriggerConditions(@Nullable String wood, @Nullable String dimension, @Nullable String stopName, @Nullable Long stopId,
                                @Nullable Range totalDiscovered, @Nullable String from, @Nullable String to, @Nullable Range distance,
                                @Nullable Range cost, @Nullable Boolean crossDimension, @Nullable Range totalJourneys) {
    public static final TriggerConditions ANY = new TriggerConditions(null, null, null, null, null, null, null, null, null, null, null);

    /** What happened; fields a trigger doesn't use stay at their defaults. */
    public record Fired(String wood, String dimension, String stopName, long stopId, int totalDiscovered, String from, String to,
                        int distance, int cost, boolean crossDimension, int totalJourneys) {
    }

    public record Range(@Nullable Integer min, @Nullable Integer max) {
        boolean test(int v) {
            return (min == null || v >= min) && (max == null || v <= max);
        }

        static Range parse(String key, JsonElement e) {
            if (e.isJsonPrimitive()) return new Range(e.getAsInt(), e.getAsInt());
            if (!e.isJsonObject()) throw new IllegalArgumentException("\"" + key + "\" must be a number or {\"min\": .., \"max\": ..}");
            JsonObject o = e.getAsJsonObject();
            return new Range(o.has("min") ? o.get("min").getAsInt() : null, o.has("max") ? o.get("max").getAsInt() : null);
        }
    }

    public static final Map<String, Set<String>> KEYS = Map.of(
            "coach_placed", Set.of("wood", "dimension"),
            "stop_discovered", Set.of("stop_name", "stop_id", "total_discovered", "dimension"),
            "journey_started", Set.of("from", "to", "distance", "cost", "cross_dimension"),
            "journey_completed", Set.of("from", "to", "distance", "total_journeys", "cross_dimension"),
            "journey_cancelled", Set.of("to"));

    /** {@code trigger} is the path of the trigger id; "player" is vanilla's own predicate and is skipped. */
    public static TriggerConditions parse(String trigger, JsonObject json) {
        Set<String> allowed = KEYS.get(trigger);
        for (String key : json.keySet()) {
            if (!key.equals("player") && !allowed.contains(key)) {
                throw new IllegalArgumentException("stagecoach:" + trigger + " has no condition \"" + key + "\" (it has " + List.copyOf(allowed) + ")");
            }
        }
        return new TriggerConditions(text(json, "wood"), dimension(json), text(json, "stop_name"),
                json.has("stop_id") ? json.get("stop_id").getAsLong() : null, range(json, "total_discovered"), text(json, "from"),
                text(json, "to"), range(json, "distance"), range(json, "cost"),
                json.has("cross_dimension") ? json.get("cross_dimension").getAsBoolean() : null, range(json, "total_journeys"));
    }

    public boolean matches(Fired f) {
        return (wood == null || wood.equals(f.wood)) && (dimension == null || dimension.equals(f.dimension))
                && (stopName == null || stopName.equalsIgnoreCase(f.stopName)) && (stopId == null || stopId == f.stopId)
                && (totalDiscovered == null || totalDiscovered.test(f.totalDiscovered))
                && (from == null || from.equalsIgnoreCase(f.from)) && (to == null || to.equalsIgnoreCase(f.to))
                && (distance == null || distance.test(f.distance)) && (cost == null || cost.test(f.cost))
                && (crossDimension == null || crossDimension == f.crossDimension)
                && (totalJourneys == null || totalJourneys.test(f.totalJourneys));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (wood != null) o.addProperty("wood", wood);
        if (dimension != null) o.addProperty("dimension", dimension);
        if (stopName != null) o.addProperty("stop_name", stopName);
        if (stopId != null) o.addProperty("stop_id", stopId);
        put(o, "total_discovered", totalDiscovered);
        if (from != null) o.addProperty("from", from);
        if (to != null) o.addProperty("to", to);
        put(o, "distance", distance);
        put(o, "cost", cost);
        if (crossDimension != null) o.addProperty("cross_dimension", crossDimension);
        put(o, "total_journeys", totalJourneys);
        return o;
    }

    private static void put(JsonObject o, String key, @Nullable Range r) {
        if (r == null) return;
        JsonObject j = new JsonObject();
        if (r.min != null) j.addProperty("min", r.min);
        if (r.max != null) j.addProperty("max", r.max);
        o.add(key, j);
    }

    private static @Nullable String text(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : null;
    }

    private static @Nullable String dimension(JsonObject json) {
        String d = text(json, "dimension");
        if (d == null) return null;
        d = d.toLowerCase(Locale.ROOT);
        return d.contains(":") ? d : "minecraft:" + d;
    }

    private static @Nullable Range range(JsonObject json, String key) {
        return json.has(key) ? Range.parse(key, json.get(key)) : null;
    }
}
