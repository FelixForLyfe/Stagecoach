package net.feelix.stagecoach.footprint;

/** One clockwise turn maps (x, z) to (-z, x): JOML's -90 degrees about +Y, as the renderer uses. */
public final class Footprint {
    private Footprint() {}

    public static final int CONTROLLER = 0;
    public static final int CELL_COUNT = StagecoachFootprintData.CELLS.length;

    public static int[] offset(int index, int turns) {
        int[] c = StagecoachFootprintData.CELLS[index];
        int x = c[0], z = c[2];
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[] {x, c[1], z};
    }

    /** {x1, y1, z1, x2, y2, z2} in pixels, 0..16 inside the cell. */
    public static int[][] boxes(int index, int turns) {
        int[][] src = StagecoachFootprintData.BOXES[index];
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) out[i] = rotateBox(src[i], turns);
        return out;
    }

    public static int[] rotateBox(int[] b, int turns) {
        int x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int nx1 = 16 - z2, nx2 = 16 - z1, nz1 = x1, nz2 = x2;
            x1 = nx1; x2 = nx2; z1 = nz1; z2 = nz2;
        }
        return new int[] {x1, b[1], z1, x2, b[4], z2};
    }

    /** Applied after GeckoLib's translate(0.5, 0, 0.5). */
    public static float renderYawDegrees(int turns) {
        return StagecoachFootprintData.BASE_YAW_DEGREES - 90f * Math.floorMod(turns, 4);
    }
}
