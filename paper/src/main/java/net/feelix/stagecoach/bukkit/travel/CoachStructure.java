package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.Messages;
import net.feelix.stagecoach.bukkit.model.CoachModel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CoachStructure {
    private CoachStructure() {}

    public enum Reason { BUILD_LIMIT, BLOCKED, ENTITY, PROTECTED }

    /** {@code what} names the block or entity in the way, else null. */
    public record Refusal(Reason reason, Block where, Component what) {
        public Component message(Messages m) {
            return switch (reason) {
                case BUILD_LIMIT -> m.c("place-build-limit");
                case BLOCKED -> m.c("place-blocked", what);
                case ENTITY -> m.c("place-entity", what);
                case PROTECTED -> m.c("place-protected");
            };
        }
    }

    /** True while the plugin fires its own place/break events, so its listeners let those through. */
    public static boolean firingOwnEvent;

    /** Into the clicked block if it's replaceable. */
    public static Block placementBlock(Block clicked, BlockFace face) {
        return replaceable(clicked) ? clicked : clicked.getRelative(face);
    }

    /** Away from the player: NORTH 0, EAST 1, SOUTH 2, WEST 3. */
    public static int turnsFor(BlockFace facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public static Block cell(Block origin, int index, int turns) {
        int[] o = CoachModel.offset(index, turns);
        return origin.getRelative(o[0], o[1], o[2]);
    }

    /** Null if every cell is free; pass {@code blocked} to mark them all (protection comes later, in place). */
    public static Refusal check(NetworkState state, Messages msg, Player player, Block origin, int turns,
                                boolean[] blocked) {
        Refusal first = null;
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            Refusal r = checkCell(state, msg, player, cell(origin, i, turns));
            if (blocked != null) blocked[i] = r != null;
            if (r != null && first == null) {
                first = r;
                if (blocked == null) return first;
            }
        }
        return first;
    }

    private static Refusal checkCell(NetworkState state, Messages msg, Player player, Block b) {
        World w = b.getWorld();
        if (b.getY() < w.getMinHeight() || b.getY() >= w.getMaxHeight()) return new Refusal(Reason.BUILD_LIMIT, b, null);
        if (state.cellAt(w.getUID(), b.getX(), b.getY(), b.getZ()) != null) {
            return new Refusal(Reason.BLOCKED, b, msg.c("place-another"));
        }
        if (!replaceable(b)) return new Refusal(Reason.BLOCKED, b, Component.translatable(b.getType().translationKey()));
        if (!w.getWorldBorder().isInside(b.getLocation().add(0.5, 0.5, 0.5)) || spawnProtected(player, b)) {
            return new Refusal(Reason.PROTECTED, b, null);
        }
        Collection<Entity> inTheWay = w.getNearbyEntities(BoundingBox.of(b), CoachStructure::blocksBuilding);
        if (!inTheWay.isEmpty()) return new Refusal(Reason.ENTITY, b, name(inTheWay.iterator().next()));
        return null;
    }

    /** Vanilla's "blocks building" filter. */
    static boolean blocksBuilding(Entity e) {
        if (!e.isValid()) return false;
        if (e instanceof Player p) return p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        if (e instanceof ArmorStand a) return !a.isMarker();
        return e instanceof LivingEntity || e instanceof Vehicle || e instanceof EnderCrystal
                || e instanceof TNTPrimed || e instanceof FallingBlock;
    }

    private static Component name(Entity e) {
        return e.name(); // the custom name, a player's name, or the translated mob name
    }

    /** Vanilla's rule: main world, radius above 0, at least one op, and the player isn't one. */
    static boolean spawnProtected(Player player, Block b) {
        World main = Bukkit.getWorlds().get(0);
        int radius = Bukkit.getSpawnRadius();
        if (player == null || !b.getWorld().equals(main) || radius <= 0) return false;
        if (player.isOp() || Bukkit.getOperators().isEmpty()) return false;
        Location s = main.getSpawnLocation();
        return Math.max(Math.abs(b.getX() - s.getBlockX()), Math.abs(b.getZ() - s.getBlockZ())) <= radius;
    }

    /** The minecraft:replaceable tag, but only one snow layer. */
    public static boolean replaceable(Block b) {
        Material t = b.getType();
        if (t.isAir()) return true;
        if (!Tag.REPLACEABLE.isTagged(t)) return false;
        return !(b.getBlockData() instanceof Snow s) || s.getLayers() <= 1;
    }

    /** A real BlockPlaceEvent per cell so protection plugins can refuse; one refusal undoes them all. */
    public static Refusal place(Player player, Block origin, int turns, ItemStack item, EquipmentSlot hand, Block against) {
        List<BlockState> before = new ArrayList<>();
        firingOwnEvent = true;
        try {
            for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
                Block b = cell(origin, i, turns);
                BlockState old = b.getState();
                before.add(old);
                b.setType(Material.BARRIER, false);
                BlockPlaceEvent event = new BlockPlaceEvent(b, old, against, item, player, true, hand);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled() || !event.canBuild()) {
                    for (int j = before.size() - 1; j >= 0; j--) before.get(j).update(true, false);
                    return new Refusal(Reason.PROTECTED, b, null);
                }
                if (!CoachModel.barrier(i)) b.setType(Material.AIR, false);
            }
        } finally {
            firingOwnEvent = false;
        }
        return null;
    }

    /** Only cells that still hold one. */
    public static void clear(World world, CoachRecord r) {
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            if (!CoachModel.barrier(i)) continue;
            int[] o = CoachModel.offset(i, r.turns);
            Block b = world.getBlockAt(r.x + o[0], r.y + o[1], r.z + o[2]);
            if (b.getType() == Material.BARRIER) b.setType(Material.AIR, true);
        }
    }
}
