package net.feelix.stagecoach.bukkit;

import net.feelix.stagecoach.bukkit.config.StagecoachConfig;
import net.feelix.stagecoach.bukkit.travel.CoachNames;
import net.feelix.stagecoach.bukkit.travel.TravelMath;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private static final StagecoachConfig D = StagecoachConfig.DEFAULTS;

    @Test
    void costIsBasePlusOneLevelPerStartedStretchCappedAtMax() {
        assertEquals(1, TravelMath.cost(0, D));
        assertEquals(2, TravelMath.cost(1, D));
        assertEquals(2, TravelMath.cost(5000, D));
        assertEquals(3, TravelMath.cost(5001, D));
        assertEquals(10, TravelMath.cost(45_000, D));
        assertEquals(10, TravelMath.cost(10_000_000, D));
    }

    @Test
    void itemFareRouteLimitAndPartialRefunds() throws InvalidConfigurationException {
        assertEquals(1, TravelMath.itemCost(0, D));
        assertEquals(2, TravelMath.itemCost(1000, D));
        assertEquals(3, TravelMath.itemCost(1001, D));
        assertEquals(32, TravelMath.itemCost(1_000_000, D));
        assertEquals(false, TravelMath.tooFar(1_000_000_000, D)); // 0 = no limit
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("maxDistance: 1000");
        StagecoachConfig c = StagecoachConfig.from(y, new ArrayList<>());
        assertEquals(false, TravelMath.tooFar(1000, c));
        assertEquals(true, TravelMath.tooFar(1000.5, c));
        assertEquals(10, TravelMath.refundShare(10, 100));
        assertEquals(3, TravelMath.refundShare(7, 50)); // rounds down
        assertEquals(0, TravelMath.refundShare(10, 0));
    }

    @Test
    void timeIsClampedAnd45000BlocksIsExactlyTheTenMinuteCap() {
        assertEquals(5.0, TravelMath.seconds(10, D));
        assertEquals(600.0, TravelMath.seconds(45_000, D));
        assertEquals(600.0, TravelMath.seconds(90_000, D));
        assertEquals(12_000, TravelMath.ticks(45_000, D));
        assertEquals(20 * 40, TravelMath.ticks(3000, D));
        assertEquals("0:00", TravelMath.clock(0));
        assertEquals("0:01", TravelMath.clock(1));
        assertEquals("10:00", TravelMath.clock(12_000));
        assertEquals(5.0, TravelMath.horizontalDistance(0, 0, 3, 4));
    }

    @Test
    void namesAreCleanedAndBounded() {
        assertEquals("King's Road", CoachNames.clean("  King's   Road ", 24));
        assertEquals("Red", CoachNames.clean("§cRed", 24));
        assertEquals("ab", CoachNames.clean("a\u0000b", 24));
        assertNull(CoachNames.clean("   ", 24));
        assertNull(CoachNames.clean("x".repeat(25), 24));
        assertEquals("x".repeat(24), CoachNames.clean("x".repeat(24), 24));
        assertEquals(24, CoachNames.clean("🐴".repeat(24), 24).codePointCount(0, 48)); // emoji count as one
        assertNull(CoachNames.clean("Riverrun", 5));
        assertEquals("x".repeat(64), CoachNames.clean("x".repeat(64), 64));
        assertEquals("Stagecoach 7", CoachNames.defaultName(7));
    }

    @Test
    void configFallsBackPerFieldAndResetsAnInvertedRange() throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                baseCost: -3
                blocksPerLevel: 2.5
                maxCost: 20
                blocksPerSecond: fast
                minTravelSeconds: 900
                maxTravelSeconds: 60
                allowCrossWorld: "yes"
                onlyOwnerCanBreak: false
                combatLockSeconds: 0
                placementPreview: false
                itemCostEnabled: true
                costItem: ""
                cancelRefundPercent: 150
                maxNameLength: 64
                blockedWorlds: [world_nether, 7]
                """);
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.from(y, warnings);
        assertEquals(D.baseCost(), c.baseCost());
        assertEquals(D.blocksPerLevel(), c.blocksPerLevel());
        assertEquals(20, c.maxCost());
        assertEquals(D.blocksPerSecond(), c.blocksPerSecond());
        assertEquals(D.minTravelSeconds(), c.minTravelSeconds());
        assertEquals(D.maxTravelSeconds(), c.maxTravelSeconds());
        assertEquals(D.allowCrossWorld(), c.allowCrossWorld());
        assertEquals(false, c.onlyOwnerCanBreak());
        assertEquals(0, c.combatLockSeconds());
        assertEquals(false, c.placementPreview());
        assertEquals(true, c.craftable());
        assertEquals(true, c.itemCostEnabled());
        assertEquals("minecraft:emerald", c.costItem());
        assertEquals(D.cancelRefundPercent(), c.cancelRefundPercent());
        assertEquals(64, c.maxNameLength());
        assertEquals(List.of("world_nether"), c.blockedWorlds());
        assertEquals(8, warnings.size(), warnings.toString());
    }

    @Test
    void theShippedConfigIsExactlyTheDefaults() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            y.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        List<String> warnings = new ArrayList<>();
        assertEquals(D, StagecoachConfig.from(y, warnings));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void argumentsAreFilledAfterColouringSoPlayerTextCantAddColours() throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("greet: '&6Hello {0}, {1} and {0}'");
        Messages m = Messages.of(y);
        assertEquals("§6Hello &cRed, 3 and &cRed",
                LegacyComponentSerializer.legacySection().serialize(m.c("greet", "&cRed", 3)));
        assertEquals("Hello &cRed, 3 and &cRed", m.plain("greet", "&cRed", 3));
        assertEquals("missing-key", m.plain("missing-key"));
    }

    @Test
    void everyMessageKeyTheCodeUsesExistsInTheShippedFile() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/messages.yml")) {
            y.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        List<String> missing = new ArrayList<>();
        int found = 0;
        java.nio.file.Path src = java.nio.file.Path.of("src/main/java");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:(?:\\bm(?:\\(\\))?|\\bmsg|messages\\(\\))\\.(?:c|item|plain|lines|send|actionBar)|\\bdeny|Denial\\.of|new Denial)"
                        + "\\((?:[a-zA-Z.()]+, )?\"([a-z_-]+[a-z])\"");
        try (var files = java.nio.file.Files.walk(src)) {
            for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                var m = p.matcher(java.nio.file.Files.readString(f));
                while (m.find()) {
                    found++;
                    if (!y.contains(m.group(1))) missing.add(f.getFileName() + ": " + m.group(1));
                }
            }
        }
        for (Wood w : Wood.values()) if (!y.contains("wood-" + w.id())) missing.add("wood-" + w.id());
        for (String end : List.of("cancelled", "destination_gone", "game_mode_changed", "died", "recovered")) {
            if (!y.contains("end-" + end)) missing.add("end-" + end);
        }
        assertTrue(found > 50, "the key scan found only " + found + " uses - is the pattern still right?");
        assertTrue(missing.isEmpty(), missing.toString());
    }
}
