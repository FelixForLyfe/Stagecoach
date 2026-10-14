package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerEntityMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void stagecoach$keepOwnCameraOnTheRoad(@Nullable Entity entity, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (entity != null && entity != self && JourneyManager.isTravelling(self.getUUID())) ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void stagecoach$writeJourney(ValueOutput output, CallbackInfo ci) {
        JourneyManager.writeMirror((ServerPlayer) (Object) this, output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void stagecoach$readJourney(ValueInput input, CallbackInfo ci) {
        JourneyManager.readMirror((ServerPlayer) (Object) this, input);
    }
}
