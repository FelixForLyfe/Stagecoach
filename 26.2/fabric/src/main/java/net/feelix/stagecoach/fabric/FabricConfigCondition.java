package net.feelix.stagecoach.fabric;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

/** {@code "fabric:load_conditions": [{"condition": "stagecoach:builtin_advancements"}]} follows the config switch. */
record FabricConfigCondition() implements ResourceCondition {
    static final ResourceConditionType<FabricConfigCondition> TYPE =
            ResourceConditionType.create(Stagecoach.id("builtin_advancements"), MapCodec.unit(new FabricConfigCondition()));

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(@Nullable RegistryOps.RegistryInfoLookup registryInfo) {
        return StagecoachConfig.get().builtinAdvancements;
    }
}
