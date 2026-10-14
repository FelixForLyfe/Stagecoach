package net.feelix.stagecoach.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.feelix.stagecoach.client.PlacementPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No event while submissions are collected, so the ghost goes in after the block entities. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitBlockEntities", at = @At("RETURN"))
    private void stagecoach$submitGhost(PoseStack pose, LevelRenderState state, SubmitNodeStorage storage, CallbackInfo ci) {
        PlacementPreview.submit(pose, storage, state.cameraRenderState, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
