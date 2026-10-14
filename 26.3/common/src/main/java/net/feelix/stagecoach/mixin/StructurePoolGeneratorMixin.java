package net.feelix.stagecoach.mixin;

import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class StructurePoolGeneratorMixin {
    @Shadow @Final private List<? super PoolElementStructurePiece> pieces;

    @Inject(method = "tryPlacingChildren", at = @At("HEAD"))
    private void stagecoach$begin(CallbackInfo ci) {
        StructureInjections.assembling(pieces);
    }

    @Inject(method = "tryPlacingChildren", at = @At("RETURN"))
    private void stagecoach$end(CallbackInfo ci) {
        StructureInjections.assembling(null);
    }
}
