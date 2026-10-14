package net.feelix.stagecoach.block;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.compat.ClaimCompat;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StagecoachStructure {
    private StagecoachStructure() {}

    public static final TagKey<Block> PLACEMENT_BLOCKED_ON = TagKey.create(Registries.BLOCK, Stagecoach.id("placement_blocked_on"));

    /** Set while the mod itself swaps a controller, so the old one's removal doesn't unregister the stop. */
    private static boolean rebuilding;

    public enum Reason { BUILD_LIMIT, BLOCKED, ENTITY, PROTECTED, GROUND }

    /** {@code what} names the block or entity in the way, else empty. */
    public record Refusal(Reason reason, BlockPos pos, Component what) {
        public Component message() {
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
    public static @Nullable Refusal check(BlockPlaceContext ctx, BlockState controller, boolean @Nullable [] blocked) {
        Level level = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos();
        Direction facing = controller.getValue(StagecoachBlock.FACING);
        Player player = ctx.getPlayer();
        Refusal first = null;
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(origin, facing, i);
            Refusal r = checkCell(ctx, level, player, p, i, origin.getY());
            if (blocked != null) blocked[i] = r != null;
            if (r != null && first == null) {
                first = r;
                if (blocked == null) return first;
            }
        }
        return first;
    }

    private static @Nullable Refusal checkCell(BlockPlaceContext ctx, Level level, @Nullable Player player, BlockPos p, int index,
                                               int bottomY) {
        if (index != Footprint.CONTROLLER) {
            if (!level.isInWorldBounds(p)) return new Refusal(Reason.BUILD_LIMIT, p, Component.empty());
            BlockState there = level.getBlockState(p);
            if (!there.canBeReplaced(BlockPlaceContext.at(ctx, p, Direction.UP))) {
                boolean coach = there.getBlock() instanceof StagecoachBlock || there.getBlock() instanceof StagecoachPartBlock;
                return new Refusal(Reason.BLOCKED, p, coach ? Stagecoach.text("place.another") : there.getBlock().getName());
            }
            if (player != null && !level.mayInteract(player, p)) return new Refusal(Reason.PROTECTED, p, Component.empty());
            if (player instanceof ServerPlayer sp && ClaimCompat.prevents(sp, p)) return new Refusal(Reason.PROTECTED, p, Component.empty());
        }
        if (p.getY() == bottomY) {
            BlockState ground = level.getBlockState(p.below());
            if (ground.is(PLACEMENT_BLOCKED_ON)) return new Refusal(Reason.GROUND, p, ground.getBlock().getName());
        }
        List<Entity> inTheWay = level.getEntities((Entity) null, new AABB(p), e -> !e.isRemoved() && e.blocksBuilding);
        if (!inTheWay.isEmpty()) return new Refusal(Reason.ENTITY, p, inTheWay.get(0).getDisplayName());
        return null;
    }

    /** For commands: every cell, the controller's too, must be free (replaceable, no entity) and on allowed ground. */
    public static @Nullable Refusal checkClear(Level level, BlockPos origin, Direction facing) {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(origin, facing, i);
            if (!level.isInWorldBounds(p)) return new Refusal(Reason.BUILD_LIMIT, p, Component.empty());
            BlockState there = level.getBlockState(p);
            if (!there.canBeReplaced()) {
                boolean coach = there.getBlock() instanceof StagecoachBlock || there.getBlock() instanceof StagecoachPartBlock;
                return new Refusal(Reason.BLOCKED, p, coach ? Stagecoach.text("place.another") : there.getBlock().getName());
            }
            if (p.getY() == origin.getY() && level.getBlockState(p.below()).is(PLACEMENT_BLOCKED_ON)) {
                return new Refusal(Reason.GROUND, p, level.getBlockState(p.below()).getBlock().getName());
            }
            List<Entity> inTheWay = level.getEntities((Entity) null, new AABB(p), e -> !e.isRemoved() && e.blocksBuilding);
            if (!inTheWay.isEmpty()) return new Refusal(Reason.ENTITY, p, inTheWay.get(0).getDisplayName());
        }
        return null;
    }

    public static void placeParts(Level level, BlockPos controllerPos, BlockState controller) {
        Direction facing = controller.getValue(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            level.setBlock(StagecoachShapes.cellPos(controllerPos, facing, i), partState(controller, i), Block.UPDATE_ALL);
        }
    }

    /** {@code stop create ... force}: clears the footprint, then places the coach without any checks. */
    public static void placeForced(ServerLevel level, BlockPos pos, BlockState controller) {
        Direction facing = controller.getValue(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(pos, facing, i);
            if (level.isInWorldBounds(p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(pos, controller, Block.UPDATE_ALL);
        placeParts(level, pos, controller);
    }

    /** Only parts whose index, facing and wood point back here; parts drop nothing. */
    public static void removeParts(LevelAccessor level, BlockPos controllerPos, BlockState controller) {
        Direction facing = controller.getValue(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(controllerPos, facing, i);
            BlockState s = level.getBlockState(p);
            if (s.is(StagecoachRegistry.PART) && s.getValue(StagecoachPartBlock.PART) == i
                    && s.getValue(StagecoachPartBlock.FACING) == facing
                    && s.getValue(StagecoachPartBlock.WOOD) == ((StagecoachBlock) controller.getBlock()).wood()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Takes a coach away without drops, leaving whatever surrounds it. */
    public static void removeCoach(ServerLevel level, BlockPos pos, BlockState controller) {
        if (level.getBlockEntity(pos) instanceof StagecoachBlockEntity be) be.setDropsNothing(true);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        removeParts(level, pos, controller);
    }

    /** Lays every part again around a controller in {@code wood}, so a turned or mirrored template comes out whole; taken cells stay. */
    public static StagecoachBlockEntity rebuild(ServerLevel level, BlockPos pos, BlockState state, StagecoachWood wood) {
        BlockState controller = state;
        if (((StagecoachBlock) state.getBlock()).wood() != wood) {
            controller = StagecoachRegistry.BLOCKS.get(wood).defaultBlockState().setValue(StagecoachBlock.FACING, state.getValue(StagecoachBlock.FACING));
            rebuilding = true;
            try {
                level.setBlock(pos, controller, Block.UPDATE_CLIENTS);
            } finally {
                rebuilding = false;
            }
        }
        Direction facing = controller.getValue(StagecoachBlock.FACING);
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(pos, facing, i);
            if (!level.isInWorldBounds(p)) continue;
            BlockState there = level.getBlockState(p);
            if (there.is(StagecoachRegistry.PART) || there.canBeReplaced()) {
                level.setBlock(p, partState(controller, i), Block.UPDATE_CLIENTS);
            }
        }
        return (StagecoachBlockEntity) level.getBlockEntity(pos);
    }

    public static boolean isRebuilding() {
        return rebuilding;
    }

    public static BlockPos controllerPos(BlockPos partPos, BlockState part) {
        return StagecoachShapes.controllerPos(partPos, part.getValue(StagecoachPartBlock.FACING), part.getValue(StagecoachPartBlock.PART));
    }

    public static boolean hasController(Level level, BlockPos partPos, BlockState part) {
        BlockState c = level.getBlockState(controllerPos(partPos, part));
        return c.getBlock() instanceof StagecoachBlock sb
                && sb.wood() == part.getValue(StagecoachPartBlock.WOOD)
                && c.getValue(StagecoachBlock.FACING) == part.getValue(StagecoachPartBlock.FACING);
    }

    public static BlockState partState(BlockState controller, int index) {
        return StagecoachRegistry.PART.defaultBlockState()
                .setValue(StagecoachPartBlock.FACING, controller.getValue(StagecoachBlock.FACING))
                .setValue(StagecoachPartBlock.WOOD, ((StagecoachBlock) controller.getBlock()).wood())
                .setValue(StagecoachPartBlock.PART, index);
    }
}
