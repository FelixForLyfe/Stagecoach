package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.LeadItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Anything on a lead is tied to the coach as to a fence; a sneaking right-click takes the leads back. */
public final class Hitching {
    private Hitching() {}

    private static final double COACH_REACH = 7;
    private static final double LEAD_LENGTH = 10;

    public static boolean isCoach(BlockState state) {
        return state.getBlock() instanceof StagecoachBlock || state.getBlock() instanceof StagecoachPartBlock;
    }

    /** True when the click hitched or unhitched something, so the destination list stays shut. */
    public static boolean onUse(ServerWorld world, BlockPos clicked, BlockPos controller, ServerPlayerEntity player) {
        if (player.isSneaking()) return unhitch(world, controller, player);
        return LeadItem.attachHeldMobsToBlock(player, world, clicked).isAccepted();
    }

    private static boolean unhitch(ServerWorld world, BlockPos controller, ServerPlayerEntity player) {
        boolean any = false;
        for (LeashKnotEntity knot : world.getEntitiesByClass(LeashKnotEntity.class, new Box(controller).expand(COACH_REACH), k -> true)) {
            BlockPos at = knot.getDecorationBlockPos();
            BlockState state = world.getBlockState(at);
            if (!isCoach(state) || !controllerOf(at, state).equals(controller)) continue;
            for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, knot.getBoundingBox().expand(LEAD_LENGTH),
                    m -> m.getHoldingEntity() == knot)) {
                mob.attachLeash(player, true);
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
