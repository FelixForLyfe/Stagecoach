package net.feelix.stagecoach.bukkit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.Locale;

public enum Wood {
    OAK("oak", Material.OAK_PLANKS),
    SPRUCE("spruce", Material.SPRUCE_PLANKS),
    BIRCH("birch", Material.BIRCH_PLANKS),
    JUNGLE("jungle", Material.JUNGLE_PLANKS),
    ACACIA("acacia", Material.ACACIA_PLANKS),
    DARK_OAK("dark_oak", Material.DARK_OAK_PLANKS),
    MANGROVE("mangrove", Material.MANGROVE_PLANKS),
    CHERRY("cherry", Material.CHERRY_PLANKS),
    BAMBOO("bamboo", Material.BAMBOO_PLANKS),
    CRIMSON("crimson", Material.CRIMSON_PLANKS),
    WARPED("warped", Material.WARPED_PLANKS);

    private final String id;
    private final Material planks;
    private final NamespacedKey itemModel;

    Wood(String id, Material planks) {
        this.id = id;
        this.planks = planks;
        this.itemModel = new NamespacedKey("stagecoach", id + "_stagecoach");
    }

    /** Used in commands, item data, recipe keys and messages.yml ("wood-oak"). */
    public String id() {
        return id;
    }

    public Material planks() {
        return planks;
    }

    public NamespacedKey itemModel() {
        return itemModel;
    }

    public static Wood byId(String id) {
        if (id == null) return null;
        String want = id.toLowerCase(Locale.ROOT);
        for (Wood w : values()) if (w.id.equals(want)) return w;
        return null;
    }
}
