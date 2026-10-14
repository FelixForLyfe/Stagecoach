package net.feelix.stagecoach.forge;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** {@code "forge:condition": {"type": "stagecoach:builtin_advancements"}} follows the config switch. */
record ForgeConfigCondition() implements ICondition {
    static final MapCodec<ForgeConfigCondition> CODEC = MapCodec.unit(new ForgeConfigCondition());

    static void register(BusGroup modBus) {
        DeferredRegister<MapCodec<? extends ICondition>> conditions = DeferredRegister.create(ForgeRegistries.Keys.CONDITION_SERIALIZERS, Stagecoach.MOD_ID);
        conditions.register("builtin_advancements", () -> CODEC);
        conditions.register(modBus);
    }

    @Override
    public boolean test(IContext context, DynamicOps<?> ops) {
        return StagecoachConfig.get().builtinAdvancements;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
