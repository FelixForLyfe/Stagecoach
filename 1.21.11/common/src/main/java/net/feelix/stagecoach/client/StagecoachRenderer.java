package net.feelix.stagecoach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachFootprintData;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class StagecoachRenderer extends GeoBlockRenderer<StagecoachBlockEntity, StagecoachRenderState> {
    public StagecoachRenderer(BlockEntityRendererProvider.Context context) {
        super(new StagecoachModel<>());
    }

    @Override
    public StagecoachRenderState createRenderState() {
        return new StagecoachRenderState();
    }

    @Override
    public void captureDefaultRenderState(StagecoachBlockEntity coach, Void unused, StagecoachRenderState state, float partialTick) {
        super.captureDefaultRenderState(coach, unused, state, partialTick);
        if (coach.getBlockState().getBlock() instanceof StagecoachBlock block) state.addGeckolibData(StagecoachModel.WOOD, block.wood());
    }

    /** Collision and model share Footprint's yaw and lift, so a regenerated model moves both together. */
    @Override
    protected void tryRotateByBlockstate(RenderPassInfo<StagecoachRenderState> info, PoseStack poseStack) {
        Direction facing = info.getOrDefaultGeckolibData(DataTickets.BLOCK_FACING, Direction.NORTH);
        poseStack.mulPose(Axis.YP.rotationDegrees(Footprint.renderYawDegrees(StagecoachShapes.turns(facing))));
        poseStack.translate(0, StagecoachFootprintData.Y_OFFSET_PX / 16f, 0);
    }

    /** Otherwise the coach vanishes once the controller's chunk section leaves the view. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
