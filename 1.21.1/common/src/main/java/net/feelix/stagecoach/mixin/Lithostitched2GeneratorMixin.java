package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.math.random.Random;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Lithostitched 2 moved its assembler into an impl package; the members are unchanged. */
@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.impl.worldgen.structure.AlternateJigsawGenerator$StructurePoolGenerator", remap = false)
public abstract class Lithostitched2GeneratorMixin {
    @Shadow @Final private List<? super PoolStructurePiece> piecesToPlace;
    @Shadow @Final private Random random;

    @Inject(method = "getPoolElements", at = @At("RETURN"), cancellable = true, require = 0)
    private void stagecoach$offerCoachStop(RegistryKey<StructurePool> pool, MutableObject<?> checked, int depth, boolean firstIteration,
                                           CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        List<StructurePoolElement> offered = StructureInjections.offer(pool.getValue(), cir.getReturnValue(), piecesToPlace, random);
        if (offered != cir.getReturnValue()) cir.setReturnValue(offered);
    }
}
