package net.feelix.stagecoach.bukkit.listen;

import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.travel.Journey;
import net.feelix.stagecoach.bukkit.travel.JourneyManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class PlayerListener implements Listener {
    private final StagecoachPlugin plugin;

    public PlayerListener(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    private JourneyManager journeys() {
        return plugin.journeys();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        journeys().onJoin(e.getPlayer());
        if (plugin.settings().craftable()) plugin.items().unlockIfEarned(e.getPlayer());
    }

    /** Before Bukkit saves the file: back at the origin, in their own mode, refunded. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        journeys().onQuit(e.getPlayer());
        plugin.dialogs().forget(e.getPlayer().getUniqueId());
        plugin.ghosts().remove(e.getPlayer().getUniqueId());
        plugin.coachListener().forget(e.getPlayer());
    }

    /** Their levels come back on respawn, so the orbs are withheld (no free XP). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (journeys().isTravelling(e.getEntity().getUniqueId())) e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (!journeys().isTravelling(player.getUniqueId())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) journeys().onRespawned(player);
        });
    }

    /** Travellers can't teleport through the spectator menu; normal spectators are untouched. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && journeys().isTravelling(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Travellers stay at the hold point; looking around is fine. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!journeys().isTravelling(player.getUniqueId())) return;
        Location to = e.getTo();
        if (to == null) return;
        Journey j = journeys().journeyOf(player);
        if (j == null) return;
        // moving between worlds is left to the per-tick hold
        if (to.getWorld() == null || !to.getWorld().getUID().equals(j.originWorld)) return;
        Location hold = JourneyManager.holdPoint(j, to.getWorld());
        if (to.distanceSquared(hold) <= 0.0025) return;
        hold.setYaw(to.getYaw());
        hold.setPitch(to.getPitch());
        e.setTo(hold);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        Entity attacker = e.getDamager();
        if (attacker instanceof Projectile p) {
            ProjectileSource shooter = p.getShooter();
            attacker = shooter instanceof Entity s ? s : null;
        }
        if (attacker != null && attacker != player) journeys().recordHurt(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && e.getItem().getItemStack().getType() == Material.DIAMOND_BLOCK
                && plugin.settings().craftable()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.items().unlockIfEarned(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && plugin.settings().craftable()) plugin.items().unlockIfEarned(p);
    }
}
