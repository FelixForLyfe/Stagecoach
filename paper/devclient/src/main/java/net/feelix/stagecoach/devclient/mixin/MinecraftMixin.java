package net.feelix.stagecoach.devclient.mixin;

import net.feelix.stagecoach.devclient.Harness;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void stagecoach$tick(CallbackInfo ci) {
        Harness.tick((Minecraft) (Object) this);
    }
}
