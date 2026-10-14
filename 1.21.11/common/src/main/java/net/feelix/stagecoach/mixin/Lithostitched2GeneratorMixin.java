package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
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
    @Shadow @Final private List<? super PoolElementStructurePiece> piecesToPlace;
    @Shadow @Final private RandomSource random;

    @Inject(method = "getPoolElements", at = @At("RETURN"), cancellable = true, require = 0)
    private void stagecoach$offerCoachStop(ResourceKey<StructureTemplatePool> pool, MutableObject<?> checked, int depth, boolean firstIteration,
                                           CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        List<StructurePoolElement> offered = StructureInjections.offer(pool.identifier(), cir.getReturnValue(), piecesToPlace, random);
        if (offered != cir.getReturnValue()) cir.setReturnValue(offered);
    }
}
