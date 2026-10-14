package net.feelix.stagecoach.forge;

import com.google.gson.JsonObject;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/** {@code "type": "stagecoach:builtin_advancements"}: the builtinAdvancements option is on. */
final class ForgeConfigCondition implements ICondition {
    static final Identifier ID = Stagecoach.id("builtin_advancements");
    static final ForgeConfigCondition INSTANCE = new ForgeConfigCondition();
    static final IConditionSerializer<ForgeConfigCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override
        public void write(JsonObject json, ForgeConfigCondition value) {
        }

        @Override
        public ForgeConfigCondition read(JsonObject json) {
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
        return StagecoachConfig.get().builtinAdvancements;
    }
}
