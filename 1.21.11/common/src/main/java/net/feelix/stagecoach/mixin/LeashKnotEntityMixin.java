package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.Hitching;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A knot tied to a coach stays, as one on a fence does. */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashKnotEntityMixin {
    @Inject(method = "survives", at = @At("HEAD"), cancellable = true)
    private void stagecoach$tiedToCoach(CallbackInfoReturnable<Boolean> cir) {
        LeashFenceKnotEntity self = (LeashFenceKnotEntity) (Object) this;
        if (Hitching.isCoach(self.level().getBlockState(self.getPos()))) cir.setReturnValue(true);
    }
}
