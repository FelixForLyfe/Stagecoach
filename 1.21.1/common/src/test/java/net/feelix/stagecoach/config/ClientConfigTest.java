package net.feelix.stagecoach.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    @Test
    void theRoadSceneIsTheDefault() {
        ClientConfig c = ClientConfig.parse("{}", new ArrayList<>());
        assertTrue(c.roadScene());
        assertFalse(c.mapScreen());
        assertTrue(c.showOtherStopsOnMap);
        assertTrue(c.showPlacementPreview);
    }

    @Test
    void simplifiedShowsTheMapAndMinimalWinsOverBoth() {
        ClientConfig map = ClientConfig.parse("{\"simplifiedLoadingScreen\": true}", new ArrayList<>());
        assertTrue(map.mapScreen());
        assertFalse(map.roadScene());
        ClientConfig minimal = ClientConfig.parse("{\"simplifiedLoadingScreen\": true, \"minimalLoadingScreen\": true}", new ArrayList<>());
        assertFalse(minimal.mapScreen());
        assertFalse(minimal.roadScene());
    }

    @Test
    void aBadFieldFallsBackOnItsOwnWithAWarning() {
        List<String> warnings = new ArrayList<>();
        ClientConfig c = ClientConfig.parse("{\"simplifiedLoadingScreen\": \"yes\", \"showPlacementPreview\": false}", warnings);
        assertFalse(c.simplifiedLoadingScreen);
        assertFalse(c.showPlacementPreview);
        assertEquals(1, warnings.size(), warnings.toString());
        warnings.clear();
        assertTrue(ClientConfig.parse("not json {", warnings).roadScene());
        assertEquals(1, warnings.size(), warnings.toString());
    }
}
