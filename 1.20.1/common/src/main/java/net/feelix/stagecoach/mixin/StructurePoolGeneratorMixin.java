package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.structure.PoolStructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.structure.pool.StructurePoolBasedGenerator$StructurePoolGenerator")
public abstract class StructurePoolGeneratorMixin {
    @Shadow @Final private List<? super PoolStructurePiece> children;

    @Inject(method = "generatePiece", at = @At("HEAD"))
    private void stagecoach$begin(CallbackInfo ci) {
        StructureInjections.assembling(children);
    }

    @Inject(method = "generatePiece", at = @At("RETURN"))
    private void stagecoach$end(CallbackInfo ci) {
        StructureInjections.assembling(null);
    }
}
