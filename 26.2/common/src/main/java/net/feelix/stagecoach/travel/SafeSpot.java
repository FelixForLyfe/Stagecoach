package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

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

    public static Vec3 near(ServerLevel level, BlockPos coach, Direction facing) {
        return around(level, coach.relative(facing, FRONT));
    }

    /** Falls back to the top of the ground there. */
    public static Vec3 around(ServerLevel level, BlockPos centre) {
        for (int[] o : OFFSETS) {
            BlockPos feet = centre.offset(o[0], o[1], o[2]);
            double floorTop = standHeight(level, feet);
            if (!Double.isNaN(floorTop)) return new Vec3(feet.getX() + 0.5, floorTop, feet.getZ() + 0.5);
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
        return Vec3.atBottomCenterOf(top);
    }

    /** NaN if the spot is not safe. */
    private static double standHeight(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(feet.above())) return Double.NaN;
        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        if (isCoach(floor) || floor.is(Blocks.MAGMA_BLOCK) || !floor.isFaceSturdy(level, floorPos, Direction.UP)) {
            return Double.NaN;
        }
        if (!isOpen(level, feet) || !isOpen(level, feet.above())) return Double.NaN;
        double y = floorPos.getY() + floor.getCollisionShape(level, floorPos).max(Direction.Axis.Y);
        // the whole player box must be clear, or a spot beside the coach clips into it
        if (!level.noCollision(EntityTypes.PLAYER.getDimensions().makeBoundingBox(feet.getX() + 0.5, y, feet.getZ() + 0.5))) return Double.NaN;
        return y;
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !isCoach(s) && s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty()
                && !s.is(BlockTags.FIRE) && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.SWEET_BERRY_BUSH)
                && !s.is(Blocks.WITHER_ROSE) && !s.is(Blocks.CACTUS);
    }

    private static boolean isCoach(BlockState s) {
        return s.getBlock() instanceof StagecoachBlock || s.getBlock() instanceof StagecoachPartBlock;
    }
}
