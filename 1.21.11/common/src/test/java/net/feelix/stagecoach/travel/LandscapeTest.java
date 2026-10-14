package net.feelix.stagecoach.travel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandscapeTest {
    @Test
    void theSameJourneyAlwaysDrawsTheSameRoad() {
        for (long cell = -50; cell < 50; cell++) {
            assertEquals(Landscape.hasTree(42, cell), Landscape.hasTree(42, cell));
            assertEquals(Landscape.treeKind(42, cell), Landscape.treeKind(42, cell));
            assertEquals(Landscape.roadside(42, cell), Landscape.roadside(42, cell));
        }
        assertEquals(Landscape.hill(42, Landscape.LAYER_FAR_HILLS, 1234.5, 150),
                Landscape.hill(42, Landscape.LAYER_FAR_HILLS, 1234.5, 150));
    }

    @Test
    void anotherJourneyDrawsAnotherRoad() {
        int differences = 0;
        for (long cell = 0; cell < 100; cell++) {
            if (Landscape.hasTree(1, cell) != Landscape.hasTree(2, cell)) differences++;
        }
        assertTrue(differences > 10, "trees should move with the seed: " + differences);
        assertNotEquals(Landscape.hill(1, Landscape.LAYER_NEAR_HILLS, 500, 85), Landscape.hill(2, Landscape.LAYER_NEAR_HILLS, 500, 85));
    }

    @Test
    void everyValueStaysInItsRange() {
        int trees = 0;
        for (long cell = -1000; cell < 1000; cell++) {
            double u = Landscape.unit(7, Landscape.LAYER_ROAD, cell);
            assertTrue(u >= 0 && u < 1);
            int kind = Landscape.treeKind(7, cell);
            assertTrue(kind >= 0 && kind <= 2);
            int height = Landscape.treeHeight(7, cell);
            assertTrue(height >= 26 && height < 46);
            int offset = Landscape.treeOffset(7, cell);
            assertTrue(offset >= 0 && offset < Landscape.TREE_CELL);
            int post = Landscape.roadside(7, cell);
            assertTrue(post >= 0 && post <= 2);
            int postOffset = Landscape.roadsideOffset(7, cell);
            assertTrue(postOffset >= 0 && postOffset < Landscape.POST_CELL);
            int tile = Landscape.roadTile(7, cell);
            assertTrue(tile >= 0 && tile <= 2);
            double hill = Landscape.hill(7, Landscape.LAYER_FAR_HILLS, cell * 3.7, 150);
            assertTrue(hill >= 0 && hill <= 1, "hill " + hill);
            if (Landscape.hasTree(7, cell)) trees++;
        }
        assertTrue(trees > 1100 && trees < 1600, "about two thirds of the slots hold a tree: " + trees);
    }

    @Test
    void hillsAreSmoothFromOnePixelToTheNext() {
        for (double x = 0; x < 2000; x += 1) {
            double step = Math.abs(Landscape.hill(9, Landscape.LAYER_FAR_HILLS, x + 1, 150) - Landscape.hill(9, Landscape.LAYER_FAR_HILLS, x, 150));
            assertTrue(step < 0.05, "a jump of " + step + " at x " + x);
        }
    }
}
