package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.EntityExplosionBehavior;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity explosions (TNT, creepers) override canDestroyBlock without calling it, so both classes are hooked. */
@Mixin({ExplosionBehavior.class, EntityExplosionBehavior.class})
abstract class ExplosionBehaviorMixin {
    @Inject(method = "canDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void stagecoach$spareLockedCoaches(Explosion explosion, BlockView world, BlockPos pos, BlockState state, float power,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (StagecoachService.isLockedCoach(world, pos, state)) cir.setReturnValue(false);
    }
}
