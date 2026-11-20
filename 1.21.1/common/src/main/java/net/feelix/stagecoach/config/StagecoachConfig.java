package net.feelix.stagecoach.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.Stagecoach;

import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Each field falls back on its own, so a broken file never stops the server. */
public final class StagecoachConfig {
    public static final List<String> TRAVEL_STYLES = List.of("none", "road", "map", "minimal");
    public static final StagecoachConfig DEFAULTS = new StagecoachConfig(new JsonObject(), new ArrayList<>());

    /** Every option name, in file order. */
    public static final Set<String> KEYS = Collections.unmodifiableSet(DEFAULTS.toJsonObject().keySet());

    private static volatile StagecoachConfig current = DEFAULTS;

    public final int baseCost;
    public final int blocksPerLevel;
    public final int maxCost;

    public final boolean itemCostEnabled;
    public final String costItem;
    public final int itemBaseCost;
    public final int blocksPerItem;
    public final int maxItemCost;

    public final double blocksPerSecond;
    public final int maxTravelSeconds;
    public final int minTravelSeconds;
    public final int maxDistance;
    public final boolean instantTravel;
    public final boolean allowCrossDimension;
    public final int useRange;
    public final int combatLockSeconds;
    public final int travelCooldownSeconds;
    public final int cancelRefundPercent;
    public final boolean creativeTravelsFree;

    public final boolean discoveryRequired;
    public final boolean onlyOwnerCanBreak;
    public final boolean opsCanManageAnyCoach;
    public final boolean anyoneCanMakeGlobal;
    public final int maxCoachesPerPlayer;
    public final int maxNameLength;
    public final List<String> blockedDimensions;

    public final boolean tfcRecipes;

    public final Map<String, Double> dimensionCostMultipliers;
    public final double crossDimensionCostMultiplier;
    public final int crossDimensionDistance;
    public final int adminCommandLevel;
    public final String autoNameFormat;
    public final boolean builtinAdvancements;
    public final String forceClientTravelStyle;
    public final String defaultScene;

    public final boolean naturalGeneration;
    public final double generationWeightMultiplier;
    public final List<String> disabledInjections;
    public final int maxGeneratedStopsPerStructure;
    public final boolean generatedCoachesBreakable;

    public final boolean companionsTravel;
    public final int companionRange;
    public final boolean requireRoad;
    public final int ambushChancePercent;
    public final int ambushMinDistance;
    public final List<String> ambushMobs;
    public final int ambushMobCount;

