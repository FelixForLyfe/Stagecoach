package net.feelix.stagecoach.devclient.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public class ServerDataMixin {
    @Inject(method = "getResourcePackStatus", at = @At("HEAD"), cancellable = true)
    private void stagecoach$autoAccept(CallbackInfoReturnable<ServerData.ServerPackStatus> cir) {
        if (Boolean.getBoolean("stagecoach.autoPack")) cir.setReturnValue(ServerData.ServerPackStatus.ENABLED);
    }
}
