package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.model.CoachModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStateTest {
    private static final Logger LOG = Logger.getLogger("test");
    private static final UUID WORLD = UUID.randomUUID(), ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();

    @TempDir
    Path dir;

    private static CoachRecord coach(long id, int x, int turns, UUID owner) {
        return new CoachRecord(id, CoachNames.defaultName(id), WORLD, "world", x, 64, 0, turns, "oak", owner);
    }

    private static Journey journey() {
        return new Journey(77L, BOB, "SURVIVAL", false, false, 30, 0.25f, 1500, 3, List.of("2:AAAA", "1:BBBB"), 1,
                "Riverrun", WORLD, 0, 64, 0, 0,
                2, "Stagecoach 2", WORLD, 9000, 70, 0, 2, 1234, 2400, Journey.Phase.TRAVELLING);
    }

    @Test
    void everythingSurvivesARoundTrip() {
        Path file = dir.resolve("network.json");
        NetworkState s = NetworkState.empty(file, LOG);
        CoachRecord a = s.register(id -> coach(id, 0, 0, ALICE));
        s.register(id -> coach(id, 9000, 2, null));
        s.rename(a, "Riverrun");
        s.discover(BOB, a.id);
        s.putJourney(journey());
        s.flush();

        NetworkState t = NetworkState.load(file, LOG);
        assertEquals("Riverrun", t.coach(1).name);
        assertNull(t.coach(2).owner);
        assertEquals(2, t.coach(2).turns);
        assertTrue(t.hasDiscovered(BOB, 1));
        assertFalse(t.hasDiscovered(ALICE, 1)); // owners know their coach by owning it
        Journey j = t.journey(BOB);
        assertNotNull(j);
        assertEquals(journey().toJson(), j.toJson());
        assertEquals(3, t.register(id -> coach(id, 50, 0, ALICE)).id, "ids continue after a reload");
    }

    @Test
    void theCellIndexCoversEveryCellAndForgetsThemOnRemove() {
        NetworkState s = NetworkState.empty(dir.resolve("n.json"), LOG);
        CoachRecord a = s.register(id -> coach(id, 100, 1, ALICE));
        s.discover(BOB, a.id);
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            int[] o = CoachModel.offset(i, 1);
            NetworkState.Cell c = s.cellAt(WORLD, 100 + o[0], 64 + o[1], o[2]);
            assertNotNull(c, "cell " + i);
            assertEquals(a.id, c.coachId());
            assertEquals(CoachModel.barrier(i), c.barrier());
        }
        assertNull(s.cellAt(WORLD, 100, 70, 0));
        s.remove(a.id);
        assertNull(s.coach(a.id));
        assertNull(s.cellAt(WORLD, 100, 64, 0));
        assertFalse(s.hasDiscovered(BOB, a.id));
    }

    @Test
    void aMissingMainFileFallsBackToTheBackup() throws Exception {
        Path file = dir.resolve("network.json");
        NetworkState s = NetworkState.empty(file, LOG);
        s.register(id -> coach(id, 0, 0, ALICE));
        s.flush();
        s.register(id -> coach(id, 10, 0, ALICE));
        s.flush(); // .bak now holds the one-coach state
        Files.delete(file); // as if the process died between the two moves
        assertEquals(1, NetworkState.load(file, LOG).coaches().size());
    }

    @Test
    void anUnreadableFileIsNeverOverwritten() throws Exception {
        Path file = dir.resolve("network.json");
        Files.writeString(file, "{ this is not json");
        NetworkState s = NetworkState.load(file, LOG);
        assertTrue(s.coaches().isEmpty());
        s.register(id -> coach(id, 0, 0, ALICE));
        s.flush();
        assertEquals("{ this is not json", Files.readString(file));
    }

    @Test
    void aMalformedJourneyIsRejectedWhole() {
        var o = journey().toJson();
        o.remove("xpTotal");
        assertNull(Journey.fromJson(o));
        assertNull(Journey.fromJsonString("not json"));
        assertEquals(journey().endTime(), 1234 + 2400);
    }

    @Test
    void aJourneyFromBeforeItemFaresStillLoads() {
        var o = journey().toJson();
        o.remove("id");
        o.remove("items");
        Journey j = Journey.fromJson(o);
        assertNotNull(j);
        assertEquals(0, j.id);
        assertTrue(j.itemsPaid.isEmpty());
        assertEquals(3, j.xpPaid);
    }

    @Test
    void theItemTotalIsReadWithoutTheServer() {
        assertEquals(3, Fare.total(journey().itemsPaid));
        assertEquals(2, Fare.total(List.of("2:AAAA", "broken", "x:CCCC")));
    }
}