    private StagecoachConfig(JsonObject o, List<String> warnings) {
        baseCost = intField(o, "baseCost", 1, 0, 1_000, warnings);
        blocksPerLevel = intField(o, "blocksPerLevel", 5000, 1, 10_000_000, warnings);
        maxCost = intField(o, "maxCost", 10, 0, 1_000, warnings);

        itemCostEnabled = boolField(o, "itemCostEnabled", false, warnings);
        costItem = stringField(o, "costItem", "minecraft:emerald", warnings);
        itemBaseCost = intField(o, "itemBaseCost", 1, 0, 10_000, warnings);
        blocksPerItem = intField(o, "blocksPerItem", 1000, 1, 10_000_000, warnings);
        maxItemCost = intField(o, "maxItemCost", 32, 0, 10_000, warnings);

        blocksPerSecond = doubleField(o, "blocksPerSecond", 75.0, 0.001, 1_000_000, warnings);
        int maxSeconds = intField(o, "maxTravelSeconds", 600, 1, 86_400, warnings);
        int minSeconds = intField(o, "minTravelSeconds", 5, 0, 86_400, warnings);
        if (minSeconds > maxSeconds) {
            warnings.add("minTravelSeconds (" + minSeconds + ") is above maxTravelSeconds (" + maxSeconds + ") - using 5 and 600");
            minSeconds = 5;
            maxSeconds = 600;
        }
        maxTravelSeconds = maxSeconds;
        minTravelSeconds = minSeconds;
        maxDistance = intField(o, "maxDistance", 0, 0, 100_000_000, warnings);
        instantTravel = boolField(o, "instantTravel", false, warnings);
        allowCrossDimension = boolField(o, "allowCrossDimension", false, warnings);
        useRange = intField(o, "useRange", 12, 1, 256, warnings);
        combatLockSeconds = intField(o, "combatLockSeconds", 10, 0, 3_600, warnings);
        travelCooldownSeconds = intField(o, "travelCooldownSeconds", 0, 0, 86_400, warnings);
        cancelRefundPercent = intField(o, "cancelRefundPercent", 100, 0, 100, warnings);
        creativeTravelsFree = boolField(o, "creativeTravelsFree", true, warnings);

        discoveryRequired = boolField(o, "discoveryRequired", true, warnings);
        onlyOwnerCanBreak = boolField(o, "onlyOwnerCanBreak", true, warnings);
        opsCanManageAnyCoach = boolField(o, "opsCanManageAnyCoach", true, warnings);
        anyoneCanMakeGlobal = boolField(o, "anyoneCanMakeGlobal", false, warnings);
        maxCoachesPerPlayer = intField(o, "maxCoachesPerPlayer", 0, 0, 100_000, warnings);
        maxNameLength = intField(o, "maxNameLength", 24, 1, 64, warnings);
        blockedDimensions = stringList(o, "blockedDimensions", List.of(), warnings);

        tfcRecipes = boolField(o, "tfcRecipes", true, warnings);

        dimensionCostMultipliers = multiplierMap(o, "dimensionCostMultipliers", warnings);
        crossDimensionCostMultiplier = doubleField(o, "crossDimensionCostMultiplier", 1.0, 0, 1_000, warnings);
        crossDimensionDistance = intField(o, "crossDimensionDistance", 10_000, 0, 100_000_000, warnings);
        adminCommandLevel = intField(o, "adminCommandLevel", 2, 0, 4, warnings);
        autoNameFormat = stringField(o, "autoNameFormat", "{biome} Stop", warnings);
        builtinAdvancements = boolField(o, "builtinAdvancements", true, warnings);
        forceClientTravelStyle = choiceField(o, "forceClientTravelStyle", "none", TRAVEL_STYLES, warnings);
        defaultScene = idField(o, "defaultScene", Stagecoach.MOD_ID + ":road", warnings);

        naturalGeneration = boolField(o, "naturalGeneration", true, warnings);
        generationWeightMultiplier = doubleField(o, "generationWeightMultiplier", 1.0, 0, 100, warnings);
        disabledInjections = stringList(o, "disabledInjections", List.of(), warnings);
        maxGeneratedStopsPerStructure = intField(o, "maxGeneratedStopsPerStructure", 1, 0, 1_000, warnings);
        generatedCoachesBreakable = boolField(o, "generatedCoachesBreakable", false, warnings);

        companionsTravel = boolField(o, "companionsTravel", true, warnings);
        companionRange = intField(o, "companionRange", 10, 1, 64, warnings);
        requireRoad = boolField(o, "requireRoad", false, warnings);
        ambushChancePercent = intField(o, "ambushChancePercent", 0, 0, 100, warnings);
        ambushMinDistance = intField(o, "ambushMinDistance", 1000, 0, 100_000_000, warnings);
        ambushMobs = stringList(o, "ambushMobs", List.of("minecraft:pillager", "minecraft:vindicator"), warnings);
        ambushMobCount = intField(o, "ambushMobCount", 3, 1, 32, warnings);
    }

    public double dimensionCostMultiplier(String dimension) {
        Double m = dimensionCostMultipliers.get(dimension);
        return m == null ? 1.0 : m;
    }

