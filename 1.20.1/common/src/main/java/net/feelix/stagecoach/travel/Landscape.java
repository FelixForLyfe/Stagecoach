package net.feelix.stagecoach.travel;

/** Everything is a pure function of (seed, x), so layers scroll forever and every journey differs. */
public final class Landscape {
    private Landscape() {}

    public static final int LAYER_FAR_HILLS = 1;
    public static final int LAYER_NEAR_HILLS = 2;
    public static final int LAYER_TREES = 3;
    public static final int LAYER_ROAD = 4;

    /** At most one tree per slot. */
    public static final int TREE_CELL = 34;
    /** At most one fence post or signpost per slot. */
    public static final int POST_CELL = 26;

    /** SplitMix64 finaliser. */
    public static long hash(long seed, int layer, long cell) {
        long z = seed ^ (layer * 0x9E3779B97F4A7C15L) ^ (cell * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    public static double unit(long seed, int layer, long cell) {
        return (hash(seed, layer, cell) >>> 11) * 0x1.0p-53;
    }

    /** Cosine-interpolated value noise plus a half-amplitude octave, in [0, 1]. */
    public static double hill(long seed, int layer, double x, double wavelength) {
        double a = valueNoise(seed, layer, x / wavelength);
        double b = valueNoise(seed, layer + 100, x / (wavelength * 0.5));
        return (a * 2.0 + b) / 3.0;
    }

    private static double valueNoise(long seed, int layer, double t) {
        long i = (long) Math.floor(t);
        double f = t - i;
        double w = (1 - Math.cos(f * Math.PI)) * 0.5;
        double v0 = unit(seed, layer, i), v1 = unit(seed, layer, i + 1);
        return v0 + (v1 - v0) * w;
    }

    /** About two slots in three. */
    public static boolean hasTree(long seed, long cell) {
        return unit(seed, LAYER_TREES, cell * 4) < 0.68;
    }

    /** 0..TREE_CELL/2. */
    public static int treeOffset(long seed, long cell) {
        return (int) (unit(seed, LAYER_TREES, cell * 4 + 1) * (TREE_CELL / 2));
    }

    /** 0 = oak (round crown), 1 = spruce (tiered crown), 2 = birch (tall, narrow). */
    public static int treeKind(long seed, long cell) {
        double u = unit(seed, LAYER_TREES, cell * 4 + 2);
        return u < 0.45 ? 0 : u < 0.8 ? 1 : 2;
    }

    /** 26..45 GUI px. */
    public static int treeHeight(long seed, long cell) {
        return 26 + (int) (unit(seed, LAYER_TREES, cell * 4 + 3) * 20);
    }

    /** 0 = nothing, 1 = fence post, 2 = signpost (rare). */
    public static int roadside(long seed, long cell) {
        double u = unit(seed, LAYER_ROAD, cell);
        return u < 0.06 ? 2 : u < 0.36 ? 1 : 0;
    }

    public static int roadsideOffset(long seed, long cell) {
        return (int) (unit(seed, LAYER_ROAD, cell + 7_777_777L) * (POST_CELL - 6));
    }

    /** 0 plain path, 1 coarse-dirt patch, 2 gravel patch. */
    public static int roadTile(long seed, long tile) {
        double u = unit(seed, LAYER_ROAD, tile * 3 + 11);
        return u < 0.08 ? 1 : u < 0.12 ? 2 : 0;
    }
}
