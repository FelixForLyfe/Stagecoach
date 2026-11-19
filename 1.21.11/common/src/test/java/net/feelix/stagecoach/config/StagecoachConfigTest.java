package net.feelix.stagecoach.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagecoachConfigTest {
    @Test
    void optionKeysAreTheModIdAndTheOptionInSnakeCase() {
        assertEquals("stagecoach.max_coaches_per_player", StagecoachConfig.optionKey("maxCoachesPerPlayer"));
        assertEquals("stagecoach.base_cost", StagecoachConfig.optionKey("baseCost"));
        assertEquals("stagecoach.use_range", StagecoachConfig.optionKey("useRange"));
        assertTrue(StagecoachConfig.KEYS.contains("anyoneCanMakeGlobal"));
        assertFalse(StagecoachConfig.DEFAULTS.anyoneCanMakeGlobal);
    }

    @Test
    void withoutOverridesThePlayerGetsTheServerConfigItself() {
        List<String> warnings = new ArrayList<>();
        assertSame(StagecoachConfig.DEFAULTS, StagecoachConfig.DEFAULTS.withOverrides(k -> null, warnings));
        assertSame(StagecoachConfig.DEFAULTS, StagecoachConfig.DEFAULTS.withOverrides(k -> " ", warnings));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void overridesReplaceOnlyTheirOwnOptions() {
        Map<String, String> meta = Map.of("maxCoachesPerPlayer", "5", "instantTravel", "true", "costItem", "minecraft:diamond",
                "blocksPerSecond", "120.5", "blockedDimensions", "[\"minecraft:the_end\"]");
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.DEFAULTS.withOverrides(meta::get, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(5, c.maxCoachesPerPlayer);
        assertTrue(c.instantTravel);
        assertEquals("minecraft:diamond", c.costItem);
        assertEquals(120.5, c.blocksPerSecond);
        assertEquals(List.of("minecraft:the_end"), c.blockedDimensions);
        assertEquals(StagecoachConfig.DEFAULTS.maxCost, c.maxCost);
    }

    @Test
    void aBadOverrideFallsBackToTheDefaultWithAWarning() {
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.DEFAULTS.withOverrides(k -> k.equals("useRange") ? "lots" : null, warnings);
        assertEquals(12, c.useRange);
        assertEquals(1, warnings.size());
    }

    @Test
    void setOptionWritesOneValueAndKeepsTheRestOfTheFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stagecoach.json");
        Files.writeString(file, "{\"maxCost\": 7}", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();
        StagecoachConfig c = StagecoachConfig.setOption(file, "anyoneCanMakeGlobal", "true", warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(c.anyoneCanMakeGlobal);
        assertEquals(7, c.maxCost);
        assertEquals("true", c.valueOf("anyoneCanMakeGlobal"));
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\"anyoneCanMakeGlobal\": true"), written);
        assertTrue(written.contains("\"maxCost\": 7"), written);

        c = StagecoachConfig.setOption(file, "maxCoachesPerPlayer", "3", warnings);
        assertEquals(3, c.maxCoachesPerPlayer);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"maxCoachesPerPlayer\": 3"));
    }

    @Test
    void setOptionRefusesUnknownOptionsAndBadValuesAndLeavesTheFileAlone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stagecoach.json");
        Files.writeString(file, "{\"maxCost\": 7}", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();
        assertNull(StagecoachConfig.setOption(file, "flyingCoaches", "true", warnings));
        assertEquals(1, warnings.size());
        warnings.clear();
        assertNull(StagecoachConfig.setOption(file, "useRange", "999", warnings));
        assertEquals(1, warnings.size());
        assertEquals("{\"maxCost\": 7}", Files.readString(file, StandardCharsets.UTF_8));
    }
}
