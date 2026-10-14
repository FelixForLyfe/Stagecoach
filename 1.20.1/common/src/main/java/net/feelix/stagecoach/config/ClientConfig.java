package net.feelix.stagecoach.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Minimal wins over simplified; with neither, the road scene shows. A server's forceClientTravelStyle wins over both. */
public final class ClientConfig {
    public static final ClientConfig DEFAULTS = new ClientConfig(false, false, true, true, true, true, true);

    private static volatile ClientConfig current = DEFAULTS;
    private static volatile String forcedStyle = "none";
    private static Path file;

    public final boolean simplifiedLoadingScreen;
    public final boolean minimalLoadingScreen;
    public final boolean showOtherStopsOnMap;
    public final boolean showPlacementPreview;
    public final boolean discoveryEffects;
    public final boolean placementEffects;
    public final boolean discoverySound;

    public ClientConfig(boolean simplifiedLoadingScreen, boolean minimalLoadingScreen, boolean showOtherStopsOnMap,
                        boolean showPlacementPreview, boolean discoveryEffects, boolean placementEffects, boolean discoverySound) {
        this.simplifiedLoadingScreen = simplifiedLoadingScreen;
        this.minimalLoadingScreen = minimalLoadingScreen;
        this.showOtherStopsOnMap = showOtherStopsOnMap;
        this.showPlacementPreview = showPlacementPreview;
        this.discoveryEffects = discoveryEffects;
        this.placementEffects = placementEffects;
        this.discoverySound = discoverySound;
    }

    public static ClientConfig get() {
        return current;
    }

    /** Sent by the server when the player joins; "none" leaves the choice to the player. */
    public static void setForcedStyle(String style) {
        forcedStyle = StagecoachConfig.TRAVEL_STYLES.contains(style) ? style : "none";
    }

    public static boolean styleForced() {
        return !forcedStyle.equals("none");
    }

    public static String forcedStyle() {
        return forcedStyle;
    }

    public boolean roadScene() {
        return styleForced() ? forcedStyle.equals("road") : !minimalLoadingScreen && !simplifiedLoadingScreen;
    }

    public boolean mapScreen() {
        return styleForced() ? forcedStyle.equals("map") : !minimalLoadingScreen && simplifiedLoadingScreen;
    }

    public static void load(Path path, List<String> warnings) {
        file = path;
        if (!Files.exists(path)) {
            save(DEFAULTS, warnings);
            return;
        }
        try {
            current = parse(Files.readString(path, StandardCharsets.UTF_8), warnings);
        } catch (IOException e) {
            warnings.add("could not read " + path + " (" + e.getMessage() + ") - using defaults");
        }
    }

    /** A missing or bad field falls back to its default; bad ones also warn. */
    public static ClientConfig parse(String json, List<String> warnings) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            warnings.add("not valid JSON (" + e.getMessage() + ") - using defaults");
            return DEFAULTS;
        }
        if (!root.isJsonObject()) {
            warnings.add("not a JSON object - using defaults");
            return DEFAULTS;
        }
        JsonObject o = root.getAsJsonObject();
        ClientConfig d = DEFAULTS;
        return new ClientConfig(bool(o, "simplifiedLoadingScreen", d.simplifiedLoadingScreen, warnings),
                bool(o, "minimalLoadingScreen", d.minimalLoadingScreen, warnings),
                bool(o, "showOtherStopsOnMap", d.showOtherStopsOnMap, warnings),
                bool(o, "showPlacementPreview", d.showPlacementPreview, warnings),
                bool(o, "discoveryEffects", d.discoveryEffects, warnings),
                bool(o, "placementEffects", d.placementEffects, warnings),
                bool(o, "discoverySound", d.discoverySound, warnings));
    }

    /** Also makes {@code config} the live one. */
    public static void save(ClientConfig config, List<String> warnings) {
        current = config;
        if (file == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("simplifiedLoadingScreen", config.simplifiedLoadingScreen);
        o.addProperty("minimalLoadingScreen", config.minimalLoadingScreen);
        o.addProperty("showOtherStopsOnMap", config.showOtherStopsOnMap);
        o.addProperty("showPlacementPreview", config.showPlacementPreview);
        o.addProperty("discoveryEffects", config.discoveryEffects);
        o.addProperty("placementEffects", config.placementEffects);
        o.addProperty("discoverySound", config.discoverySound);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("could not write " + file + ": " + e.getMessage());
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def, List<String> warnings) {
        JsonElement e = o.get(key);
        if (e == null) return def;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        warnings.add(key + " must be true or false - using default " + def);
        return def;
    }
}
