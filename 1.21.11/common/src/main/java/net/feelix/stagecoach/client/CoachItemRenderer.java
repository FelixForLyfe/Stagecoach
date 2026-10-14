package net.feelix.stagecoach.client;

import com.mojang.math.Axis;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachFootprintData;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class CoachItemRenderer extends GeoItemRenderer<StagecoachItem> {
    private final StagecoachWood wood;

    public CoachItemRenderer(StagecoachWood wood) {
        super(new StagecoachModel<>());
        this.wood = wood;
    }

    @Override
    public void captureDefaultRenderState(StagecoachItem item, GeoItemRenderer.RenderData data, GeoRenderState state, float partialTick) {
        super.captureDefaultRenderState(item, data, state, partialTick);
        state.addGeckolibData(StagecoachModel.WOOD, wood);
    }

    /** The placed coach's turn and wheel lift, so the item looks exactly like the block. */
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
        super.adjustRenderPose(info);
        info.poseStack().mulPose(Axis.YP.rotationDegrees(Footprint.renderYawDegrees(0)));
        info.poseStack().translate(0, StagecoachFootprintData.Y_OFFSET_PX / 16f, 0);
    }
}
