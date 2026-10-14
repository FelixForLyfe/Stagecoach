package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(StructureTemplatePool.class)
public abstract class StructurePoolMixin {
    @Inject(method = "getShuffledTemplates", at = @At("RETURN"), cancellable = true)
    private void stagecoach$offerCoachStop(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        List<StructurePoolElement> offered = StructureInjections.offer((StructureTemplatePool) (Object) this, cir.getReturnValue(), random);
        if (offered != cir.getReturnValue()) cir.setReturnValue(offered);
    }
}
