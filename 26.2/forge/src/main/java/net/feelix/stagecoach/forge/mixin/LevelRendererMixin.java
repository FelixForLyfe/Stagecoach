package net.feelix.stagecoach.forge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.feelix.stagecoach.client.PlacementPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No event while submissions are collected, so the ghost goes in at the end of submitFeatures. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Unique
    private PoseStack stagecoach$pose;

    @ModifyExpressionValue(method = "submitFeatures", at = @At(value = "NEW", target = "Lcom/mojang/blaze3d/vertex/PoseStack;"))
    private PoseStack stagecoach$keepPose(PoseStack pose) {
        stagecoach$pose = pose;
        return pose;
    }

    @Inject(method = "submitFeatures", at = @At("RETURN"))
    private void stagecoach$submitGhost(LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        if (stagecoach$pose == null) return;
        PlacementPreview.submit(stagecoach$pose, collector, state.cameraRenderState,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
