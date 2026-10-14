package net.feelix.stagecoach.block;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.compat.ClaimCompat;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StagecoachStructure {
    private StagecoachStructure() {}

    public static final TagKey<Block> PLACEMENT_BLOCKED_ON = TagKey.of(RegistryKeys.BLOCK, Stagecoach.id("placement_blocked_on"));

    /** Set while the mod itself swaps a controller, so the old one's removal doesn't unregister the stop. */
    private static boolean rebuilding;

    public enum Reason { BUILD_LIMIT, BLOCKED, ENTITY, PROTECTED, GROUND }

    /** {@code what} names the block or entity in the way, else empty. */
    public record Refusal(Reason reason, BlockPos pos, Text what) {
        public Text message() {
            return switch (reason) {
                case BUILD_LIMIT -> Stagecoach.text("place.build_limit");
                case BLOCKED -> Stagecoach.text("place.blocked", what);
                case ENTITY -> Stagecoach.text("place.entity", what);
                case PROTECTED -> Stagecoach.text("place.protected");
                case GROUND -> Stagecoach.text("place.ground", what);
            };
        }
    }

    /** The one rule for placement and the preview; pass {@code blocked} to mark every blocked cell. */
    public static @Nullable Refusal check(ItemPlacementContext ctx, BlockState controller, boolean @Nullable [] blocked) {
        World world = ctx.getWorld();
        BlockPos origin = ctx.getBlockPos();
        Direction facing = controller.get(StagecoachBlock.FACING);
        PlayerEntity player = ctx.getPlayer();
        Refusal first = null;
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(origin, facing, i);
            Refusal r = checkCell(ctx, world, player, p, i, origin.getY());
            if (blocked != null) blocked[i] = r != null;
            if (r != null && first == null) {
                first = r;
                if (blocked == null) return first;
            }
        }
        return first;
    }

    private static @Nullable Refusal checkCell(ItemPlacementContext ctx, World world, @Nullable PlayerEntity player, BlockPos p, int index,
                                               int bottomY) {
        if (index != Footprint.CONTROLLER) {
            if (!world.isInBuildLimit(p)) return new Refusal(Reason.BUILD_LIMIT, p, Text.empty());
            BlockState there = world.getBlockState(p);
            if (!there.canReplace(ItemPlacementContext.offset(ctx, p, Direction.UP))) {
                boolean coach = there.getBlock() instanceof StagecoachBlock || there.getBlock() instanceof StagecoachPartBlock;
                return new Refusal(Reason.BLOCKED, p, coach ? Stagecoach.text("place.another") : there.getBlock().getName());
            }
            if (player != null && !world.canPlayerModifyAt(player, p)) return new Refusal(Reason.PROTECTED, p, Text.empty());
            if (player instanceof ServerPlayerEntity sp && ClaimCompat.prevents(sp, p)) return new Refusal(Reason.PROTECTED, p, Text.empty());
        }
        if (p.getY() == bottomY) {
            BlockState ground = world.getBlockState(p.down());
            if (ground.isIn(PLACEMENT_BLOCKED_ON)) return new Refusal(Reason.GROUND, p, ground.getBlock().getName());
        }
        List<Entity> inTheWay = world.getOtherEntities(null, new Box(p), e -> !e.isRemoved() && e.intersectionChecked);
        if (!inTheWay.isEmpty()) return new Refusal(Reason.ENTITY, p, inTheWay.get(0).getDisplayName());
        return null;
    }

    /** For commands: every cell, the controller's too, must be free (replaceable, no entity) and on allowed ground. */
    public static @Nullable Refusal checkClear(World world, BlockPos origin, Direction facing) {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(origin, facing, i);
            if (!world.isInBuildLimit(p)) return new Refusal(Reason.BUILD_LIMIT, p, Text.empty());
            BlockState there = world.getBlockState(p);
            if (!there.isReplaceable()) {
                boolean coach = there.getBlock() instanceof StagecoachBlock || there.getBlock() instanceof StagecoachPartBlock;
                return new Refusal(Reason.BLOCKED, p, coach ? Stagecoach.text("place.another") : there.getBlock().getName());
            }
            if (p.getY() == origin.getY() && world.getBlockState(p.down()).isIn(PLACEMENT_BLOCKED_ON)) {
                return new Refusal(Reason.GROUND, p, world.getBlockState(p.down()).getBlock().getName());
            }
            List<Entity> inTheWay = world.getOtherEntities(null, new Box(p), e -> !e.isRemoved() && e.intersectionChecked);
            if (!inTheWay.isEmpty()) return new Refusal(Reason.ENTITY, p, inTheWay.get(0).getDisplayName());
        }
        return null;
    }

    public static void placeParts(World world, BlockPos controllerPos, BlockState controller) {
        Direction facing = controller.get(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            world.setBlockState(StagecoachShapes.cellPos(controllerPos, facing, i), partState(controller, i), Block.NOTIFY_ALL);
        }
    }

    /** {@code stop create ... force}: clears the footprint, then places the coach without any checks. */
    public static void placeForced(ServerWorld world, BlockPos pos, BlockState controller) {
        Direction facing = controller.get(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(pos, facing, i);
            if (world.isInBuildLimit(p)) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.setBlockState(pos, controller, Block.NOTIFY_ALL);
        placeParts(world, pos, controller);
    }

    /** Only parts whose index, facing and wood point back here; parts drop nothing. */
    public static void removeParts(WorldAccess world, BlockPos controllerPos, BlockState controller) {
        Direction facing = controller.get(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(controllerPos, facing, i);
            BlockState s = world.getBlockState(p);
            if (s.isOf(StagecoachRegistry.PART) && s.get(StagecoachPartBlock.PART) == i
                    && s.get(StagecoachPartBlock.FACING) == facing
                    && s.get(StagecoachPartBlock.WOOD) == ((StagecoachBlock) controller.getBlock()).wood()) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    /** Takes a coach away without drops, leaving whatever surrounds it. */
    public static void removeCoach(ServerWorld world, BlockPos pos, BlockState controller) {
        if (world.getBlockEntity(pos) instanceof StagecoachBlockEntity be) be.setDropsNothing(true);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        removeParts(world, pos, controller);
    }

    /** Lays every part again around a controller in {@code wood}, so a turned or mirrored template comes out whole; taken cells stay. */
    public static StagecoachBlockEntity rebuild(ServerWorld world, BlockPos pos, BlockState state, StagecoachWood wood) {
        BlockState controller = state;
        if (((StagecoachBlock) state.getBlock()).wood() != wood) {
            controller = StagecoachRegistry.BLOCKS.get(wood).getDefaultState().with(StagecoachBlock.FACING, state.get(StagecoachBlock.FACING));
            rebuilding = true;
            try {
                world.setBlockState(pos, controller, Block.NOTIFY_LISTENERS);
            } finally {
                rebuilding = false;
            }
        }
        Direction facing = controller.get(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(pos, facing, i);
            if (!world.isInBuildLimit(p)) continue;
            BlockState there = world.getBlockState(p);
            if (there.isOf(StagecoachRegistry.PART) || there.isReplaceable()) {
                world.setBlockState(p, partState(controller, i), Block.NOTIFY_LISTENERS);
            }
        }
        return (StagecoachBlockEntity) world.getBlockEntity(pos);
    }

    public static boolean isRebuilding() {
        return rebuilding;
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState part) {
        return StagecoachShapes.controllerPos(partPos, part.get(StagecoachPartBlock.FACING), part.get(StagecoachPartBlock.PART));
    }

    public static boolean hasController(World world, BlockPos partPos, BlockState part) {
        BlockState c = world.getBlockState(controllerPos(partPos, part));
        return c.getBlock() instanceof StagecoachBlock sb
                && sb.wood() == part.get(StagecoachPartBlock.WOOD)
                && c.get(StagecoachBlock.FACING) == part.get(StagecoachPartBlock.FACING);
    }

    public static BlockState partState(BlockState controller, int index) {
        return StagecoachRegistry.PART.getDefaultState()
                .with(StagecoachPartBlock.FACING, controller.get(StagecoachBlock.FACING))
                .with(StagecoachPartBlock.WOOD, ((StagecoachBlock) controller.getBlock()).wood())
                .with(StagecoachPartBlock.PART, index);
    }
}
