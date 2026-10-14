package net.feelix.stagecoach.neoforge;

import com.mojang.serialization.MapCodec;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** {@code "neoforge:conditions": [{"type": "stagecoach:builtin_advancements"}]} follows the config switch. */
record NeoForgeConfigCondition() implements ICondition {
    static final MapCodec<NeoForgeConfigCondition> CODEC = MapCodec.unit(new NeoForgeConfigCondition());

    @Override
    public boolean test(IContext context) {
        return StagecoachConfig.get().builtinAdvancements;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
