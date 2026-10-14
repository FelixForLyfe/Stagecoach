package net.feelix.stagecoach.neoforge;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

final class NeoForgeCoachItemRenderer extends BuiltinModelItemRenderer {
    private final StagecoachBlock block;
    private StagecoachBlockEntity dummy;

    NeoForgeCoachItemRenderer(StagecoachBlock block) {
        super(MinecraftClient.getInstance().getBlockEntityRenderDispatcher(), MinecraftClient.getInstance().getEntityModelLoader());
        this.block = block;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (dummy == null) dummy = new StagecoachBlockEntity(BlockPos.ORIGIN, block.getDefaultState());
        MinecraftClient.getInstance().getBlockEntityRenderDispatcher().renderEntity(dummy, matrices, vertexConsumers, light, overlay);
    }
}
