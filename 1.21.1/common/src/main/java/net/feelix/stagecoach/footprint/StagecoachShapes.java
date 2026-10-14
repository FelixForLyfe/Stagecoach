package net.feelix.stagecoach.footprint;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class StagecoachShapes {
    private StagecoachShapes() {}

    /** [turns][cell]; built once, shared by every wood and state. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][Footprint.CELL_COUNT];

    static {
        for (int turns = 0; turns < 4; turns++) {
            for (int i = 0; i < Footprint.CELL_COUNT; i++) {
                VoxelShape shape = VoxelShapes.empty();
                for (int[] b : Footprint.boxes(i, turns)) {
                    shape = VoxelShapes.union(shape, Block.createCuboidShape(b[0], b[1], b[2], b[3], b[4], b[5]));
                }
                SHAPES[turns][i] = shape.simplify();
            }
        }
    }

    public static VoxelShape shape(Direction facing, int index) {
        return SHAPES[turns(facing)][index];
    }

    /** NORTH 0, EAST 1, SOUTH 2, WEST 3, as in {@link Footprint}. */
    public static int turns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public static BlockPos cellPos(BlockPos controller, Direction facing, int index) {
        int[] o = Footprint.offset(index, turns(facing));
        return controller.add(o[0], o[1], o[2]);
    }

    public static BlockPos controllerPos(BlockPos partPos, Direction facing, int index) {
        int[] o = Footprint.offset(index, turns(facing));
        return partPos.add(-o[0], -o[1], -o[2]);
    }
}
