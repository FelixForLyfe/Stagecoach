package net.feelix.stagecoach.bukkit.listen;

import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.Wood;
import net.feelix.stagecoach.bukkit.model.CoachDisplays;
import net.feelix.stagecoach.bukkit.travel.CoachRecord;
import net.feelix.stagecoach.bukkit.travel.CoachStructure;
import net.feelix.stagecoach.bukkit.travel.NetworkState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoachListener implements Listener {
    /** Survival hits in a row, each within HIT_WINDOW_MS of the last. */
    private static final int HITS_TO_BREAK = 3;
    private static final long HIT_WINDOW_MS = 1500;

    private record Hits(long coachId, int count, long last) {}

    private final StagecoachPlugin plugin;
    private final Map<UUID, Hits> hits = new HashMap<>();

    public CoachListener(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    private NetworkState state() {
        return plugin.state();
    }

    private CoachRecord coachAt(Block b, boolean barrierOnly) {
        if (b == null) return null;
        NetworkState.Cell cell = state().cellAt(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        if (cell == null || (barrierOnly && (!cell.barrier() || b.getType() != Material.BARRIER))) return null;
        return state().coach(cell.coachId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            onHit(e);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        CoachRecord coach = e.getAction() == Action.RIGHT_CLICK_BLOCK ? coachAt(e.getClickedBlock(), true) : null;
        if (coach != null) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            if (e.getHand() == EquipmentSlot.HAND) plugin.service().use(player, coach);
            return;
        }
        ItemStack item = e.getItem();
        Wood wood = plugin.items().woodOf(item);
        if (wood == null) return;
        // never let the boat underneath do anything
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked.getType().isInteractable() && !player.isSneaking()) return; // open the chest, press the button ...
        e.setUseInteractedBlock(Event.Result.DENY);
        plugin.service().place(player, item, e.getHand(), wood, clicked, e.getBlockFace());
    }

    /** Survival and adventure can't break a barrier, so a coach comes down after a few hits. */
    private void onHit(PlayerInteractEvent e) {
        CoachRecord coach = coachAt(e.getClickedBlock(), true);
        if (coach == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!plugin.service().mayBreak(player, coach)) {
            plugin.messages().actionBar(player, "error-not-owner-break");
            return;
        }
        long now = System.currentTimeMillis();
        Hits h = hits.get(player.getUniqueId());
        int count = h != null && h.coachId == coach.id && now - h.last <= HIT_WINDOW_MS ? h.count + 1 : 1;
        Block hit = e.getClickedBlock();
        World world = hit.getWorld();
        Wood wood = Wood.byId(coach.wood);
        world.playSound(hit.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1.0f, 0.8f);
        world.spawnParticle(Particle.BLOCK, hit.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0,
                (wood == null ? Material.OAK_PLANKS : wood.planks()).createBlockData());
        if (count >= HITS_TO_BREAK) {
            hits.remove(player.getUniqueId());
            plugin.service().breakCoach(player, coach, hit);
        } else {
            hits.put(player.getUniqueId(), new Hits(coach.id, count, now));
        }
    }

    /** Breaking a barrier in creative takes the whole coach down instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (CoachStructure.firingOwnEvent) return;
        CoachRecord coach = coachAt(e.getBlock(), false);
        if (coach == null) return;
        e.setCancelled(true);
        if (e.getBlock().getType() == Material.BARRIER) plugin.service().breakCoach(e.getPlayer(), coach, e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (CoachStructure.firingOwnEvent) return;
        if (coachAt(e.getBlockPlaced(), false) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (coachAt(e.getToBlock(), false) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (coachAt(e.getBlock().getRelative(e.getDirection()), false) != null) {
            e.setCancelled(true);
            return;
        }
        for (Block b : e.getBlocks()) {
            if (coachAt(b.getRelative(e.getDirection()), false) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        e.getBlocks().removeIf((BlockState s) -> coachAt(s.getBlock(), false) != null);
    }

    /** Removes our displays whose coach is gone and respawns a coach whose displays went missing. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        World world = e.getWorld();
        CoachDisplays displays = plugin.displays();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        for (Entity entity : e.getEntities()) {
            Long id = displays.coachOf(entity);
            if (id == null) continue;
            CoachRecord r = state().coach(id);
            if (r == null || !r.world.equals(world.getUID()) || (r.x >> 4) != cx || (r.z >> 4) != cz) entity.remove();
        }
        List<Long> here = new ArrayList<>();
        for (CoachRecord r : state().coaches()) {
            if (r.world.equals(world.getUID()) && (r.x >> 4) == cx && (r.z >> 4) == cz) here.add(r.id);
        }
        if (here.isEmpty()) return;
        // next tick, so nothing spawns while the chunk is still handing its entities over
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(cx, cz)) return;
            for (long id : here) {
                CoachRecord r = state().coach(id);
                if (r != null) displays.repair(world, r, displays.find(world, r));
            }
        });
    }

    public void forget(Player player) {
        hits.remove(player.getUniqueId());
    }
}
