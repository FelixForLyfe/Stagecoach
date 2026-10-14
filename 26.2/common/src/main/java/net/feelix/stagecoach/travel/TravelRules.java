package net.feelix.stagecoach.travel;

import com.mojang.serialization.JsonOps;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.StageCompat;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.TravelRule;
import net.feelix.stagecoach.permission.Permissions;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Evaluated when a list opens and when a journey is asked for, never per tick. */
public final class TravelRules {
    private TravelRules() {}

    public record Verdict(boolean allowed, @Nullable Component message, double multiplier) {
        public static final Verdict OPEN = new Verdict(true, null, 1.0);
    }

    public static Verdict evaluate(ServerPlayer player, Journey.Origin from, CoachRecord to) {
        Map<String, TravelRule> rules = StagecoachData.get().rules();
        if (rules.isEmpty()) return Verdict.OPEN;
        List<String> flags = flags(to);
        String fromDim = from.dimension().identifier().toString(), toDim = to.dimension.identifier().toString();
        double multiplier = 1.0;
        Component denied = null;
        for (TravelRule rule : rules.values()) {
            if (!rule.appliesTo(from.id(), from.name(), fromDim, to.id, to.name, toDim, flags)) continue;
            if (rule.hasRequirements() && !meets(player, rule)) {
                if (denied == null) denied = message(rule, player);
                continue;
            }
            multiplier *= rule.costMultiplier();
        }
        return denied == null ? new Verdict(true, null, multiplier) : new Verdict(false, denied, multiplier);
    }

    private static List<String> flags(CoachRecord c) {
        List<String> flags = new ArrayList<>(4);
        if (c.isPublic) flags.add("public");
        if (c.hidden) flags.add("hidden");
        if (c.locked) flags.add("locked");
        if (c.free) flags.add("free");
        return flags;
    }

    private static boolean meets(ServerPlayer player, TravelRule rule) {
        if (rule.minLevel() > 0 && player.experienceLevel < rule.minLevel()) return false;
        if (rule.advancement() != null && !hasAdvancement(player, rule.advancement())) return false;
        if (rule.permission() != null && !Permissions.check(player, rule.permission())) return false;
        if (rule.item() != null && !carries(player, rule.item())) return false;
        return rule.stage() == null || StageCompat.has(player, rule.stage());
    }

    private static boolean hasAdvancement(ServerPlayer player, String id) {
        Identifier key = Identifier.tryParse(id);
        AdvancementHolder advancement = key == null ? null : player.level().getServer().getAdvancements().get(key);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Anywhere in the inventory, armour and off hand included; nothing is taken. */
    private static boolean carries(ServerPlayer player, String id) {
        TagKey<Item> tag = null;
        Item item = null;
        if (id.startsWith("#")) {
            Identifier t = Identifier.tryParse(id.substring(1));
            if (t != null) tag = TagKey.create(Registries.ITEM, t);
        } else {
            Identifier i = Identifier.tryParse(id);
            if (i != null && BuiltInRegistries.ITEM.containsKey(i)) item = BuiltInRegistries.ITEM.getValue(i);
        }
        if (tag == null && item == null) return false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (item != null ? stack.is(item) : stack.is(tag)) return true;
        }
        return false;
    }

    private static Component message(TravelRule rule, ServerPlayer player) {
        if (rule.denyMessage() != null) {
            try {
                return ComponentSerialization.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        rule.denyMessage()).getOrThrow();
            } catch (RuntimeException e) {
                Stagecoach.LOGGER.warn("A travel rule has a deny_message that isn't a text component: {}", e.getMessage());
            }
        }
        return Stagecoach.text("error.locked");
    }
}
