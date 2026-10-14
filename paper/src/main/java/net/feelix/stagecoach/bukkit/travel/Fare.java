package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.config.StagecoachConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Paid stacks are kept exactly as taken, so a refund gives back the same items. */
public final class Fare {
    private final Material item;
    private final Tag<Material> tag;
    private final Material icon;

    private Fare(Material item, Tag<Material> tag) {
        this.item = item;
        this.tag = tag;
        this.icon = item != null ? item
                : tag.getValues().stream().min(Comparator.comparing(m -> m.getKey().toString())).orElse(Material.EMERALD);
    }

    /** Null when journeys cost XP: item cost off, or costItem names no item (warned). */
    public static Fare resolve(StagecoachConfig cfg, List<String> warnings) {
        if (!cfg.itemCostEnabled()) return null;
        String id = cfg.costItem().toLowerCase(Locale.ROOT);
        if (id.startsWith("#")) {
            NamespacedKey key = NamespacedKey.fromString(id.substring(1));
            Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
            if (tag != null && !tag.getValues().isEmpty()) return new Fare(null, tag);
        } else {
            Material m = Material.matchMaterial(id);
            if (m != null && m.isItem() && !m.isAir()) return new Fare(m, null);
        }
        warnings.add("costItem '" + cfg.costItem() + "' is not an item - journeys cost XP");
        return null;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return item != null ? stack.getType() == item : tag.isTagged(stack.getType());
    }

    /** A tag shows its first item by id. */
    public Material icon() {
        return icon;
    }

    public Component name() {
        return Component.translatable(icon);
    }

    public int count(Player player) {
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) if (matches(s)) n += s.getAmount();
        if (matches(inv.getItemInOffHand())) n += inv.getItemInOffHand().getAmount();
        return n;
    }

    /** Copies of what {@link #take} would remove, without touching the inventory. */
    public List<ItemStack> peek(Player player, int amount) {
        return collect(player, amount, false);
    }

    public List<ItemStack> take(Player player, int amount) {
        return collect(player, amount, true);
    }

    private List<ItemStack> collect(Player player, int amount, boolean remove) {
        PlayerInventory inv = player.getInventory();
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length && amount > 0; i++) {
            ItemStack s = storage[i];
            if (!matches(s)) continue;
            int n = Math.min(amount, s.getAmount());
            taken.add(s.asQuantity(n));
            amount -= n;
            if (remove) inv.setItem(i, n == s.getAmount() ? null : s.asQuantity(s.getAmount() - n));
        }
        ItemStack off = inv.getItemInOffHand();
        if (amount > 0 && matches(off)) {
            int n = Math.min(amount, off.getAmount());
            taken.add(off.asQuantity(n));
            if (remove) inv.setItemInOffHand(n == off.getAmount() ? null : off.asQuantity(off.getAmount() - n));
        }
        return taken;
    }

    /** In the order they were taken; what doesn't fit drops at their feet. */
    public static void giveBack(Player player, List<String> paid, int amount) {
        for (String encoded : paid) {
            if (amount <= 0) break;
            ItemStack stack = decode(encoded);
            if (stack == null) continue;
            int n = Math.min(amount, stack.getAmount());
            amount -= n;
            for (ItemStack left : player.getInventory().addItem(stack.asQuantity(n)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }

    /** Read without the server: each entry starts with its count. */
    public static int total(List<String> paid) {
        int n = 0;
        for (String encoded : paid) {
            int colon = encoded.indexOf(':');
            if (colon > 0) {
                try {
                    n += Integer.parseInt(encoded.substring(0, colon));
                } catch (NumberFormatException ignored) {
                    // a damaged entry counts as nothing
                }
            }
        }
        return n;
    }

    /** "count:base64", so totals can be read without decoding the stack. */
    public static String encode(ItemStack stack) {
        return stack.getAmount() + ":" + Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    private static ItemStack decode(String encoded) {
        int colon = encoded.indexOf(':');
        if (colon < 0) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded.substring(colon + 1)));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
