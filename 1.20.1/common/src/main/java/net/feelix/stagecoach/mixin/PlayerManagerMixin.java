package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
abstract class PlayerManagerMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void stagecoach$endJourneyBeforeTheSave(ServerPlayerEntity player, CallbackInfo ci) {
        JourneyManager.onRemoved(player);
    }
}
