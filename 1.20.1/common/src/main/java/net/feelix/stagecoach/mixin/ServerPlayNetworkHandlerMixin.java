package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    // runs on the network thread and again after forceMainThread re-queues; either way it's dropped
    @Inject(method = "onSpectatorTeleport", at = @At("HEAD"), cancellable = true)
    private void stagecoach$noSpectatorTeleportOnTheRoad(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        if (JourneyManager.isTravelling(player.getUuid())) ci.cancel();
    }
}
