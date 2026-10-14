package net.feelix.stagecoach.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.client.scene.Scene;
import net.feelix.stagecoach.client.scene.SceneRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackDataTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void travelRuleFromTheDocsExample() {
        TravelRule rule = TravelRule.parse(json("""
                {"applies_to": {"to_stops": ["Thornmere Gate"], "to_dimension": "minecraft:overworld"},
                 "requirements": {"advancement": "mypack:story/first_boss", "min_level": 10},
                 "deny_message": {"translate": "mypack.travel.locked"}, "cost_multiplier": 1.5}"""));
        assertTrue(rule.hasRequirements());
        assertEquals(10, rule.minLevel());
        assertEquals(1.5, rule.costMultiplier());
        assertTrue(rule.appliesTo(1, "Home", "minecraft:overworld", 7, "thornmere gate", "minecraft:overworld", List.of()));
        assertFalse(rule.appliesTo(1, "Home", "minecraft:overworld", 7, "Elsewhere", "minecraft:overworld", List.of()));
        assertFalse(rule.appliesTo(1, "Home", "minecraft:overworld", 7, "Thornmere Gate", "minecraft:the_nether", List.of()));
    }

    @Test
    void travelRuleMatchesIdsDimensionsAndFlags() {
        TravelRule byId = TravelRule.parse(json("{\"applies_to\": {\"from_stops\": \"#4\", \"from_dimension\": \"the_nether\", \"stop_flag\": \"free\"}}"));
        assertEquals("minecraft:the_nether", byId.fromDimension());
        assertTrue(byId.appliesTo(4, "Any", "minecraft:the_nether", 9, "B", "minecraft:the_nether", List.of("free")));
        assertFalse(byId.appliesTo(4, "Any", "minecraft:the_nether", 9, "B", "minecraft:the_nether", List.of()));
        assertFalse(byId.appliesTo(5, "#4", "minecraft:the_nether", 9, "B", "minecraft:the_nether", List.of("free")));
        assertFalse(byId.hasRequirements());
        assertThrows(IllegalArgumentException.class, () -> TravelRule.parse(json("{\"applies_to\": {\"stop_flag\": \"shiny\"}}")));
        assertThrows(IllegalArgumentException.class, () -> TravelRule.parse(json("{\"cost_multiplier\": -1}")));
    }

    @Test
    void triggerConditionsFromTheDocsExample() {
        TriggerConditions c = TriggerConditions.parse("journey_completed", json("{\"distance\": {\"min\": 10000}}"));
        TriggerConditions.Fired far = new TriggerConditions.Fired("", "", "", -1, 0, "A", "B", 12_000, 0, false, 3);
        TriggerConditions.Fired near = new TriggerConditions.Fired("", "", "", -1, 0, "A", "B", 900, 0, false, 3);
        assertTrue(c.matches(far));
        assertFalse(c.matches(near));
        assertTrue(TriggerConditions.ANY.matches(near));
    }

    @Test
    void triggerConditionsRefuseKeysTheTriggerDoesNotHave() {
        assertThrows(IllegalArgumentException.class, () -> TriggerConditions.parse("coach_placed", json("{\"distance\": 5}")));
        TriggerConditions placed = TriggerConditions.parse("coach_placed", json("{\"wood\": \"spruce\", \"dimension\": \"overworld\", \"player\": {}}"));
        assertTrue(placed.matches(new TriggerConditions.Fired("spruce", "minecraft:overworld", "", -1, 0, "", "", 0, 0, false, 0)));
        assertFalse(placed.matches(new TriggerConditions.Fired("oak", "minecraft:overworld", "", -1, 0, "", "", 0, 0, false, 0)));
        TriggerConditions exact = TriggerConditions.parse("stop_discovered", json("{\"total_discovered\": 10}"));
        assertTrue(exact.matches(new TriggerConditions.Fired("", "", "X", 1, 10, "", "", 0, 0, false, 0)));
        assertFalse(exact.matches(new TriggerConditions.Fired("", "", "X", 1, 11, "", "", 0, 0, false, 0)));
    }

    @Test
    void scenesParseLayersImagesAndTheBuiltinRoad() {
        Scene layered = Scene.parse(json("""
                {"sky": {"top": "#1b1530", "bottom": "#4a2c3a"},
                 "layers": [{"texture": "mypack:textures/gui/travel/hills.png", "speed": 0.1, "y": 0.35},
                            {"texture": "mypack:textures/gui/travel/fence.png", "speed": 1.0, "y": 0.75, "scale": 2, "fill": "#3c2a1e"}],
                 "coach": false, "ambient_sound": "mypack:travel.wind"}"""));
        assertEquals(0xFF1B1530, layered.skyTop());
        assertEquals(2, layered.layers().size());
        assertEquals(0.35f, layered.layers().get(0).y());
        assertEquals(0xFF3C2A1E, layered.layers().get(1).fill());
        assertFalse(layered.coach());
        assertFalse(layered.isRoad());
        Scene image = Scene.parse(json("{\"image\": \"mypack:textures/gui/travel/splash.png\", \"fit\": \"contain\"}"));
        assertFalse(image.cover());
        assertTrue(Scene.parse(json("{\"builtin\": \"road\"}")).isRoad());
        assertThrows(IllegalArgumentException.class, () -> Scene.parse(json("{\"image\": \"a:b.png\", \"fit\": \"stretch\"}")));
        assertThrows(RuntimeException.class, () -> Scene.parse(json("{\"sky\": {\"top\": \"purple\"}}")));
    }

    @Test
    void sceneRulesTakeTheFirstMatch() {
        SceneRules rules = SceneRules.parse(json("""
                {"rules": [{"biome_tag": "minecraft:is_badlands", "scene": "stagecoach:desert"},
                           {"dimension": "minecraft:the_nether", "scene": "stagecoach:nether"},
                           {"biome": "minecraft:plains", "scene": "stagecoach:fields"}]}"""));
        assertEquals("stagecoach:desert", rules.pick("minecraft:badlands", "minecraft:overworld", "minecraft:is_badlands"::equals));
        assertEquals("stagecoach:nether", rules.pick("minecraft:crimson_forest", "minecraft:the_nether", t -> false));
        assertEquals("stagecoach:fields", rules.pick("minecraft:plains", "minecraft:overworld", t -> false));
        assertNull(rules.pick("minecraft:forest", "minecraft:overworld", t -> false));
        SceneRules both = SceneRules.parse(json("{\"rules\": [{\"scene\": \"a:first\"}]}")).then(rules);
        assertEquals("a:first", both.pick("minecraft:badlands", "minecraft:overworld", t -> true));
    }

    @Test
    void themesPickTheSameWoodForTheSameSeed() {
        Theme theme = Theme.parse(json("{\"wood\": [\"mangrove\", \"dark_oak\"], \"decor_palette\": \"mangrove\", \"name_pool\": \"stagecoach:marsh\"}"));
        for (long seed = 0; seed < 50; seed++) assertEquals(theme.pickWood(seed), theme.pickWood(seed));
        boolean[] seen = new boolean[2];
        for (long seed = 0; seed < 200; seed++) seen[theme.pickWood(seed).equals("mangrove") ? 0 : 1] = true;
        assertTrue(seen[0] && seen[1], "both woods should come up");
        assertEquals("spruce", Theme.parse(json("{\"wood\": \"spruce\"}")).pickWood(42));
        assertThrows(IllegalArgumentException.class, () -> Theme.parse(json("{\"wood\": []}")));
    }

    @Test
    void injectionsNeedPoolsAPieceAndATheme() {
        Injection i = Injection.parse(json("""
                {"requires_mods": ["repurposed_structures"], "target_pools": ["repurposed_structures:villages/badlands/houses"],
                 "piece": "stagecoach:village/coach_stop_small", "theme": "stagecoach:badlands", "wood": "dark_oak"}"""));
        assertEquals(List.of("repurposed_structures"), i.requiresMods());
        assertTrue(i.guaranteed(), "a stop is guaranteed unless the file says otherwise");
        assertEquals("dark_oak", i.wood());
        assertEquals(List.of("stagecoach:village/coach_stop_small"), i.pieces());
        assertEquals("rigid", i.projection());
        Injection chance = Injection.parse(json("{\"target_pools\": [\"a:p\"], \"piece\": \"a:b\", \"theme\": \"a:t\", \"guaranteed\": false, \"weight\": 6}"));
        assertFalse(chance.guaranteed());
        assertEquals(6, chance.weight());
        assertNull(chance.wood());
        assertThrows(IllegalArgumentException.class, () -> Injection.parse(json("{\"piece\": \"a:b\", \"theme\": \"a:t\"}")));
        assertThrows(IllegalArgumentException.class, () -> Injection.parse(json("{\"target_pools\": [\"a:p\"], \"piece\": \"a:b\", \"theme\": \"a:t\", \"projection\": \"floating\"}")));
    }

    @Test
    void everyShippedVillageInjectionIsGuaranteedAndNamesACoachWood() throws IOException {
        Set<String> woods = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped");
        Path dir = Path.of("common/src/main/resources/data/stagecoach/stagecoach/structure_injections");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() > 70, "found " + files.size());
        for (Path file : files) {
            Injection i = Injection.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            assertTrue(woods.contains(i.wood()), file + " names wood " + i.wood());
            boolean tavern = i.targetPools().contains("nova_structures:tavern");
            assertEquals(!tavern, i.guaranteed(), file + ": villages are guaranteed, taverns are a chance");
        }
    }
}