    /** The permission option (LuckPerms meta) that overrides {@code key} for one player: maxCoachesPerPlayer -> stagecoach.max_coaches_per_player. */
    public static String optionKey(String key) {
        return Stagecoach.MOD_ID + "." + key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** This config with every option {@code lookup} has a value for laid over it; itself when there are none. */
    public StagecoachConfig withOverrides(Function<String, String> lookup, List<String> warnings) {
        JsonObject o = null;
        for (String key : KEYS) {
            String raw = lookup.apply(key);
            if (raw == null || raw.isBlank()) continue;
            if (o == null) o = toJsonObject();
            o.add(key, parseValue(raw));
        }
        return o == null ? this : new StagecoachConfig(o, warnings);
    }

    /** Text from a command or a permission option as JSON: true/false, a number, a list or object, else a string. */
    static JsonElement parseValue(String raw) {
        String v = raw.trim();
        if (v.equals("true") || v.equals("false")) return new JsonPrimitive(Boolean.parseBoolean(v));
        if (v.startsWith("[") || v.startsWith("{")) {
            try {
                return JsonParser.parseString(v);
            } catch (RuntimeException ignored) {
                return new JsonPrimitive(v);
            }
        }
        try {
            return new JsonPrimitive(new BigDecimal(v));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(v);
        }
    }

    /** The option as it would be written to the file. */
    public String valueOf(String key) {
        JsonElement e = toJsonObject().get(key);
        return e == null ? "" : e.toString();
    }

    /** Sets one option in the file and loads it; null (with the reason in warnings) leaves the file alone. */
    public static @Nullable StagecoachConfig setOption(Path file, String key, String raw, List<String> warnings) {
        if (!KEYS.contains(key)) {
            warnings.add("there is no option called " + key);
            return null;
        }
        JsonElement value = parseValue(raw);
        JsonObject single = new JsonObject();
        single.add(key, value);
        List<String> problems = new ArrayList<>();
        new StagecoachConfig(single, problems);
        if (!problems.isEmpty()) {
            warnings.addAll(problems);
            return null;
        }
        JsonObject root = null;
        if (Files.exists(file)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
            } catch (Exception ignored) {
                // an unreadable file is replaced by the running settings plus this one
            }
        }
        if (root == null) root = get().toJsonObject();
        root.add(key, value);
        write(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", warnings);
        return load(file, warnings);
    }

    public static StagecoachConfig get() {
        return current;
    }

    public static void set(StagecoachConfig config) {
        current = config;
    }

    public static StagecoachConfig parse(String json, List<String> warnings) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            warnings.add("config is not a JSON object - using all defaults");
            return DEFAULTS;
        }
        return new StagecoachConfig(root.getAsJsonObject(), warnings);
    }

    public static StagecoachConfig load(Path file, List<String> warnings) {
        if (!Files.exists(file)) {
            write(file, DEFAULTS.toJson(), warnings);
            return DEFAULTS;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                warnings.add("config is not a JSON object - using all defaults");
                return DEFAULTS;
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            warnings.add("could not read " + file + " (" + e.getMessage() + ") - using all defaults");
            return DEFAULTS;
        }
        int before = warnings.size();
        StagecoachConfig config = new StagecoachConfig(root, warnings);
        // only fill in new keys when nothing was wrong, so an owner's typo is never overwritten
        if (warnings.size() == before && missingKeys(root)) write(file, config.toJson(), warnings);
        return config;
    }

    private static boolean missingKeys(JsonObject root) {
        for (Map.Entry<String, JsonElement> e : DEFAULTS.toJsonObject().entrySet()) {
            if (!root.has(e.getKey())) return true;
        }
        return false;
    }

    private static void write(Path file, String json, List<String> warnings) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("could not write " + file + ": " + e.getMessage());
        }
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(toJsonObject()) + "\n";
    }

    private JsonObject toJsonObject() {
        JsonObject o = new JsonObject();
        o.addProperty("baseCost", baseCost);
        o.addProperty("blocksPerLevel", blocksPerLevel);
        o.addProperty("maxCost", maxCost);
        o.addProperty("itemCostEnabled", itemCostEnabled);
        o.addProperty("costItem", costItem);
        o.addProperty("itemBaseCost", itemBaseCost);
        o.addProperty("blocksPerItem", blocksPerItem);
        o.addProperty("maxItemCost", maxItemCost);
        o.addProperty("blocksPerSecond", blocksPerSecond);
        o.addProperty("maxTravelSeconds", maxTravelSeconds);
        o.addProperty("minTravelSeconds", minTravelSeconds);
        o.addProperty("maxDistance", maxDistance);
        o.addProperty("instantTravel", instantTravel);
        o.addProperty("allowCrossDimension", allowCrossDimension);
        o.addProperty("useRange", useRange);
        o.addProperty("combatLockSeconds", combatLockSeconds);
        o.addProperty("travelCooldownSeconds", travelCooldownSeconds);
        o.addProperty("cancelRefundPercent", cancelRefundPercent);
        o.addProperty("creativeTravelsFree", creativeTravelsFree);
        o.addProperty("discoveryRequired", discoveryRequired);
        o.addProperty("onlyOwnerCanBreak", onlyOwnerCanBreak);
        o.addProperty("opsCanManageAnyCoach", opsCanManageAnyCoach);
        o.addProperty("anyoneCanMakeGlobal", anyoneCanMakeGlobal);
        o.addProperty("maxCoachesPerPlayer", maxCoachesPerPlayer);
        o.addProperty("maxNameLength", maxNameLength);
        JsonArray dims = new JsonArray();
        for (String d : blockedDimensions) dims.add(d);
        o.add("blockedDimensions", dims);
        o.addProperty("tfcRecipes", tfcRecipes);
        JsonObject multipliers = new JsonObject();
        for (Map.Entry<String, Double> e : dimensionCostMultipliers.entrySet()) multipliers.addProperty(e.getKey(), e.getValue());
        o.add("dimensionCostMultipliers", multipliers);
        o.addProperty("crossDimensionCostMultiplier", crossDimensionCostMultiplier);
        o.addProperty("crossDimensionDistance", crossDimensionDistance);
        o.addProperty("adminCommandLevel", adminCommandLevel);
        o.addProperty("autoNameFormat", autoNameFormat);
        o.addProperty("builtinAdvancements", builtinAdvancements);
        o.addProperty("forceClientTravelStyle", forceClientTravelStyle);
        o.addProperty("defaultScene", defaultScene);
        o.addProperty("naturalGeneration", naturalGeneration);
        o.addProperty("generationWeightMultiplier", generationWeightMultiplier);
        JsonArray disabled = new JsonArray();
        for (String d : disabledInjections) disabled.add(d);
        o.add("disabledInjections", disabled);
        o.addProperty("maxGeneratedStopsPerStructure", maxGeneratedStopsPerStructure);
        o.addProperty("generatedCoachesBreakable", generatedCoachesBreakable);
        o.addProperty("companionsTravel", companionsTravel);
        o.addProperty("companionRange", companionRange);
        o.addProperty("requireRoad", requireRoad);
        o.addProperty("ambushChancePercent", ambushChancePercent);
        o.addProperty("ambushMinDistance", ambushMinDistance);
        JsonArray mobs = new JsonArray();
        for (String m : ambushMobs) mobs.add(m);
        o.add("ambushMobs", mobs);
        o.addProperty("ambushMobCount", ambushMobCount);
        return o;
    }

    private static String choiceField(JsonObject o, String key, String def, List<String> choices, List<String> warnings) {
        String v = stringField(o, key, def, warnings);
        if (choices.contains(v)) return v;
        warnings.add(key + " must be one of " + String.join(", ", choices) + " - using default " + def);
        return def;
    }

    /** Namespace and path as vanilla allows them: lowercase letters, digits and _-./ (no colon in the path). */
    private static String idField(JsonObject o, String key, String def, List<String> warnings) {
        String v = stringField(o, key, def, warnings);
        if (v.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) return v.contains(":") ? v : "minecraft:" + v;
        warnings.add(key + " must be a resource id like stagecoach:road - using default " + def);
        return def;
    }

    private static Map<String, Double> multiplierMap(JsonObject o, String key, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return Map.of();
        if (!e.isJsonObject()) {
            warnings.add(key + " must be an object of dimension id to multiplier - using none");
            return Map.of();
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
            try {
                double v = entry.getValue().getAsDouble();
                if (!(v >= 0 && v <= 1_000)) throw new NumberFormatException();
                out.put(entry.getKey(), v);
            } catch (RuntimeException ex) {
                warnings.add(key + "." + entry.getKey() + " must be a number from 0 to 1000 - skipped");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static int intField(JsonObject o, String key, int def, int min, int max, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        try {
            double v = e.getAsDouble();
            if (v != Math.rint(v) || v < min || v > max) throw new NumberFormatException();
            return (int) v;
        } catch (RuntimeException ex) {
            warnings.add(key + " must be a whole number from " + min + " to " + max + " - using default " + def);
            return def;
        }
    }

    private static double doubleField(JsonObject o, String key, double def, double min, double max, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        try {
            double v = e.getAsDouble();
            if (!(v >= min && v <= max)) throw new NumberFormatException(); // also rejects NaN
            return v;
        } catch (RuntimeException ex) {
            warnings.add(key + " must be a number from " + min + " to " + max + " - using default " + def);
            return def;
        }
    }

    private static boolean boolField(JsonObject o, String key, boolean def, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        warnings.add(key + " must be true or false - using default " + def);
        return def;
    }

    private static String stringField(JsonObject o, String key, String def, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !e.getAsString().isBlank()) return e.getAsString().trim();
        warnings.add(key + " must be a text value - using default " + def);
        return def;
    }

    private static List<String> stringList(JsonObject o, String key, List<String> def, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        if (!e.isJsonArray()) {
            warnings.add(key + " must be a list of ids - using the default");
            return def;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : e.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) out.add(item.getAsString().trim());
            else warnings.add(key + " has an entry that isn't text - skipped");
        }
        return List.copyOf(out);
    }
}
