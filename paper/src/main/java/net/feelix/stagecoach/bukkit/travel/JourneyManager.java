package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.model.CoachModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Crash-safe order: record to disk before changing the player; on the way out restore, drop, save, flush. */
public final class JourneyManager {
    public enum End { ARRIVED, CANCELLED, DESTINATION_GONE, GAME_MODE_CHANGED, DISCONNECTED, SERVER_STOPPING, DIED, RECOVERED }

    private final StagecoachPlugin plugin;
    private final NamespacedKey mirrorKey;
    /** Main thread only: every Bukkit event the plugin uses runs there. */
    private final Set<UUID> travelling = new HashSet<>();
    private final Map<UUID, Long> lastHurt = new HashMap<>();
    private final Map<UUID, Long> lastArrival = new HashMap<>();

    public JourneyManager(StagecoachPlugin plugin) {
        this.plugin = plugin;
        this.mirrorKey = new NamespacedKey(plugin, "journey");
    }

    private NetworkState state() {
        return plugin.state();
    }

    public boolean isTravelling(UUID player) {
        return travelling.contains(player);
    }

    public void recordHurt(Player player) {
        lastHurt.put(player.getUniqueId(), plugin.gameTime());
    }

    public int combatLockSecondsLeft(Player player) {
        return secondsLeft(lastHurt.get(player.getUniqueId()), plugin.settings().combatLockSeconds());
    }

    public int cooldownSecondsLeft(Player player) {
        return secondsLeft(lastArrival.get(player.getUniqueId()), plugin.settings().travelCooldownSeconds());
    }

    private int secondsLeft(Long since, int seconds) {
        if (since == null || seconds <= 0) return 0;
        long left = seconds * 20L - (plugin.gameTime() - since);
        return left > 0 ? (int) ((left + 19) / 20) : 0;
    }

    public Journey journeyOf(Player player) {
        return state().journey(player.getUniqueId());
    }

    /** Everything has been checked, including that {@code cost} can be paid (null fare = XP, 0 = free). */
    public void start(Player player, CoachRecord origin, CoachRecord dest, Fare fare, int cost, int ticks) {
        NetworkState state = state();
        List<String> items = new ArrayList<>();
        if (fare != null && cost > 0) for (ItemStack s : fare.peek(player, cost)) items.add(Fare.encode(s));
        int xpPaid = fare == null ? cost : 0;
        Journey journey = new Journey(ThreadLocalRandom.current().nextLong(), player.getUniqueId(),
                player.getGameMode().name(), player.isFlying(), player.getAllowFlight(), player.getLevel(),
                player.getExp(), player.getTotalExperience(), xpPaid, items, origin.id, origin.name, origin.world, origin.x, origin.y, origin.z, origin.turns,
                dest.id, dest.name, dest.world, dest.x, dest.y, dest.z, dest.turns,
                plugin.gameTime(), ticks, Journey.Phase.TRAVELLING);

        state.putJourney(journey);
        state.flush();                                   // on disk before the player changes
        travelling.add(player.getUniqueId());
        if (xpPaid > 0) player.giveExpLevels(-xpPaid);
        if (!items.isEmpty()) fare.take(player, cost);
        player.setGameMode(GameMode.SPECTATOR);
        if (player.getGameMode() != GameMode.SPECTATOR) {
            // another plugin refused the change: undo everything
            travelling.remove(player.getUniqueId());
            refund(player, journey, 100, true);
            finish(player, journey);
            plugin.messages().actionBar(player, "error-cannot-start");
            return;
        }
        player.setSpectatorTarget(null);
        writeMirror(player, journey);
        hold(player, journey);
        player.saveData();                               // the player's file now agrees with the record (+ mirror)
        showStart(player, journey);
        plugin.getLogger().info(player.getName() + " set off from '" + origin.name + "' to '" + dest.name + "' ("
                + ticks + " ticks, paid " + cost + (fare == null ? " levels)" : " items)"));
    }

    /** No time on the road: pay and step out at the destination. */
    public void travelInstantly(Player player, CoachRecord origin, CoachRecord dest, Fare fare, int cost) {
        World world = Bukkit.getWorld(dest.world);
        if (world == null) return;
        if (fare != null && cost > 0) fare.take(player, cost);
        else if (cost > 0) player.giveExpLevels(-cost);
        Location spot = SafeSpot.near(state(), world, dest.x, dest.y, dest.z, dest.turns);
        spot.setYaw(CoachModel.frontYaw(dest.turns));
        player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setFallDistance(0);
        arrivalSounds(world, spot);
        lastArrival.put(player.getUniqueId(), plugin.gameTime());
        player.saveData();
        plugin.messages().actionBar(player, "arrived", dest.name);
        plugin.getLogger().info(player.getName() + " travelled from '" + origin.name + "' to '" + dest.name
                + "' instantly (paid " + cost + (fare == null ? " levels)" : " items)"));
    }

