package net.feelix.stagecoach.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds the mixins into other mods' jigsaw assemblers only when that mod is installed, so a missing one logs nothing. */
public final class StagecoachMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> OPTIONAL = Map.of(
            "LithostitchedGeneratorMixin", "dev/worldgen/lithostitched/worldgen/structure/AlternateJigsawGenerator$StructurePoolGenerator.class",
            "Lithostitched2GeneratorMixin", "dev/worldgen/lithostitched/impl/worldgen/structure/AlternateJigsawGenerator$StructurePoolGenerator.class",
            "RepurposedStructuresAssemblerMixin", "com/telepathicgrunt/repurposedstructures/world/structures/pieces/PieceLimitedJigsawManager$Assembler.class",
            "IntegratedApiAssemblerMixin", "com/craisinlord/integrated_api/world/structures/pieces/manager/PieceLimitedJigsawManager$Assembler.class");

    @Override
    public List<String> getMixins() {
        List<String> present = new ArrayList<>();
        ClassLoader loader = StagecoachMixinPlugin.class.getClassLoader();
        for (Map.Entry<String, String> e : OPTIONAL.entrySet()) {
            if (loader.getResource(e.getValue()) != null) present.add(e.getKey());
        }
        return present;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
