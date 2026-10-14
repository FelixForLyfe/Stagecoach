package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerManagerMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void stagecoach$endJourneyBeforeTheSave(ServerPlayer player, CallbackInfo ci) {
        JourneyManager.onRemoved(player);
    }
}
