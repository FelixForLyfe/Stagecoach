package net.feelix.stagecoach.advancement;

import com.google.gson.JsonObject;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.data.TriggerConditions;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class StagecoachCriterion extends AbstractCriterion<StagecoachCriterion.Conditions> {
    private final Identifier id;
    private final String name;

    StagecoachCriterion(String name) {
        this.name = name;
        this.id = Stagecoach.id(name);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    protected Conditions conditionsFromJson(JsonObject json, LootContextPredicate player, AdvancementEntityPredicateDeserializer deserializer) {
        return new Conditions(id, player, TriggerConditions.parse(name, json));
    }

    public void trigger(ServerPlayerEntity player, TriggerConditions.Fired fired) {
        trigger(player, c -> c.conditions.matches(fired));
    }

    public static final class Conditions extends AbstractCriterionConditions {
        private final TriggerConditions conditions;

        Conditions(Identifier id, LootContextPredicate player, TriggerConditions conditions) {
            super(id, player);
            this.conditions = conditions;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer serializer) {
            JsonObject json = super.toJson(serializer);
            JsonObject own = conditions.toJson();
            for (String key : own.keySet()) json.add(key, own.get(key));
            return json;
        }
    }
}
