package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSearchTest {
    /** A world of road blocks at the given (x, y, z); everything else is not road. */
    private static RoadSearch.Ground roads(LongOpenHashSet road) {
        return (x, y, z) -> road.contains(RoadSearch.pack(x, y, z)) ? RoadSearch.ROAD : RoadSearch.NOT_ROAD;
    }

    private static void line(LongOpenHashSet road, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) road.add(RoadSearch.pack(x, y, z));
    }

    private static RoadSearch run(RoadSearch.Ground ground) {
        RoadSearch s = new RoadSearch(8);
        s.addTarget(2, 300, 65, 0);
        s.addTarget(3, 0, 65, 300);
        s.seed(ground, 0, 65, 0);
        while (!s.step(ground, 1000)) { }
        return s;
    }

    @Test
    void packingRoundTripsNegativeAndLargeCoordinates() {
        int[][] points = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-29_999_999, -2048, 12345}};
        for (int[] p : points) {
            long k = RoadSearch.pack(p[0], p[1], p[2]);
            assertEquals(p[0], RoadSearch.x(k));
            assertEquals(p[1], RoadSearch.y(k));
            assertEquals(p[2], RoadSearch.z(k));
        }
    }

    @Test
    void aRoadReachesTheStopAtItsEndAndNoOther() {
        LongOpenHashSet road = new LongOpenHashSet();
        line(road, -3, 297, 64, 2);
        RoadSearch s = run(roads(road));
        assertTrue(s.reached(2));
        assertFalse(s.reached(3));
    }

    @Test
    void roadsClimbHillsAndTurnCornersDiagonally() {
        LongOpenHashSet road = new LongOpenHashSet();
        line(road, 0, 100, 64, 0);
        line(road, 101, 200, 65, 1);                 // one block up, diagonal step
        line(road, 201, 300, 66, 2);
        RoadSearch s = run(roads(road));
        assertTrue(s.reached(2));
    }

    @Test
    void aGapBreaksTheRoad() {
        LongOpenHashSet road = new LongOpenHashSet();
        line(road, 0, 150, 64, 0);
        line(road, 153, 300, 64, 0);
        assertFalse(run(roads(road)).reached(2));
    }

    @Test
    void noRoadAtTheStartFinishesAtOnce() {
        RoadSearch s = new RoadSearch(8);
        s.addTarget(2, 300, 65, 0);
        s.seed(roads(new LongOpenHashSet()), 0, 65, 0);
        assertTrue(s.isDone());
        assertFalse(s.reached(2));
    }

    @Test
    void anUnloadedChunkPausesTheSearchUntilItLoads() {
        LongOpenHashSet road = new LongOpenHashSet();
        line(road, 0, 300, 64, 0);
        boolean[] loaded = {false};
        RoadSearch.Ground ground = (x, y, z) -> x > 100 && !loaded[0] ? RoadSearch.NOT_LOADED
                : road.contains(RoadSearch.pack(x, y, z)) ? RoadSearch.ROAD : RoadSearch.NOT_ROAD;
        RoadSearch s = new RoadSearch(8);
        s.addTarget(2, 300, 65, 0);
        s.seed(ground, 0, 65, 0);
        for (int i = 0; i < 50; i++) assertFalse(s.step(ground, 1000));
        assertFalse(s.reached(2));
        loaded[0] = true;
        while (!s.step(ground, 1000)) { }
        assertTrue(s.reached(2));
    }
}
