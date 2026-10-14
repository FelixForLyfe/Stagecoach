package net.feelix.stagecoach.bukkit.model;

import net.feelix.stagecoach.bukkit.travel.CoachRecord;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Each display carries {@code stagecoach:coach} = id; all stand in the controller's block. */
public final class CoachDisplays {
    private final NamespacedKey coachKey;

    public CoachDisplays(JavaPlugin plugin) {
        this.coachKey = new NamespacedKey(plugin, "coach");
    }

    public static Location anchor(World world, CoachRecord r) {
        return new Location(world, r.x + 0.5, r.y, r.z + 0.5);
    }

    /** What an item display shows: a plain item wearing the pack's model. */
    public static ItemStack modelItem(String path) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("stagecoach", path));
        stack.setItemMeta(meta);
        return stack;
    }

    public void spawn(World world, CoachRecord r) {
        Location at = anchor(world, r);
        for (int i = 0; i < CoachModelData.CUBES.length; i++) {
            CoachModelData.Cube cube = CoachModelData.CUBES[i];
            ItemStack piece = modelItem("coach/" + r.wood + "/" + i);
            world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(piece);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformationMatrix(CoachModel.displayMatrix(cube, r.turns));
                d.setViewRange(2.0f); // 128 blocks, like the server's display tracking range
                d.setPersistent(true);
                d.getPersistentDataContainer().set(coachKey, PersistentDataType.LONG, r.id);
            });
        }
    }

    /** Null if it isn't one of ours. */
    public Long coachOf(Entity e) {
        if (!(e instanceof ItemDisplay)) return null;
        return e.getPersistentDataContainer().get(coachKey, PersistentDataType.LONG);
    }

    /** Only those loaded right now. */
    public List<Entity> find(World world, CoachRecord r) {
        List<Entity> out = new ArrayList<>();
        Chunk chunk = world.getChunkAt(r.x >> 4, r.z >> 4);
        for (Entity e : chunk.getEntities()) {
            Long id = coachOf(e);
            if (id != null && id == r.id) out.add(e);
        }
        return out;
    }

    public void remove(World world, CoachRecord r) {
        for (Entity e : find(world, r)) e.remove();
    }

    public void repair(World world, CoachRecord r, List<Entity> present) {
        if (present.size() == CoachModelData.CUBES.length) return;
        for (Entity e : present) e.remove();
        spawn(world, r);
    }
}
