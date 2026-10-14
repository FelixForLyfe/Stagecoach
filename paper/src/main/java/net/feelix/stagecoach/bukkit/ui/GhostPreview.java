package net.feelix.stagecoach.bukkit.ui;

import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.model.CoachDisplays;
import net.feelix.stagecoach.bukkit.model.CoachModel;
import net.feelix.stagecoach.bukkit.model.CoachModelData;
import net.feelix.stagecoach.bukkit.travel.CoachStructure;
import net.feelix.stagecoach.bukkit.travel.StagecoachService;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GhostPreview {
    private static final Matrix4f HIDDEN = new Matrix4f().scaling(0f);

    private static final class Ghost {
        final List<ItemDisplay> pieces = new ArrayList<>();
        final List<ItemDisplay> cells = new ArrayList<>();
        Block origin;
        int turns = -1;
        Boolean fits;
        boolean[] blocked = new boolean[CoachModel.CELL_COUNT];
    }

    private final StagecoachPlugin plugin;
    private final Map<UUID, Ghost> ghosts = new HashMap<>();

    public GhostPreview(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Target t = plugin.settings().placementPreview() ? target(p) : null;
            if (t == null) {
                remove(p.getUniqueId());
                continue;
            }
            Ghost g = ghosts.get(p.getUniqueId());
            if (g != null && (!g.pieces.get(0).isValid() || g.origin.getWorld() != t.origin.getWorld()
                    || farApart(g.origin, t.origin))) {
                remove(p.getUniqueId());
                g = null;
            }
            // only once the client has the chunk: a display that arrives before it is never shown later
            if (!p.isChunkSent(Chunk.getChunkKey(t.origin.getX() >> 4, t.origin.getZ() >> 4))) continue;
            if (g == null) {
                g = spawn(p, t);
                ghosts.put(p.getUniqueId(), g);
            }
            update(g, t);
        }
    }

    private record Target(Block origin, int turns, boolean fits, boolean[] blocked) {}

    private static boolean farApart(Block a, Block b) {
        return Math.abs(a.getX() - b.getX()) > 16 || Math.abs(a.getY() - b.getY()) > 16 || Math.abs(a.getZ() - b.getZ()) > 16;
    }

    private Target target(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR || !p.hasPermission(StagecoachService.PERM_PLACE)) return null;
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!plugin.items().isCoach(main)
                && !(main.getType().isAir() && plugin.items().isCoach(p.getInventory().getItemInOffHand()))) {
            return null;
        }
        RayTraceResult hit = p.rayTraceBlocks(p.getGameMode() == GameMode.CREATIVE ? 5.0 : 4.5, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return null;
        Block clicked = hit.getHitBlock();
        if (plugin.state().cellAt(clicked.getWorld().getUID(), clicked.getX(), clicked.getY(), clicked.getZ()) != null) {
            return null; // aiming at a coach opens it rather than placing
        }
        Block origin = CoachStructure.placementBlock(clicked, hit.getHitBlockFace());
        int turns = CoachStructure.turnsFor(p.getFacing());
        boolean[] blocked = new boolean[CoachModel.CELL_COUNT];
        boolean fits = CoachStructure.check(plugin.state(), plugin.messages(), p, origin, turns, blocked) == null;
        return new Target(origin, turns, fits, blocked);
    }

    private Ghost spawn(Player p, Target t) {
        Ghost g = new Ghost();
        Location at = anchor(t.origin);
        for (int i = 0; i < CoachModelData.CUBES.length; i++) g.pieces.add(display(p, at));
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) g.cells.add(display(p, at));
        ItemStack cell = CoachDisplays.modelItem("coach/blocked_cell");
        for (ItemDisplay d : g.cells) {
            d.setItemStack(cell);
            d.setTransformationMatrix(HIDDEN);
        }
        return g;
    }

    private ItemDisplay display(Player owner, Location at) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTeleportDuration(2);
            d.setViewRange(1.0f);
        });
        owner.showEntity(plugin, display);
        return display;
    }

    private void update(Ghost g, Target t) {
        boolean moved = g.origin == null || !g.origin.equals(t.origin);
        boolean turned = g.turns != t.turns;
        if (moved) {
            Location at = anchor(t.origin);
            for (ItemDisplay d : g.pieces) d.teleport(at);
            for (ItemDisplay d : g.cells) d.teleport(at);
        }
        if (turned) {
            for (int i = 0; i < g.pieces.size(); i++) {
                g.pieces.get(i).setTransformationMatrix(CoachModel.displayMatrix(CoachModelData.CUBES[i], t.turns));
            }
        }
        if (g.fits == null || g.fits != t.fits) {
            String skin = t.fits ? "ghost_fits" : "ghost_blocked";
            for (int i = 0; i < g.pieces.size(); i++) g.pieces.get(i).setItemStack(CoachDisplays.modelItem("coach/" + skin + "/" + i));
        }
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            if (turned || g.blocked[i] != t.blocked[i]) {
                g.cells.get(i).setTransformationMatrix(t.blocked[i] ? CoachModel.cellDisplayMatrix(i, t.turns) : HIDDEN);
            }
        }
        g.origin = t.origin;
        g.turns = t.turns;
        g.fits = t.fits;
        g.blocked = t.blocked;
    }

    private static Location anchor(Block origin) {
        return new Location(origin.getWorld(), origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
    }

    public void remove(UUID player) {
        Ghost g = ghosts.remove(player);
        if (g == null) return;
        for (ItemDisplay d : g.pieces) d.remove();
        for (ItemDisplay d : g.cells) d.remove();
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(ghosts.keySet())) remove(id);
    }
}
