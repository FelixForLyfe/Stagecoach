package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

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

    public static Vec3d near(ServerWorld world, BlockPos coach, Direction facing) {
        return around(world, coach.offset(facing, FRONT));
    }

    /** Falls back to the top of the ground there. */
    public static Vec3d around(ServerWorld world, BlockPos centre) {
        for (int[] o : OFFSETS) {
            BlockPos feet = centre.add(o[0], o[1], o[2]);
            double floorTop = standHeight(world, feet);
            if (!Double.isNaN(floorTop)) return new Vec3d(feet.getX() + 0.5, floorTop, feet.getZ() + 0.5);
        }
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre);
        return Vec3d.ofBottomCenter(top);
    }

    /** NaN if the spot is not safe. */
    private static double standHeight(ServerWorld world, BlockPos feet) {
        if (world.isOutOfHeightLimit(feet) || world.isOutOfHeightLimit(feet.up())) return Double.NaN;
        BlockPos floorPos = feet.down();
        BlockState floor = world.getBlockState(floorPos);
        if (isCoach(floor) || floor.isOf(Blocks.MAGMA_BLOCK) || !floor.isSideSolidFullSquare(world, floorPos, Direction.UP)) {
            return Double.NaN;
        }
        if (!isOpen(world, feet) || !isOpen(world, feet.up())) return Double.NaN;
        double y = floorPos.getY() + floor.getCollisionShape(world, floorPos).getMax(Direction.Axis.Y);
        // the whole player box must be clear, or a spot beside the coach clips into it
        if (!world.isSpaceEmpty(EntityType.PLAYER.getDimensions().getBoxAt(feet.getX() + 0.5, y, feet.getZ() + 0.5))) return Double.NaN;
        return y;
    }

    private static boolean isOpen(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return !isCoach(s) && s.getCollisionShape(world, pos).isEmpty() && s.getFluidState().isEmpty()
                && !s.isIn(BlockTags.FIRE) && !s.isOf(Blocks.POWDER_SNOW) && !s.isOf(Blocks.SWEET_BERRY_BUSH)
                && !s.isOf(Blocks.WITHER_ROSE) && !s.isOf(Blocks.CACTUS);
    }

    private static boolean isCoach(BlockState s) {
        return s.getBlock() instanceof StagecoachBlock || s.getBlock() instanceof StagecoachPartBlock;
    }
}