    private static void arrivalSounds(World world, Location spot) {
        world.playSound(spot, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.8f, 1.1f);
        world.playSound(spot, Sound.ENTITY_HORSE_BREATHE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }

    private void showStart(Player player, Journey j) {
        plugin.dialogs().travelling(player, j, plugin.gameTime());
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.0f);
    }

    public void tick() {
        if (travelling.isEmpty()) return;
        NetworkState state = state();
        long now = plugin.gameTime();
        for (UUID id : new ArrayList<>(travelling)) {
            Player player = Bukkit.getPlayer(id);
            Journey journey = state.journey(id);
            if (player == null || journey == null) {
                travelling.remove(id);
                continue;
            }
            if (player.isDead()) continue; // the respawn handler finishes it
            if (player.getGameMode() != GameMode.SPECTATOR) {
                // something else (/gamemode) changed the mode: that mode wins
                endEarly(player, journey, End.GAME_MODE_CHANGED);
            } else if (state.coach(journey.destId) == null) {
                endEarly(player, journey, End.DESTINATION_GONE);
            } else if (now >= journey.endTime()) {
                arrive(player, journey);
            } else {
                hold(player, journey);
                long elapsed = now - journey.startTime;
                if (elapsed % 10 == 0) {
                    // the next road frame every 10 ticks (16px at the mod's 1.6px a tick); also reopens it after Esc
                    plugin.dialogs().travelling(player, journey, now);
                    player.playSound(player.getLocation(), Sound.ENTITY_HORSE_STEP, SoundCategory.NEUTRAL, 0.25f,
                            elapsed % 20 == 0 ? 0.9f : 1.05f);
                }
            }
        }
    }

