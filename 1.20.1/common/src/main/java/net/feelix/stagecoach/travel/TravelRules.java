package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.StageCompat;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.TravelRule;
import net.feelix.stagecoach.permission.Permissions;
import net.minecraft.advancement.Advancement;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Evaluated when a list opens and when a journey is asked for, never per tick. */
public final class TravelRules {
    private TravelRules() {}

    public record Verdict(boolean allowed, @Nullable Text message, double multiplier) {
        public static final Verdict OPEN = new Verdict(true, null, 1.0);
    }

    public static Verdict evaluate(ServerPlayerEntity player, Journey.Origin from, CoachRecord to) {
        Map<String, TravelRule> rules = StagecoachData.get().rules();
        if (rules.isEmpty()) return Verdict.OPEN;
        List<String> flags = flags(to);
        String fromDim = from.dimension().getValue().toString(), toDim = to.dimension.getValue().toString();
        double multiplier = 1.0;
        Text denied = null;
        for (TravelRule rule : rules.values()) {
            if (!rule.appliesTo(from.id(), from.name(), fromDim, to.id, to.name, toDim, flags)) continue;
            if (rule.hasRequirements() && !meets(player, rule)) {
                if (denied == null) denied = message(rule);
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

    private static boolean meets(ServerPlayerEntity player, TravelRule rule) {
        if (rule.minLevel() > 0 && player.experienceLevel < rule.minLevel()) return false;
        if (rule.advancement() != null && !hasAdvancement(player, rule.advancement())) return false;
        if (rule.permission() != null && !Permissions.check(player, rule.permission())) return false;
        if (rule.item() != null && !carries(player, rule.item())) return false;
        return rule.stage() == null || StageCompat.has(player, rule.stage());
    }

    private static boolean hasAdvancement(ServerPlayerEntity player, String id) {
        Identifier key = Identifier.tryParse(id);
        Advancement advancement = key == null ? null : player.getServer().getAdvancementLoader().get(key);
        return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
    }

    /** Anywhere in the inventory, armour and off hand included; nothing is taken. */
    private static boolean carries(ServerPlayerEntity player, String id) {
        TagKey<Item> tag = null;
        Item item = null;
        if (id.startsWith("#")) {
            Identifier t = Identifier.tryParse(id.substring(1));
            if (t != null) tag = TagKey.of(RegistryKeys.ITEM, t);
        } else {
            Identifier i = Identifier.tryParse(id);
            if (i != null && Registries.ITEM.containsId(i)) item = Registries.ITEM.get(i);
        }
        if (tag == null && item == null) return false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (item != null ? stack.isOf(item) : stack.isIn(tag)) return true;
        }
        return false;
    }

    private static Text message(TravelRule rule) {
        if (rule.denyMessage() != null) {
            try {
                Text t = Text.Serializer.fromJson(rule.denyMessage());
                if (t != null) return t;
            } catch (RuntimeException e) {
                Stagecoach.LOGGER.warn("A travel rule has a deny_message that isn't a text component: {}", e.getMessage());
            }
        }
        return Stagecoach.text("error.locked");
    }
}
