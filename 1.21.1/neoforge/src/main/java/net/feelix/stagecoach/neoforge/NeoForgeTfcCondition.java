package net.feelix.stagecoach.neoforge;

import com.mojang.serialization.MapCodec;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ICondition;

/** {@code "type": "stagecoach:tfc_recipes"}: TerraFirmaCraft is installed and the tfcRecipes option is on. */
final class NeoForgeTfcCondition implements ICondition {
    static final NeoForgeTfcCondition INSTANCE = new NeoForgeTfcCondition();
    static final MapCodec<NeoForgeTfcCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        return StagecoachConfig.get().tfcRecipes && ModList.get().isLoaded("tfc");
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
