package net.feelix.stagecoach.worldgen;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopOffersTest {
    private static final List<String> HOUSES = List.of("house_a", "house_b", "empty", "house_c");

    @Test
    void aVillageWithoutAStopTriesTheGuaranteedPieceFirst() {
        List<String> out = StopOffers.order(HOUSES, List.of("stop"), List.of(), 0, 1, n -> 0);
        assertEquals(List.of("stop", "house_a", "house_b", "empty", "house_c"), out);
    }

    @Test
    void onceAVillageHasItsStopsNothingIsAdded() {
        assertSame(HOUSES, StopOffers.order(HOUSES, List.of("stop"), List.of("tavern", "tavern"), 1, 1, n -> 0));
        assertSame(HOUSES, StopOffers.order(HOUSES, List.of(), List.of(), 0, 1, n -> 0));
    }

    @Test
    void aSecondStopCanOnlyComeFromTheWeightedPieces() {
        List<String> out = StopOffers.order(HOUSES, List.of("stop"), List.of("tavern"), 1, 2, n -> n - 1);
        assertEquals(List.of("house_a", "house_b", "empty", "house_c", "tavern"), out);
    }

    @Test
    void weightedCopiesLandAnywhereAmongThePoolsPieces() {
        Random random = new Random(7);
        int[] seen = new int[HOUSES.size() + 1];
        for (int i = 0; i < 2000; i++) {
            List<String> out = StopOffers.order(HOUSES, List.of(), List.of("tavern"), 0, 1, random::nextInt);
            assertEquals(HOUSES.size() + 1, out.size());
            seen[out.indexOf("tavern")]++;
        }
        for (int count : seen) assertTrue(count > 300, "every position should come up about equally: " + Arrays.toString(seen));
    }

    @Test
    void guaranteedPiecesStayAheadOfWeightedOnes() {
        List<String> out = StopOffers.order(HOUSES, List.of("stop"), List.of("tavern"), 0, 1, n -> 0);
        assertEquals("stop", out.get(0));
        assertEquals("tavern", out.get(1));
    }

    @Test
    void weightedAssemblersAlmostAlwaysTryTheStopFirst() {
        int pool = 87;
        int first = StopOffers.guaranteedWeight(pool, 0, 2);
        int second = StopOffers.guaranteedWeight(pool, 1, 2);
        assertTrue(first > second && second > pool * 999, first + " / " + second);
        assertTrue((double) (first + second) / (first + second + pool) > 0.9995);
        assertEquals(100_000_000, StopOffers.guaranteedWeight(Integer.MAX_VALUE, 0, 2), "never overflows the assembler's sum");
        assertTrue(StopOffers.guaranteedWeight(0, 0, 1) > 0, "an empty pool still gets a weight");
    }
}
