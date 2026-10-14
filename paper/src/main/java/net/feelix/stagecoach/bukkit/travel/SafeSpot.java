package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.model.CoachModel;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SafeSpot {
    private SafeSpot() {}

    /** Just past the shaft tip (2.8 blocks out). */
    private static final int FRONT = 4;
    private static final int RADIUS = 5;
    private static final int[][] OFFSETS;

    static {
        List<int[]> list = new ArrayList<>();
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int i = 0; i < dys.length; i++) list.add(new int[] {dx, dys[i], dz, dx * dx + dz * dz, i});
            }
        }
        // Nearest first; at equal distance, the same height first.
        list.sort(Comparator.<int[]>comparingInt(o -> o[3]).thenComparingInt(o -> o[4]));
        OFFSETS = list.toArray(new int[0][]);
    }

    public static Location near(NetworkState state, World world, int x, int y, int z, int turns) {
        int[] f = CoachModel.front(turns);
        return around(state, world, x + f[0] * FRONT, y, z + f[1] * FRONT);
    }

    /** Falls back to the top of the ground there. */
    public static Location around(NetworkState state, World world, int cx, int cy, int cz) {
        for (int[] o : OFFSETS) {
            int x = cx + o[0], y = cy + o[1], z = cz + o[2];
            double floorTop = standHeight(state, world, x, y, z);
            if (!Double.isNaN(floorTop)) return new Location(world, x + 0.5, floorTop, z + 0.5);
        }
        int top = world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, cx + 0.5, top + 1, cz + 0.5);
    }

    /** NaN if the spot is not safe. */
    private static double standHeight(NetworkState state, World w, int x, int y, int z) {
        if (y - 1 < w.getMinHeight() || y + 1 >= w.getMaxHeight()) return Double.NaN;
        Block floor = w.getBlockAt(x, y - 1, z);
        if (isCoach(state, floor) || floor.getType() == Material.MAGMA_BLOCK
                || !floor.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)) {
            return Double.NaN;
        }
        if (!isOpen(state, w.getBlockAt(x, y, z)) || !isOpen(state, w.getBlockAt(x, y + 1, z))) return Double.NaN;
        double top = floor.getY();
        for (BoundingBox b : floor.getCollisionShape().getBoundingBoxes()) top = Math.max(top, floor.getY() + b.getMaxY());
        // the whole player box must be clear, or a spot beside the coach clips into it
        BoundingBox player = new BoundingBox(x + 0.2, top, z + 0.2, x + 0.8, top + 1.8, z + 0.8);
        for (int by = (int) Math.floor(top); by <= (int) Math.floor(top + 1.8); by++) {
            Block b = w.getBlockAt(x, by, z);
            if (isCoach(state, b)) return Double.NaN;
            for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
                if (box.shift(x, by, z).overlaps(player)) return Double.NaN;
            }
        }
        return top;
    }

    private static boolean isOpen(NetworkState state, Block b) {
        if (isCoach(state, b) || !b.getCollisionShape().getBoundingBoxes().isEmpty()) return false;
        Material t = b.getType();
        BlockData d = b.getBlockData();
        if (b.isLiquid() || (d instanceof Waterlogged wl && wl.isWaterlogged())) return false;
        return !Tag.FIRE.isTagged(t) && t != Material.POWDER_SNOW && t != Material.SWEET_BERRY_BUSH
                && t != Material.WITHER_ROSE && t != Material.CACTUS && t != Material.BUBBLE_COLUMN
                && t != Material.SEAGRASS && t != Material.TALL_SEAGRASS && t != Material.KELP
                && t != Material.KELP_PLANT;
    }

    private static boolean isCoach(NetworkState state, Block b) {
        return state.cellAt(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()) != null;
    }
}
