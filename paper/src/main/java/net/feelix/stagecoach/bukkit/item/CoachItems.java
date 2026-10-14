package net.feelix.stagecoach.bukkit.item;

import net.feelix.stagecoach.bukkit.Messages;
import net.feelix.stagecoach.bukkit.Wood;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** An oak chest boat underneath, recognised only by its {@code stagecoach:wood} tag. */
public final class CoachItems {
    public static final Material BASE = Material.OAK_CHEST_BOAT;

    private final JavaPlugin plugin;
    private final NamespacedKey woodKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public CoachItems(JavaPlugin plugin) {
        this.plugin = plugin;
        this.woodKey = new NamespacedKey(plugin, "wood");
    }

    public ItemStack create(Wood wood, int amount, Messages msg) {
        ItemStack stack = new ItemStack(BASE, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(msg.c("item-name", msg.c("wood-" + wood.id())));
        meta.lore(msg.lines("item-lore"));
        meta.setItemModel(wood.itemModel());
        meta.setMaxStackSize(64);
        meta.getPersistentDataContainer().set(woodKey, PersistentDataType.STRING, wood.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public Wood woodOf(ItemStack stack) {
        if (stack == null || stack.getType() != BASE || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(woodKey, PersistentDataType.STRING);
        return Wood.byId(id);
    }

    public boolean isCoach(ItemStack stack) {
        return woodOf(stack) != null;
    }

    /** PCP / LDL / DGD: planks, chest, lead, block of diamond, block of gold. */
    public void registerRecipes(Messages msg) {
        unregisterRecipes();
        for (Wood wood : Wood.values()) {
            NamespacedKey key = new NamespacedKey(plugin, wood.id() + "_stagecoach");
            ShapedRecipe r = new ShapedRecipe(key, create(wood, 1, msg));
            r.shape("PCP", "LDL", "DGD");
            r.setIngredient('P', new RecipeChoice.MaterialChoice(wood.planks()));
            r.setIngredient('C', Material.CHEST);
            r.setIngredient('L', Material.LEAD);
            r.setIngredient('D', Material.DIAMOND_BLOCK);
            r.setIngredient('G', Material.GOLD_BLOCK);
            r.setGroup("stagecoach");
            Bukkit.addRecipe(r);
            recipeKeys.add(key);
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys) Bukkit.removeRecipe(key);
        recipeKeys.clear();
    }

    public List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }

    /** Once they carry a block of diamond. */
    public void unlockIfEarned(Player player) {
        if (recipeKeys.isEmpty() || !player.getInventory().contains(Material.DIAMOND_BLOCK)) return;
        for (NamespacedKey key : recipeKeys) {
            if (!player.hasDiscoveredRecipe(key)) player.discoverRecipe(key);
        }
    }
}
