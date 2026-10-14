package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(StructurePool.class)
public abstract class StructurePoolMixin {
    @Inject(method = "getElementIndicesInRandomOrder", at = @At("RETURN"), cancellable = true)
    private void stagecoach$offerCoachStop(Random random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        List<StructurePoolElement> offered = StructureInjections.offer((StructurePool) (Object) this, cir.getReturnValue(), random);
        if (offered != cir.getReturnValue()) cir.setReturnValue(offered);
    }
}
