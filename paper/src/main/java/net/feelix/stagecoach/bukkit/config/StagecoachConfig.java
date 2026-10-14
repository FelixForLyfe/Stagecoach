package net.feelix.stagecoach.bukkit.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;

/** The mod's config under the same names; allowCrossWorld and blockedWorlds stand for its dimensions. */
public record StagecoachConfig(
        int baseCost, int blocksPerLevel, int maxCost,
        boolean itemCostEnabled, String costItem, int itemBaseCost, int blocksPerItem, int maxItemCost,
        double blocksPerSecond, int maxTravelSeconds, int minTravelSeconds, int maxDistance, boolean instantTravel,
        boolean allowCrossWorld, int useRange, int combatLockSeconds, int travelCooldownSeconds,
        int cancelRefundPercent, boolean creativeTravelsFree,
        boolean discoveryRequired, boolean onlyOwnerCanBreak, int maxCoachesPerPlayer, int maxNameLength,
        List<String> blockedWorlds,
        boolean placementPreview, boolean craftable,
        boolean packSend, boolean packRequired, int packPort, String packHost, String packUrl) {

    public static final StagecoachConfig DEFAULTS = from(new MemoryConfiguration(), new ArrayList<>());

    /** Never throws; problems go to {@code warnings}. */
    public static StagecoachConfig from(ConfigurationSection c, List<String> warnings) {
        int baseCost = intField(c, "baseCost", 1, 0, 1_000, warnings);
        int blocksPerLevel = intField(c, "blocksPerLevel", 5000, 1, 10_000_000, warnings);
        int maxCost = intField(c, "maxCost", 10, 0, 1_000, warnings);

        boolean itemCost = boolField(c, "itemCostEnabled", false, warnings);
        String costItem = stringField(c, "costItem", "minecraft:emerald", warnings);
        if (costItem.isEmpty()) {
            warnings.add("costItem is empty - using default \"minecraft:emerald\"");
            costItem = "minecraft:emerald";
        }
        int itemBaseCost = intField(c, "itemBaseCost", 1, 0, 10_000, warnings);
        int blocksPerItem = intField(c, "blocksPerItem", 1000, 1, 10_000_000, warnings);
        int maxItemCost = intField(c, "maxItemCost", 32, 0, 10_000, warnings);

        double blocksPerSecond = doubleField(c, "blocksPerSecond", 75.0, 0.001, 1_000_000, warnings);
        int maxTravelSeconds = intField(c, "maxTravelSeconds", 600, 1, 86_400, warnings);
        int minTravelSeconds = intField(c, "minTravelSeconds", 5, 0, 86_400, warnings);
        if (minTravelSeconds > maxTravelSeconds) {
            warnings.add("minTravelSeconds (" + minTravelSeconds + ") is above maxTravelSeconds (" + maxTravelSeconds
                    + ") - using defaults for both");
            minTravelSeconds = 5;
            maxTravelSeconds = 600;
        }
        int maxDistance = intField(c, "maxDistance", 0, 0, 100_000_000, warnings);
        boolean instant = boolField(c, "instantTravel", false, warnings);
        boolean cross = boolField(c, "allowCrossWorld", false, warnings);
        int useRange = intField(c, "useRange", 12, 1, 256, warnings);
        int combat = intField(c, "combatLockSeconds", 10, 0, 3_600, warnings);
        int cooldown = intField(c, "travelCooldownSeconds", 0, 0, 86_400, warnings);
        int refund = intField(c, "cancelRefundPercent", 100, 0, 100, warnings);
        boolean creativeFree = boolField(c, "creativeTravelsFree", true, warnings);

        boolean discovery = boolField(c, "discoveryRequired", true, warnings);
        boolean owner = boolField(c, "onlyOwnerCanBreak", true, warnings);
        int maxCoaches = intField(c, "maxCoachesPerPlayer", 0, 0, 100_000, warnings);
        int maxName = intField(c, "maxNameLength", 24, 1, 64, warnings);
        List<String> blocked = stringList(c, "blockedWorlds", warnings);

        boolean preview = boolField(c, "placementPreview", true, warnings);
        boolean craftable = boolField(c, "craftable", true, warnings);
        boolean send = boolField(c, "resourcePack.send", true, warnings);
        boolean required = boolField(c, "resourcePack.required", true, warnings);
        int port = intField(c, "resourcePack.port", 8163, 1, 65_535, warnings);
        String host = stringField(c, "resourcePack.host", "", warnings);
        String url = stringField(c, "resourcePack.url", "", warnings);
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            warnings.add("resourcePack.url must start with http:// or https:// - the plugin hosts the pack itself instead");
            url = "";
        }
        return new StagecoachConfig(baseCost, blocksPerLevel, maxCost, itemCost, costItem, itemBaseCost, blocksPerItem,
                maxItemCost, blocksPerSecond, maxTravelSeconds, minTravelSeconds, maxDistance, instant, cross, useRange,
                combat, cooldown, refund, creativeFree, discovery, owner, maxCoaches, maxName, blocked, preview,
                craftable, send, required, port, host, url);
    }

    private static int intField(ConfigurationSection c, String key, int def, int min, int max, List<String> warnings) {
        Object o = c.get(key);
        if (o == null) return def;
        if (o instanceof Number n) {
            double v = n.doubleValue();
            if (v == Math.rint(v) && v >= min && v <= max) return (int) v;
        }
        warnings.add(key + " must be a whole number from " + min + " to " + max + " - using default " + def);
        return def;
    }

    private static double doubleField(ConfigurationSection c, String key, double def, double min, double max,
                                      List<String> warnings) {
        Object o = c.get(key);
        if (o == null) return def;
        if (o instanceof Number n) {
            double v = n.doubleValue();
            if (v >= min && v <= max) return v; // also rejects NaN
        }
        warnings.add(key + " must be a number from " + min + " to " + max + " - using default " + def);
        return def;
    }

    private static boolean boolField(ConfigurationSection c, String key, boolean def, List<String> warnings) {
        Object o = c.get(key);
        if (o == null) return def;
        if (o instanceof Boolean b) return b;
        warnings.add(key + " must be true or false - using default " + def);
        return def;
    }

    private static String stringField(ConfigurationSection c, String key, String def, List<String> warnings) {
        Object o = c.get(key);
        if (o == null) return def;
        if (o instanceof String s) return s.strip();
        warnings.add(key + " must be text in quotes - using default \"" + def + "\"");
        return def;
    }

    private static List<String> stringList(ConfigurationSection c, String key, List<String> warnings) {
        Object o = c.get(key);
        if (o == null) return List.of();
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof String s && !s.isBlank()) out.add(s.strip());
                else warnings.add(key + " entries must be world names in quotes - skipping " + e);
            }
            return List.copyOf(out);
        }
        warnings.add(key + " must be a list of world names, like [\"world_nether\"] - using none");
        return List.of();
    }
}
