package net.feelix.stagecoach.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** The order a jigsaw slot tries its pieces in, with the coach-stop pieces added. */
public final class StopOffers {
    private StopOffers() {}

    /** Guaranteed pieces first until a stop exists, weighted copies at {@code bound}'s random indexes, nothing after {@code max} stops. */
    public static <T> List<T> order(List<T> shuffled, List<T> guaranteed, List<T> weighted, int placed, int max,
                                    IntUnaryOperator bound) {
        if (placed >= max || guaranteed.isEmpty() && weighted.isEmpty()) return shuffled;
        List<T> out = new ArrayList<>(shuffled.size() + guaranteed.size() + weighted.size());
        if (placed == 0) out.addAll(guaranteed);
        int head = out.size();
        out.addAll(shuffled);
        for (T piece : weighted) out.add(head + bound.applyAsInt(out.size() - head + 1), piece);
        return out;
    }

    /** Piece {@code rank} of {@code count} weighs (count - rank) thousand pool totals, so the first is nearly always first; capped for int sums. */
    public static int guaranteedWeight(int poolTotal, int rank, int count) {
        long weight = (long) Math.max(1, poolTotal) * 1000L * (count - rank);
        return (int) Math.min(weight, 100_000_000L);
    }
}
