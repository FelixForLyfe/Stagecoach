package net.feelix.stagecoach.forge;

import com.google.gson.JsonObject;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.ModList;

/** {@code "type": "stagecoach:tfc_recipes"}: TerraFirmaCraft is installed and the tfcRecipes option is on. */
final class ForgeTfcCondition implements ICondition {
    static final Identifier ID = Stagecoach.id("tfc_recipes");
    static final ForgeTfcCondition INSTANCE = new ForgeTfcCondition();
    static final IConditionSerializer<ForgeTfcCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override
        public void write(JsonObject json, ForgeTfcCondition value) {
        }

        @Override
        public ForgeTfcCondition read(JsonObject json) {
            return INSTANCE;
        }

        @Override
        public Identifier getID() {
            return ID;
        }
    };

    @Override
    public Identifier getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return StagecoachConfig.get().tfcRecipes && ModList.get().isLoaded("tfc");
    }
}
