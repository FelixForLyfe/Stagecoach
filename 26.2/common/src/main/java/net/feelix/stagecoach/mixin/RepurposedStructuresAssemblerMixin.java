package net.feelix.stagecoach.mixin;

import com.mojang.datafixers.util.Pair;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Repurposed Structures' own jigsaw assembler, which picks pieces by weight. */
@Pseudo
@Mixin(targets = "com.telepathicgrunt.repurposedstructures.world.structures.pieces.PieceLimitedJigsawManager$Assembler", remap = false)
public abstract class RepurposedStructuresAssemblerMixin {
    @Shadow @Final private List<? super PoolElementStructurePiece> structurePieces;

    @ModifyVariable(method = "processList", at = @At("HEAD"), argsOnly = true, require = 0)
    private List<Pair<StructurePoolElement, Integer>> stagecoach$offerCoachStop(List<Pair<StructurePoolElement, Integer>> candidates) {
        return StructureInjections.offerWeighted(candidates, structurePieces);
    }
}
