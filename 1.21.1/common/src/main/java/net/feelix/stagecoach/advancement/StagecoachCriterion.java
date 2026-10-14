package net.feelix.stagecoach.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.feelix.stagecoach.data.TriggerConditions;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

public final class StagecoachCriterion extends AbstractCriterion<StagecoachCriterion.Conditions> {
    private final String name;
    private final Codec<Conditions> codec;

    StagecoachCriterion(String name) {
        this.name = name;
        this.codec = Codec.PASSTHROUGH.comapFlatMap(this::decode, Conditions::encode);
    }

    public String name() {
        return name;
    }

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return codec;
    }

    public void trigger(ServerPlayerEntity player, TriggerConditions.Fired fired) {
        trigger(player, c -> c.conditions().matches(fired));
    }

    /** The trigger's own fields go through TriggerConditions, so a datapack writes the same JSON on every version. */
    private DataResult<Conditions> decode(Dynamic<?> dynamic) {
        JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!json.isJsonObject()) return DataResult.error(() -> "conditions must be an object");
        JsonObject object = json.getAsJsonObject().deepCopy();
        JsonElement playerJson = object.remove("player");
        Optional<LootContextPredicate> player = Optional.empty();
        if (playerJson != null) {
            DataResult<LootContextPredicate> parsed = EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.parse(JsonOps.INSTANCE, playerJson);
            if (parsed.error().isPresent()) return DataResult.error(() -> parsed.error().get().message());
            player = parsed.result();
        }
        try {
            return DataResult.success(new Conditions(player, TriggerConditions.parse(name, object)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    public record Conditions(Optional<LootContextPredicate> player, TriggerConditions conditions) implements AbstractCriterion.Conditions {
        private Dynamic<?> encode() {
            JsonObject json = conditions.toJson();
            player.ifPresent(p -> json.add("player", EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()));
            return new Dynamic<>(JsonOps.INSTANCE, json);
        }
    }
}
