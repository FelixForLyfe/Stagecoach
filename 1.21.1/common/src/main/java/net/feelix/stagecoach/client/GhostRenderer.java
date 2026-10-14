package net.feelix.stagecoach.client;

import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.util.Color;

final class GhostRenderer extends StagecoachRenderer {
    static final Color FITS = Color.ofRGBA(0.45f, 1.0f, 0.5f, 0.45f);
    static final Color BLOCKED = Color.ofRGBA(1.0f, 0.3f, 0.25f, 0.45f);

    Color tint = FITS;

    @Override
    public RenderLayer getRenderType(StagecoachBlockEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(StagecoachBlockEntity animatable, float partialTick, int packedLight) {
        return tint;
    }
}