    /** Only teleports when they actually moved. */
    public void hold(Player player, Journey journey) {
        World world = Bukkit.getWorld(journey.originWorld);
        if (world == null) world = player.getWorld();
        Location p = holdPoint(journey, world);
        if (player.getSpectatorTarget() != null) player.setSpectatorTarget(null);
        Location at = player.getLocation();
        if (at.getWorld() != world || at.distanceSquared(p) > 0.0025) {
            p.setYaw(at.getYaw());
            p.setPitch(at.getPitch());
            player.teleport(p, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    /** Just past the origin's 2.8-block shaft, one block up. */
    public static Location holdPoint(Journey j, World world) {
        int[] f = CoachModel.front(j.originTurns);
        return new Location(world, j.originX + 0.5 + f[0] * 4, j.originY + 1, j.originZ + 0.5 + f[1] * 4);
    }

    private void arrive(Player player, Journey journey) {
        NetworkState state = state();
        journey.phase = Journey.Phase.ARRIVING;          // from here on, recovery finishes at the destination
        state.flush();
        travelling.remove(player.getUniqueId());
        plugin.dialogs().journeyEnded(player);
        World dest = Bukkit.getWorld(journey.destWorld);
        if (dest == null) {
            endEarly(player, journey, End.DESTINATION_GONE);
            return;
        }
        Location spot = SafeSpot.near(state, dest, journey.destX, journey.destY, journey.destZ, journey.destTurns);
        spot.setYaw(CoachModel.frontYaw(journey.destTurns));
        player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
        restoreMode(player, journey);
        arrivalSounds(dest, spot);
        lastArrival.put(player.getUniqueId(), plugin.gameTime());
        finish(player, journey);
        plugin.messages().actionBar(player, "arrived", journey.destName);
    }

    public void cancel(Player player) {
        Journey journey = journeyOf(player);
        if (journey != null && isTravelling(player.getUniqueId())) endEarly(player, journey, End.CANCELLED);
    }

    /** The origin coach may be gone already; the stored position still holds. */
    private void endEarly(Player player, Journey journey, End why) {
        travelling.remove(player.getUniqueId());
        if (why != End.DISCONNECTED) plugin.dialogs().journeyEnded(player);
        World origin = Bukkit.getWorld(journey.originWorld);
        if (origin == null) origin = player.getWorld();
        Location spot = SafeSpot.near(state(), origin, journey.originX, journey.originY, journey.originZ, journey.originTurns);
        spot.setYaw(CoachModel.frontYaw(journey.originTurns + 2));
        player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (why != End.GAME_MODE_CHANGED) restoreMode(player, journey);
        int percent = why == End.CANCELLED ? plugin.settings().cancelRefundPercent() : 100;
        refund(player, journey, percent, true);
        finish(player, journey);
        if (percent < 100) {
            plugin.messages().send(player, "end-cancelled-partial", journey.destName, percent);
        } else if (why != End.DISCONNECTED && why != End.SERVER_STOPPING) {
            plugin.messages().send(player, "end-" + why.name().toLowerCase(), journey.destName);
        }
        plugin.getLogger().info(player.getName() + "'s journey to '" + journey.destName + "' ended early: " + why);
    }

    /** PlayerQuitEvent runs before Bukkit saves the file. */
    public void onQuit(Player player) {
        Journey journey = journeyOf(player);
        if (journey != null && isTravelling(player.getUniqueId())) endEarly(player, journey, End.DISCONNECTED);
        lastHurt.remove(player.getUniqueId());
    }

    /** Server stopping or a reload: every journey ends at its origin. */
    public void endAll() {
        NetworkState state = state();
        for (UUID id : new ArrayList<>(travelling)) {
            Player player = Bukkit.getPlayer(id);
            Journey journey = state.journey(id);
            if (player != null && journey != null) endEarly(player, journey, End.SERVER_STOPPING);
        }
        travelling.clear();
    }

    /** One tick after the respawn: mode and XP back. */
    public void onRespawned(Player player) {
        Journey journey = journeyOf(player);
        if (journey == null || !isTravelling(player.getUniqueId())) return;
        travelling.remove(player.getUniqueId());
        plugin.dialogs().journeyEnded(player);
        restoreMode(player, journey);
        refund(player, journey, 100, true);
        finish(player, journey);
        plugin.messages().send(player, "end-died", journey.destName);
    }

    /** A login journey is always a leftover (a crash or a kill). */
    public void onJoin(Player player) {
        NetworkState state = state();
        Journey mirror = readMirror(player);
        Journey record = state.journey(player.getUniqueId());
        Journey journey = record != null ? record : mirror;
        if (journey == null) return;
        // items only come back if the saved file saw the payment, or they'd be doubled
        boolean paidInFile = mirror != null && (record == null || mirror.id == record.id);
        travelling.remove(player.getUniqueId());
        World dest = Bukkit.getWorld(journey.destWorld);
        if (journey.phase == Journey.Phase.ARRIVING && dest != null) {
            Location spot = SafeSpot.near(state, dest, journey.destX, journey.destY, journey.destZ, journey.destTurns);
            spot.setYaw(CoachModel.frontYaw(journey.destTurns));
            player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
            restoreMode(player, journey);
            finish(player, journey);
            plugin.messages().send(player, "arrived", journey.destName);
        } else {
            World origin = Bukkit.getWorld(journey.originWorld);
            Location spot;
            if (origin != null && state.coach(journey.originId) != null) {
                spot = SafeSpot.near(state, origin, journey.originX, journey.originY, journey.originZ, journey.originTurns);
            } else {
                Location at = player.getLocation();
                spot = SafeSpot.around(state, at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            }
            spot.setYaw(player.getLocation().getYaw());
            player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
            restoreMode(player, journey);
            refund(player, journey, 100, paidInFile);
            finish(player, journey);
            plugin.messages().send(player, "end-recovered", journey.destName);
        }
        plugin.getLogger().info("Recovered " + player.getName() + "'s interrupted journey to '" + journey.destName
                + "' (" + journey.phase + ")");
    }

    private static void restoreMode(Player player, Journey journey) {
        GameMode mode;
        try {
            mode = GameMode.valueOf(journey.savedMode);
        } catch (IllegalArgumentException e) {
            mode = GameMode.SURVIVAL;
        }
        if (mode == GameMode.SPECTATOR) mode = GameMode.SURVIVAL;
        player.setGameMode(mode);                        // sets that mode's abilities (flight allowed, ...)
        if (mode != GameMode.CREATIVE && journey.allowFlight) player.setAllowFlight(true); // e.g. another plugin's /fly
        player.setFlying(player.getAllowFlight() && journey.wasFlying);
        player.setFallDistance(0);
    }

    /** XP is set, never added, so twice is harmless; items only come back when {@code returnItems}. */
    private static void refund(Player player, Journey journey, int percent, boolean returnItems) {
        if (journey.xpPaid > 0) {
            player.setLevel(journey.xpLevel);
            player.setExp(journey.xpProgress);
            player.setTotalExperience(journey.xpTotal);
            int kept = journey.xpPaid - TravelMath.refundShare(journey.xpPaid, percent);
            if (kept > 0) player.giveExpLevels(-kept);
        }
        if (returnItems && !journey.itemsPaid.isEmpty()) {
            Fare.giveBack(player, journey.itemsPaid, TravelMath.refundShare(Fare.total(journey.itemsPaid), percent));
        }
    }

    /** Drop the record, save the player, then write network.json. */
    private void finish(Player player, Journey journey) {
        NetworkState state = state();
        state.removeJourney(journey.player);
        player.getPersistentDataContainer().remove(mirrorKey);
        player.saveData();
        state.flush();
    }

    private void writeMirror(Player player, Journey journey) {
        player.getPersistentDataContainer().set(mirrorKey, PersistentDataType.STRING, journey.toJson().toString());
    }

    private Journey readMirror(Player player) {
        String s = player.getPersistentDataContainer().get(mirrorKey, PersistentDataType.STRING);
        return s == null ? null : Journey.fromJsonString(s);
    }
}
