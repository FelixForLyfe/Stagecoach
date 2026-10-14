package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        Optional<HolderSet.Named<Item>> entries = BuiltInRegistries.ITEM.get(tag);
        return entries.isPresent() && entries.get().size() > 0;
    }

    public static ItemStack icon() {
        if (item != null) return new ItemStack(item);
        if (tag != null) {
            Optional<HolderSet.Named<Item>> entries = BuiltInRegistries.ITEM.get(tag);
            if (entries.isPresent() && entries.get().size() > 0) return new ItemStack(entries.get().get(0));
        }
        return new ItemStack(Items.BARRIER);
    }

    public static boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (item != null) return stack.is(item);
        return tag != null && stack.is(tag);
    }

    /** Main inventory, hotbar and off hand. */
    public static int count(Player player) {
        Inventory inv = player.getInventory();
        int n = 0;
        NonNullList<ItemStack> main = inv.getNonEquipmentItems();
        for (int i = 0; i < main.size(); i++) if (matches(main.get(i))) n += main.get(i).getCount();
        ItemStack offhand = player.getOffhandItem();
        if (matches(offhand)) n += offhand.getCount();
        return n;
    }

    /** Copies of what {@link #take} would take; the journey record is saved before paying. */
    public static List<ItemStack> peek(Player player, int amount) {
        return collect(player, amount, false);
    }

    /** The caller has already checked there are enough. */
    public static List<ItemStack> take(Player player, int amount) {
        List<ItemStack> taken = collect(player, amount, true);
        player.getInventory().setChanged();
        return taken;
    }

    private static List<ItemStack> collect(Player player, int amount, boolean remove) {
        List<ItemStack> taken = new ArrayList<>();
        NonNullList<ItemStack> main = player.getInventory().getNonEquipmentItems();
        int left = amount;
        for (int i = 0; i < main.size() && left > 0; i++) left = collectFrom(main.get(i), left, taken, remove);
        if (left > 0) collectFrom(player.getOffhandItem(), left, taken, remove);
        return taken;
    }

    private static int collectFrom(ItemStack stack, int left, List<ItemStack> taken, boolean remove) {
        if (!matches(stack)) return left;
        int n = Math.min(left, stack.getCount());
        taken.add(stack.copyWithCount(n));
        if (remove) stack.shrink(n);
        return left - n;
    }

    public static List<CompoundTag> encode(List<ItemStack> stacks, HolderLookup.Provider registries) {
        List<CompoundTag> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
            if (tag instanceof CompoundTag c) out.add(c);
        }
        return out;
    }

    private static List<ItemStack> decode(List<CompoundTag> tags, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (CompoundTag tag : tags) {
            ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().ifPresent(out::add);
        }
        return out;
    }

    /** Whatever doesn't fit drops at the player's feet. */
    public static void giveBack(Player player, List<CompoundTag> paid, int amount) {
        for (ItemStack stack : decode(paid, player.registryAccess())) {
            if (amount <= 0) break;
            int n = Math.min(amount, stack.getCount());
            amount -= n;
            ItemStack back = stack.copyWithCount(n);
            if (!player.getInventory().add(back) && !back.isEmpty()) player.drop(back, false);
        }
    }

    public static int total(List<CompoundTag> paid) {
        int n = 0;
        for (CompoundTag tag : paid) n += tag.getIntOr("count", 1);
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
            if (id != null) tag = TagKey.create(Registries.ITEM, id);
            else Stagecoach.LOGGER.warn("config/stagecoach.json: costItem '{}' is not a valid tag - journeys cost XP", raw);
            return;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.getValue(id) != Items.AIR) {
            item = BuiltInRegistries.ITEM.getValue(id);
        } else {
            Stagecoach.LOGGER.warn("config/stagecoach.json: costItem '{}' is not an item - journeys cost XP", raw);
        }
    }
}
