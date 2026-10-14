package net.feelix.stagecoach.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class StagecoachItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private final StagecoachBlockEntity dummy;

    public StagecoachItemRenderer(StagecoachBlock block) {
        this.dummy = new StagecoachBlockEntity(BlockPos.ORIGIN, block.getDefaultState());
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient.getInstance().getBlockEntityRenderDispatcher().renderEntity(dummy, matrices, vertexConsumers, light, overlay);
    }
}
