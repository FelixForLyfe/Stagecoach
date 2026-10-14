package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** An id that names nothing falls back to the XP price, never to free travel. */
public final class Fare {
    private Fare() {}

    private static StagecoachConfig resolvedFor;
    private static @Nullable Item item;
    private static @Nullable TagKey<Item> tag;

    /** Enabled, and the configured item or tag exists. */
    public static boolean itemMode() {
        StagecoachConfig cfg = StagecoachConfig.get();
        if (!cfg.itemCostEnabled) return false;
        resolve(cfg);
        if (item != null) return true;
        if (tag == null) return false;
        Optional<RegistryEntryList.Named<Item>> entries = Registries.ITEM.getEntryList(tag);
        return entries.isPresent() && entries.get().size() > 0;
    }

    public static ItemStack icon() {
        if (item != null) return new ItemStack(item);
        if (tag != null) {
            Optional<RegistryEntryList.Named<Item>> entries = Registries.ITEM.getEntryList(tag);
            if (entries.isPresent() && entries.get().size() > 0) return new ItemStack(entries.get().get(0));
        }
        return new ItemStack(Items.BARRIER);
    }

    public static boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (item != null) return stack.isOf(item);
        return tag != null && stack.isIn(tag);
    }

    /** Main inventory, hotbar and off hand. */
    public static int count(PlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.main.size(); i++) if (matches(inv.main.get(i))) n += inv.main.get(i).getCount();
        for (int i = 0; i < inv.offHand.size(); i++) if (matches(inv.offHand.get(i))) n += inv.offHand.get(i).getCount();
        return n;
    }

    /** Copies of what {@link #take} would take; the journey record is saved before paying. */
    public static List<ItemStack> peek(PlayerEntity player, int amount) {
        return collect(player, amount, false);
    }

    /** The caller has already checked there are enough. */
    public static List<ItemStack> take(PlayerEntity player, int amount) {
        List<ItemStack> taken = collect(player, amount, true);
        player.getInventory().markDirty();
        return taken;
    }

    private static List<ItemStack> collect(PlayerEntity player, int amount, boolean remove) {
        List<ItemStack> taken = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        int left = collectFrom(inv.main, amount, taken, remove);
        collectFrom(inv.offHand, left, taken, remove);
        return taken;
    }

    private static int collectFrom(List<ItemStack> slots, int left, List<ItemStack> taken, boolean remove) {
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!matches(stack)) continue;
            int n = Math.min(left, stack.getCount());
            taken.add(stack.copyWithCount(n));
            if (remove) stack.decrement(n);
            left -= n;
        }
        return left;
    }

    /** Whatever doesn't fit drops at the player's feet. */
    public static void giveBack(PlayerEntity player, List<ItemStack> paid, int amount) {
        for (ItemStack stack : paid) {
            if (amount <= 0) break;
            int n = Math.min(amount, stack.getCount());
            amount -= n;
            ItemStack back = stack.copyWithCount(n);
            if (!player.getInventory().insertStack(back) && !back.isEmpty()) player.dropItem(back, false);
        }
    }

    public static int total(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) n += s.getCount();
        return n;
    }

    private static void resolve(StagecoachConfig cfg) {
        if (resolvedFor == cfg) return;
        resolvedFor = cfg;
        item = null;
        tag = null;
        String raw = cfg.costItem;
        if (raw.startsWith("#")) {
            Identifier id = Identifier.tryParse(raw.substring(1));
            if (id != null) tag = TagKey.of(RegistryKeys.ITEM, id);
            else Stagecoach.LOGGER.warn("config/stagecoach.json: costItem '{}' is not a valid tag - journeys cost XP", raw);
            return;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id != null && Registries.ITEM.containsId(id) && Registries.ITEM.get(id) != Items.AIR) {
            item = Registries.ITEM.get(id);
        } else {
            Stagecoach.LOGGER.warn("config/stagecoach.json: costItem '{}' is not an item - journeys cost XP", raw);
        }
    }
}
