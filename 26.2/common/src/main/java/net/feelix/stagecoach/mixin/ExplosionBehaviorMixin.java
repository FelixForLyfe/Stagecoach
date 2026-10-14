package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity explosions (TNT, creepers) override canDestroyBlock without calling it, so both classes are hooked. */
@Mixin({ExplosionDamageCalculator.class, EntityBasedExplosionDamageCalculator.class})
abstract class ExplosionBehaviorMixin {
    @Inject(method = "shouldBlockExplode", at = @At("HEAD"), cancellable = true)
    private void stagecoach$spareLockedCoaches(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (StagecoachService.isLockedCoach(level, pos, state)) cir.setReturnValue(false);
    }
}
