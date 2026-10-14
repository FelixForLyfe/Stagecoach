package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.config.StagecoachConfig;

public final class TravelMath {
    private TravelMath() {}

    public static double horizontalDistance(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int cost(double distance, StagecoachConfig c) {
        return price(distance, c.baseCost(), c.blocksPerLevel(), c.maxCost());
    }

    public static int itemCost(double distance, StagecoachConfig c) {
        return price(distance, c.itemBaseCost(), c.blocksPerItem(), c.maxItemCost());
    }

    private static int price(double distance, int base, int blocksPerUnit, int max) {
        long raw = (long) base + (long) Math.ceil(distance / blocksPerUnit);
        return (int) Math.min(raw, max);
    }

    /** maxDistance 0 means no limit. */
    public static boolean tooFar(double distance, StagecoachConfig c) {
        return c.maxDistance() > 0 && distance > c.maxDistance();
    }

    /** Rounded down, so a refund never pays out more than it took. */
    public static int refundShare(int paid, int percent) {
        return (int) ((long) paid * percent / 100);
    }

    public static double seconds(double distance, StagecoachConfig c) {
        double s = distance / c.blocksPerSecond();
        return Math.max(c.minTravelSeconds(), Math.min(c.maxTravelSeconds(), s));
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
}
