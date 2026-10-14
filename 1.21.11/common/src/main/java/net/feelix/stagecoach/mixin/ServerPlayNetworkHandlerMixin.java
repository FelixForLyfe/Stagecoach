package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.JourneyManager;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    // runs on the network thread and again after forceMainThread re-queues; either way it's dropped
    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void stagecoach$noSpectatorTeleportOnTheRoad(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (JourneyManager.isTravelling(player.getUUID())) ci.cancel();
    }
}
