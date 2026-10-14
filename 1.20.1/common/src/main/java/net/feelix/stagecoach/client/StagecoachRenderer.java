package net.feelix.stagecoach.client;

import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachFootprintData;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StagecoachRenderer extends GeoBlockRenderer<StagecoachBlockEntity> {
    public StagecoachRenderer() {
        super(new StagecoachModel());
    }

    /** Collision and model share Footprint's yaw and lift, so a regenerated model moves both together. */
    @Override
    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Footprint.renderYawDegrees(StagecoachShapes.turns(facing))));
        poseStack.translate(0, StagecoachFootprintData.Y_OFFSET_PX / 16f, 0);
    }

    /** Otherwise the coach vanishes once the controller's chunk section leaves the view. */
    @Override
    public boolean rendersOutsideBoundingBox(StagecoachBlockEntity blockEntity) {
        return true;
    }
}
