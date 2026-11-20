package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.Hitching;
import net.minecraft.entity.decoration.LeashKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A knot tied to a coach stays, as one on a fence does. */
@Mixin(LeashKnotEntity.class)
public abstract class LeashKnotEntityMixin {
    @Inject(method = "canStayAttached", at = @At("HEAD"), cancellable = true)
    private void stagecoach$tiedToCoach(CallbackInfoReturnable<Boolean> cir) {
        LeashKnotEntity self = (LeashKnotEntity) (Object) this;
        if (Hitching.isCoach(self.getWorld().getBlockState(self.getDecorationBlockPos()))) cir.setReturnValue(true);
    }
}
