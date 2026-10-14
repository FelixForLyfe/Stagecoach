package net.feelix.stagecoach.bukkit.model;

import org.joml.Matrix4f;

/** One clockwise turn maps (x, z) to (-z, x): JOML's -90 degrees about +Y, as {@link #matrix} uses. */
public final class CoachModel {
    private CoachModel() {}

    public static final int CONTROLLER = 0;
    public static final int CELL_COUNT = CoachModelData.CELLS.length;

    public static int[] offset(int index, int turns) {
        int[] c = CoachModelData.CELLS[index];
        int x = c[0], z = c[2];
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[] {x, c[1], z};
    }

    /** Else the cell is kept empty. */
    public static boolean barrier(int index) {
        return CoachModelData.CELLS[index][3] == 1;
    }

    /** {dx, dz} one block toward the front. */
    public static int[] front(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> new int[] {0, -1};
            case 1 -> new int[] {1, 0};
            case 2 -> new int[] {0, 1};
            default -> new int[] {-1, 0};
        };
    }

    /** Minecraft yaw: south 0, west 90, north 180, east 270. */
    public static float frontYaw(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> 180f;
            case 1 -> 270f;
            case 2 -> 0f;
            default -> 90f;
        };
    }

    /** Relative to a display standing at the bottom centre of the controller block. */
    public static Matrix4f matrix(CoachModelData.Cube cube, int turns) {
        float[] m = cube.m();
        // JOML takes columns; the generated data is row-major 3x4
        Matrix4f local = new Matrix4f(
                m[0], m[4], m[8], 0f,
                m[1], m[5], m[9], 0f,
                m[2], m[6], m[10], 0f,
                m[3], m[7], m[11], 1f);
        return new Matrix4f().rotationY((float) Math.toRadians(-90.0 * Math.floorMod(turns, 4))).mul(local);
    }

    /** The client turns an item display 180 degrees about Y and centres it at -0.5; both are undone here. */
    public static Matrix4f displayMatrix(CoachModelData.Cube cube, int turns) {
        return matrix(cube, turns).translate(0.5f, 0.5f, 0.5f).rotateY((float) Math.PI);
    }

    /** A hair larger than the cell, so it never z-fights. */
    public static Matrix4f cellDisplayMatrix(int index, int turns) {
        int[] o = offset(index, turns);
        float grow = 0.004f;
        return new Matrix4f().translation(o[0] - 0.5f - grow, o[1] - grow, o[2] - 0.5f - grow)
                .scale(1 + 2 * grow).translate(0.5f, 0.5f, 0.5f).rotateY((float) Math.PI);
    }
}
