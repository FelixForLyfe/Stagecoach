package net.feelix.stagecoach.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.feelix.stagecoach.data.TriggerConditions;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class StagecoachCriterion extends SimpleCriterionTrigger<StagecoachCriterion.Conditions> {
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
    public Codec<Conditions> codec() {
        return codec;
    }

    public void trigger(ServerPlayer player, TriggerConditions.Fired fired) {
        trigger(player, c -> c.conditions().matches(fired));
    }

    /** The trigger's own fields go through TriggerConditions, so a datapack writes the same JSON on every version. */
    private DataResult<Conditions> decode(Dynamic<?> dynamic) {
        JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!json.isJsonObject()) return DataResult.error(() -> "conditions must be an object");
        JsonObject object = json.getAsJsonObject().deepCopy();
        JsonElement playerJson = object.remove("player");
        Optional<ContextAwarePredicate> player = Optional.empty();
        if (playerJson != null) {
            DataResult<ContextAwarePredicate> parsed = EntityPredicate.ADVANCEMENT_CODEC.parse(JsonOps.INSTANCE, playerJson);
            if (parsed.error().isPresent()) return DataResult.error(() -> parsed.error().get().message());
            player = parsed.result();
        }
        try {
            return DataResult.success(new Conditions(player, TriggerConditions.parse(name, object)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    public record Conditions(Optional<ContextAwarePredicate> player, TriggerConditions conditions) implements SimpleCriterionTrigger.SimpleInstance {
        private Dynamic<?> encode() {
            JsonObject json = conditions.toJson();
            player.ifPresent(p -> json.add("player", EntityPredicate.ADVANCEMENT_CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()));
            return new Dynamic<>(JsonOps.INSTANCE, json);
        }
    }
}
