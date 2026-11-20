package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.config.StagecoachConfig;

public final class TravelMath {
    private TravelMath() {}

    public static double horizontalDistance(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int cost(double distance, StagecoachConfig c) {
        return price(distance, c.baseCost, c.blocksPerLevel, c.maxCost);
    }

    public static int itemCost(double distance, StagecoachConfig c) {
        return price(distance, c.itemBaseCost, c.blocksPerItem, c.maxItemCost);
    }

    /** Before multipliers: the base plus one unit per started stretch of blocks. */
    public static long rawCost(double distance, StagecoachConfig c, boolean items) {
        return items ? (long) c.itemBaseCost + (long) Math.ceil(distance / c.blocksPerItem)
                : (long) c.baseCost + (long) Math.ceil(distance / c.blocksPerLevel);
    }

    /** The tiny allowance keeps 10 x 0.3 at 3 instead of rounding it up to 4. */
    public static int fare(long raw, double multiplier, int max) {
        double v = Math.ceil(raw * multiplier - 1e-9);
        return (int) Math.max(0, Math.min(v, max));
    }

    private static int price(double distance, int base, int blocksPerUnit, int max) {
        long raw = (long) base + (long) Math.ceil(distance / blocksPerUnit);
        return (int) Math.min(raw, max);
    }

    /** maxDistance 0 means no limit. */
    public static boolean tooFar(double distance, StagecoachConfig c) {
        return c.maxDistance > 0 && distance > c.maxDistance;
    }

    /** Rounded down, so a refund never pays out more than it took. */
    public static int refundShare(int paid, int percent) {
        if (percent >= 100) return paid;
        if (percent <= 0 || paid <= 0) return 0;
        return (int) ((long) paid * percent / 100);
    }

    public static double seconds(double distance, StagecoachConfig c) {
        double s = distance / c.blocksPerSecond;
        return Math.max(c.minTravelSeconds, Math.min(c.maxTravelSeconds, s));
    }

    /** Rounded up so a journey is never shorter than stated. */
    public static int ticks(double distance, StagecoachConfig c) {
        return (int) Math.ceil(seconds(distance, c) * 20.0);
    }

    /** Rounds up so the display only reaches 0:00 on arrival. */
    public static String clock(long ticks) {
        long secs = Math.max(0, (ticks + 19) / 20);
        return (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
    }

    /** Rolled once at the start; never on a trip shorter than ambushMinDistance. */
    public static boolean ambushes(double distance, StagecoachConfig c, double roll) {
        return c.ambushChancePercent > 0 && distance >= c.ambushMinDistance && roll * 100 < c.ambushChancePercent;
    }

    /** Somewhere in the middle of the road: 35% to 65% of the way. */
    public static long ambushTick(long start, int ticks, double roll) {
        return start + Math.round(ticks * (0.35 + 0.3 * roll));
    }

    /** The share of the fare for the road not ridden, rounded down. */
    public static int unriddenPercent(long start, int ticks, long now) {
        if (ticks <= 0) return 0;
        long left = Math.max(0, start + ticks - now);
        return (int) Math.min(100, left * 100 / ticks);
    }
}
