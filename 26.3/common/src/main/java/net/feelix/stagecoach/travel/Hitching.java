package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Anything on a lead is tied to the coach as to a fence; a sneaking right-click takes the leads back. */
public final class Hitching {
    private Hitching() {}

    private static final double COACH_REACH = 7;

    public static boolean isCoach(BlockState state) {
        return state.getBlock() instanceof StagecoachBlock || state.getBlock() instanceof StagecoachPartBlock;
    }

    /** True when the click hitched or unhitched something, so the destination list stays shut. */
    public static boolean onUse(ServerLevel level, BlockPos clicked, BlockPos controller, ServerPlayer player) {
        if (player.isShiftKeyDown()) return unhitch(level, controller, player);
        return LeadItem.bindPlayerMobs(player, level, clicked).consumesAction();
    }

    private static boolean unhitch(ServerLevel level, BlockPos controller, ServerPlayer player) {
        boolean any = false;
        for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(controller).inflate(COACH_REACH), k -> true)) {
            BlockPos at = knot.getPos();
            BlockState state = level.getBlockState(at);
            if (!isCoach(state) || !controllerOf(at, state).equals(controller)) continue;
            for (Leashable mob : Leashable.leashableLeashedTo(knot)) {
                mob.setLeashedTo(player, true);
                any = true;
            }
            knot.discard();
        }
        return any;
    }

    static BlockPos controllerOf(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof StagecoachPartBlock ? StagecoachStructure.controllerPos(pos, state) : pos;
    }
}
