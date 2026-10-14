package net.feelix.stagecoach.client;

import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

final class GhostRenderer extends StagecoachRenderer {
    /** ARGB: pale green / red at 45% opacity. */
    static final int FITS = 0x7373FF80;
    static final int BLOCKED = 0x73FF4D40;

    int tint = FITS;

    GhostRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public RenderType getRenderType(StagecoachRenderState state, Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    @Override
    public int getRenderColor(StagecoachBlockEntity coach, Void unused, float partialTick) {
        return tint;
    }
}
