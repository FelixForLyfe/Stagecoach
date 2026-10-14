package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin {
    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void stagecoach$keepOwnCameraOnTheRoad(@Nullable Entity entity, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (entity != null && entity != self && JourneyManager.isTravelling(self.getUuid())) ci.cancel();
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void stagecoach$writeJourney(NbtCompound nbt, CallbackInfo ci) {
        JourneyManager.writeMirror((ServerPlayerEntity) (Object) this, nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void stagecoach$readJourney(NbtCompound nbt, CallbackInfo ci) {
        JourneyManager.readMirror((ServerPlayerEntity) (Object) this, nbt);
    }
}
