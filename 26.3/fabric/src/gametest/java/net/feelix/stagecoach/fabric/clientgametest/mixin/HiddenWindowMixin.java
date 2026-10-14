package net.feelix.stagecoach.fabric.clientgametest.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Dev only: with -Dstagecoach.hidden=true the window is created hidden (SDL_WINDOW_HIDDEN). */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
    private long stagecoach$hidden(long flags) {
        return Boolean.getBoolean("stagecoach.hidden") ? flags | 0x8L : flags;
    }
}
