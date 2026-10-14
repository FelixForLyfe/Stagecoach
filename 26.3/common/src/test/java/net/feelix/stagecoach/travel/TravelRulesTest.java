package net.feelix.stagecoach.travel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.config.StagecoachConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelRulesTest {
    private static final StagecoachConfig D = StagecoachConfig.DEFAULTS;

    private static StagecoachConfig config(String json) {
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.parse(json, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        return c;
    }

    @Test
    void costIsBasePlusCeilOfDistanceOverBlocksPerLevelCappedAtMax() {
        assertEquals(1, TravelMath.cost(0, D));
        assertEquals(2, TravelMath.cost(1, D));
        assertEquals(2, TravelMath.cost(5000, D));
        assertEquals(3, TravelMath.cost(5000.5, D));
        assertEquals(10, TravelMath.cost(45_000, D));
        assertEquals(10, TravelMath.cost(45_001, D)); // 11, capped at 10
        assertEquals(10, TravelMath.cost(10_000_000, D));
    }

    @Test
    void itemCostHasItsOwnBaseStepAndCap() {
        assertEquals(1, TravelMath.itemCost(0, D));
        assertEquals(2, TravelMath.itemCost(1, D));
        assertEquals(6, TravelMath.itemCost(5000, D));
        assertEquals(32, TravelMath.itemCost(1_000_000, D));
        StagecoachConfig c = config("{\"itemBaseCost\": 0, \"blocksPerItem\": 100, \"maxItemCost\": 5}");
        assertEquals(0, TravelMath.itemCost(0, c));
        assertEquals(3, TravelMath.itemCost(250, c));
        assertEquals(5, TravelMath.itemCost(9999, c));
    }

    @Test
    void maxDistanceZeroMeansNoLimit() {
        assertFalse(TravelMath.tooFar(1e9, D));
        StagecoachConfig c = config("{\"maxDistance\": 2000}");
        assertFalse(TravelMath.tooFar(2000, c));
        assertTrue(TravelMath.tooFar(2000.5, c));
    }

    @Test
    void refundSharesRoundDownAndNeverExceedWhatWasPaid() {
        assertEquals(7, TravelMath.refundShare(7, 100));
        assertEquals(3, TravelMath.refundShare(7, 50));
        assertEquals(0, TravelMath.refundShare(7, 0));
        assertEquals(0, TravelMath.refundShare(1, 99));
        assertEquals(31, TravelMath.refundShare(32, 99));
        assertEquals(0, TravelMath.refundShare(0, 100));
    }

    @Test
    void travelTimeIsDistanceOver75ClampedTo5And600() {
        assertEquals(5.0, TravelMath.seconds(10, D));
        assertEquals(5.0, TravelMath.seconds(375, D));
        assertEquals(20.0, TravelMath.seconds(1500, D));
        assertEquals(600.0, TravelMath.seconds(45_000, D));
        assertEquals(600.0, TravelMath.seconds(90_000, D));
        assertEquals(12_000, TravelMath.ticks(45_000, D));
        assertEquals(100, TravelMath.ticks(1, D));
        assertEquals(401, TravelMath.ticks(1500.01, D)); // never shorter than stated
    }

    @Test
    void horizontalDistanceIgnoresHeight() {
        assertEquals(5.0, TravelMath.horizontalDistance(0, 0, 3, 4), 1e-12);
        assertEquals(0.0, TravelMath.horizontalDistance(7, -3, 7, -3), 1e-12);
    }

    @Test
    void clockRoundsUpAndFormatsMinutesAndSeconds() {
        assertEquals("10:00", TravelMath.clock(12_000));
        assertEquals("0:01", TravelMath.clock(1));
        assertEquals("0:00", TravelMath.clock(0));
        assertEquals("1:05", TravelMath.clock(65 * 20));
    }

    @Test
    void namesAreTrimmedCleanedAndCappedAtTheConfiguredLength() {
        assertEquals("Riverrun Crossing", CoachNames.clean("  Riverrun   Crossing \t", 24));
        assertEquals("Red Keep", CoachNames.clean("§cRed§r Keep", 24));
        assertEquals("abcdefghijklmnopqrstuvwx", CoachNames.clean("abcdefghijklmnopqrstuvwx", 24));
        assertNull(CoachNames.clean("abcdefghijklmnopqrstuvwxy", 24));
        assertEquals("abcdefghijklmnopqrstuvwxy", CoachNames.clean("abcdefghijklmnopqrstuvwxy", 25));
        assertNull(CoachNames.clean("Winterfell", 5));
        assertNull(CoachNames.clean("   ", 24));
        assertNull(CoachNames.clean("", 24));
        assertNull(CoachNames.clean(null, 24));
        assertEquals("a🐴b", CoachNames.clean("a🐴b", 3)); // an emoji counts as one character
        assertEquals("Stagecoach 3", CoachNames.defaultName(3));
    }

    @Test
    void configFallsBackPerFieldAndCreatesTheFileWithDefaults(@TempDir Path dir) throws Exception {
        List<String> warnings = new ArrayList<>();
        Path file = dir.resolve("config/stagecoach.json");
        StagecoachConfig first = StagecoachConfig.load(file, warnings);
        assertSame(StagecoachConfig.DEFAULTS, first);
        assertTrue(Files.exists(file));
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(75.0, written.get("blocksPerSecond").getAsDouble());
        assertEquals(10, written.get("combatLockSeconds").getAsInt());
        assertEquals("minecraft:emerald", written.get("costItem").getAsString());
        assertEquals(0, written.getAsJsonArray("blockedDimensions").size());

        Files.writeString(file, "{\"baseCost\": -3, \"blocksPerLevel\": 2500, \"maxCost\": \"lots\","
                + " \"blocksPerSecond\": 0, \"allowCrossDimension\": \"yes\", \"onlyOwnerCanBreak\": false,"
                + " \"minTravelSeconds\": 700, \"combatLockSeconds\": 2.5, \"cancelRefundPercent\": 150,"
                + " \"costItem\": \"\", \"blockedDimensions\": \"minecraft:the_end\"}");
        warnings.clear();
        StagecoachConfig c = StagecoachConfig.load(file, warnings);
        assertEquals(1, c.baseCost);                 // negative -> default
        assertEquals(2500, c.blocksPerLevel);        // valid -> kept
        assertEquals(10, c.maxCost);                 // not a number -> default
        assertEquals(75.0, c.blocksPerSecond);       // zero -> default
        assertFalse(c.allowCrossDimension);          // not a boolean -> default
        assertFalse(c.onlyOwnerCanBreak);            // valid -> kept
        assertEquals(5, c.minTravelSeconds);         // above max -> both defaults
        assertEquals(600, c.maxTravelSeconds);
        assertEquals(10, c.combatLockSeconds);       // not whole -> default
        assertEquals(100, c.cancelRefundPercent);    // above 100 -> default
        assertEquals("minecraft:emerald", c.costItem);
        assertTrue(c.blockedDimensions.isEmpty());   // not a list -> empty
        assertEquals(9, warnings.size(), warnings.toString());
        // A file with mistakes is never rewritten, so the owner's typo stays where they can fix it.
        assertTrue(Files.readString(file).contains("\"lots\""));

        Files.writeString(file, "[1, 2, 3]");
        warnings.clear();
        assertSame(StagecoachConfig.DEFAULTS, StagecoachConfig.load(file, warnings));
        assertEquals(1, warnings.size());
    }

    @Test
    void anOldConfigGetsTheNewOptionsWrittenInAndKeepsItsValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stagecoach.json");
        Files.writeString(file, "{\"baseCost\": 3, \"maxCost\": 20, \"onlyOwnerCanBreak\": false}");
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.load(file, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(3, c.baseCost);
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(3, written.get("baseCost").getAsInt());
        assertEquals(20, written.get("maxCost").getAsInt());
        assertFalse(written.get("onlyOwnerCanBreak").getAsBoolean());
        assertFalse(written.get("itemCostEnabled").getAsBoolean());
        assertEquals(0, written.get("maxDistance").getAsInt());
        assertTrue(written.get("discoveryRequired").getAsBoolean());
    }

    @Test
    void theNewOptionsReadTheirValues() {
        StagecoachConfig c = config("{\"itemCostEnabled\": true, \"costItem\": \"#c:gems\", \"instantTravel\": true,"
                + " \"useRange\": 6, \"travelCooldownSeconds\": 30, \"discoveryRequired\": false, \"maxCoachesPerPlayer\": 3,"
                + " \"maxNameLength\": 40, \"blockedDimensions\": [\"minecraft:the_nether\"], \"creativeTravelsFree\": false,"
                + " \"opsCanManageAnyCoach\": false, \"tfcRecipes\": false}");
        assertTrue(c.itemCostEnabled);
        assertEquals("#c:gems", c.costItem);
        assertTrue(c.instantTravel);
        assertEquals(6, c.useRange);
        assertEquals(30, c.travelCooldownSeconds);
        assertFalse(c.discoveryRequired);
        assertEquals(3, c.maxCoachesPerPlayer);
        assertEquals(40, c.maxNameLength);
        assertEquals(List.of("minecraft:the_nether"), c.blockedDimensions);
        assertFalse(c.creativeTravelsFree);
        assertFalse(c.opsCanManageAnyCoach);
        assertFalse(c.tfcRecipes);
    }

    @Test
    void fareWithMultipliersMatchesTheOldPriceAtOne() {
        for (double d : new double[] {0, 1, 5000, 5000.5, 45_000, 45_001, 10_000_000}) {
            assertEquals(TravelMath.cost(d, D), TravelMath.fare(TravelMath.rawCost(d, D, false), 1.0, D.maxCost), "distance " + d);
            assertEquals(TravelMath.itemCost(d, D), TravelMath.fare(TravelMath.rawCost(d, D, true), 1.0, D.maxItemCost), "distance " + d);
        }
    }

    @Test
    void fareRoundsUpOnceAfterEveryMultiplierThenCaps() {
        assertEquals(3, TravelMath.fare(10, 0.3, 100), "10 x 0.3 must not round up to 4");
        assertEquals(2, TravelMath.fare(3, 0.5, 100));
        assertEquals(0, TravelMath.fare(7, 0.0, 100));
        assertEquals(10, TravelMath.fare(8, 2.0, 10));
        assertEquals(5, TravelMath.fare(2, 1.5 * 1.5, 100));
    }

    @Test
    void theOneTwoZeroOptionsDefaultToTheOldBehaviour() {
        assertEquals(2, D.adminCommandLevel);
        assertEquals("{biome} Stop", D.autoNameFormat);
        assertTrue(D.builtinAdvancements);
        assertEquals("none", D.forceClientTravelStyle);
        assertEquals("stagecoach:road", D.defaultScene);
        assertEquals(1.0, D.crossDimensionCostMultiplier);
        assertEquals(10_000, D.crossDimensionDistance);
        assertEquals(1.0, D.dimensionCostMultiplier("minecraft:overworld"));
        assertTrue(D.naturalGeneration);
        assertEquals(1.0, D.generationWeightMultiplier);
        assertEquals(1, D.maxGeneratedStopsPerStructure);
        assertFalse(D.generatedCoachesBreakable);
        assertTrue(D.disabledInjections.isEmpty());
    }

    @Test
    void theOneTwoZeroOptionsReadTheirValuesAndRejectBadOnes() {
        StagecoachConfig c = config("{\"dimensionCostMultipliers\": {\"minecraft:the_nether\": 0.5}, \"crossDimensionCostMultiplier\": 2,"
                + " \"forceClientTravelStyle\": \"map\", \"defaultScene\": \"mypack:dunes\", \"disabledInjections\": [\"stagecoach:vanilla_taiga\"],"
                + " \"generationWeightMultiplier\": 0.5, \"adminCommandLevel\": 3}");
        assertEquals(0.5, c.dimensionCostMultiplier("minecraft:the_nether"));
        assertEquals(1.0, c.dimensionCostMultiplier("minecraft:overworld"));
        assertEquals(2.0, c.crossDimensionCostMultiplier);
        assertEquals("map", c.forceClientTravelStyle);
        assertEquals("mypack:dunes", c.defaultScene);
        assertEquals(List.of("stagecoach:vanilla_taiga"), c.disabledInjections);
        assertEquals(3, c.adminCommandLevel);
        List<String> warnings = new ArrayList<>();
        StagecoachConfig bad = StagecoachConfig.parse("{\"forceClientTravelStyle\": \"fancy\", \"defaultScene\": \"Not An Id\","
                + " \"dimensionCostMultipliers\": {\"minecraft:the_end\": -2}, \"adminCommandLevel\": 9}", warnings);
        assertEquals("none", bad.forceClientTravelStyle);
        assertEquals("stagecoach:road", bad.defaultScene);
        assertEquals(1.0, bad.dimensionCostMultiplier("minecraft:the_end"));
        assertEquals(2, bad.adminCommandLevel);
        assertEquals(4, warnings.size(), warnings.toString());
    }
}
