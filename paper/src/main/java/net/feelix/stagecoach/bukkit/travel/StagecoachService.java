package net.feelix.stagecoach.bukkit.travel;

import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.Wood;
import net.feelix.stagecoach.bukkit.config.StagecoachConfig;
import net.feelix.stagecoach.bukkit.model.CoachDisplays;
import net.feelix.stagecoach.bukkit.model.CoachModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StagecoachService {
    public static final String PERM_USE = "stagecoach.use";
    public static final String PERM_PLACE = "stagecoach.place";
    public static final String PERM_ADMIN = "stagecoach.admin";

    /** A messages.yml key and its arguments; {@code retry} means asking again makes sense. */
    public record Denial(String key, Object[] args, boolean retry) {
        static Denial of(String key, Object... args) {
            return new Denial(key, args, false);
        }
    }

    private final StagecoachPlugin plugin;

    public StagecoachService(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    private NetworkState state() {
        return plugin.state();
    }

    public void place(Player player, ItemStack item, EquipmentSlot hand, Wood wood, Block clicked, BlockFace face) {
        if (!player.hasPermission(PERM_PLACE)) {
            plugin.messages().actionBar(player, "place-no-permission");
            return;
        }
        if (plugin.journeys().isTravelling(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) return;
        Denial limit = placementRefusal(player, clicked.getWorld());
        if (limit != null) {
            plugin.messages().actionBar(player, limit.key(), limit.args());
            return;
        }
        Block origin = CoachStructure.placementBlock(clicked, face);
        int turns = CoachStructure.turnsFor(player.getFacing());
        CoachStructure.Refusal refusal = CoachStructure.check(state(), plugin.messages(), player, origin, turns, null);
        if (refusal == null) refusal = CoachStructure.place(player, origin, turns, item, hand, clicked);
        if (refusal != null) {
            player.sendActionBar(refusal.message(plugin.messages()));
            return;
        }
        World world = origin.getWorld();
        CoachRecord record = state().register(id -> new CoachRecord(id, CoachNames.defaultName(id), world.getUID(),
                world.getName(), origin.getX(), origin.getY(), origin.getZ(), turns, wood.id(), player.getUniqueId()));
        plugin.displays().spawn(world, record);
        state().discover(player.getUniqueId(), record.id);
        state().flush();
        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        world.playSound(CoachDisplays.anchor(world, record), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        plugin.dialogs().naming(player, record, false, null);
        plugin.getLogger().info(player.getName() + " placed stagecoach #" + record.id + " at " + world.getName() + " "
                + record.x + " " + record.y + " " + record.z);
    }

    /** Admins are never limited. */
    public Denial placementRefusal(Player player, World world) {
        StagecoachConfig cfg = plugin.settings();
        if (cfg.blockedWorlds().contains(world.getName()) || cfg.blockedWorlds().contains(world.getKey().toString())) {
            return Denial.of("place-world");
        }
        if (cfg.maxCoachesPerPlayer() <= 0 || player.hasPermission(PERM_ADMIN)) return null;
        if (state().countOwnedBy(player.getUniqueId()) < cfg.maxCoachesPerPlayer()) return null;
        return Denial.of("place-limit", cfg.maxCoachesPerPlayer());
    }

    public void use(Player player, CoachRecord record) {
        if (!player.hasPermission(PERM_USE)) {
            plugin.messages().actionBar(player, "error-no-permission");
            return;
        }
        if (plugin.journeys().isTravelling(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) return;
        NetworkState s = state();
        if (plugin.settings().discoveryRequired() && !s.hasDiscovered(player.getUniqueId(), record.id)) {
            s.discover(player.getUniqueId(), record.id);
            s.flush();
            if (!record.isOwner(player.getUniqueId())) {
                plugin.messages().actionBar(player, "discovered", record.name);
                player.playSound(player.getLocation(), Sound.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                return;
            }
        }
        plugin.dialogs().destinations(player, record, "");
    }

    /** A bad name is a {@code retry} denial, so the naming screen asks again. */
    public Denial rename(Player player, long coachId, String raw) {
        CoachRecord record = state().coach(coachId);
        if (record == null || !exists(record)) return Denial.of("error-gone");
        if (!near(player, record)) return Denial.of("error-too-far");
        if (plugin.journeys().isTravelling(player.getUniqueId())) return Denial.of("error-gone");
        if (!canRename(player, record)) return Denial.of("error-not-owner");
        int max = plugin.settings().maxNameLength();
        String name = CoachNames.clean(raw, max);
        if (name == null) return new Denial("error-bad-name", new Object[] {max}, true);
        state().rename(record, name);
        state().flush();
        plugin.messages().actionBar(player, "renamed", name);
        return null;
    }

    public boolean canRename(Player player, CoachRecord record) {
        return record.isOwner(player.getUniqueId()) || player.hasPermission(PERM_ADMIN);
    }

    public Denial travel(Player player, long originId, long destId) {
        if (!player.hasPermission(PERM_USE)) return Denial.of("error-no-permission");
        if (plugin.journeys().isTravelling(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR
                || player.isDead()) {
            return Denial.of("error-gone");
        }
        NetworkState s = state();
        StagecoachConfig cfg = plugin.settings();
        CoachRecord origin = s.coach(originId), dest = s.coach(destId);
        if (origin == null || dest == null || origin.id == dest.id || !exists(origin)) return deny(player, "error-gone");
        if (!near(player, origin)) return deny(player, "error-too-far");
        if (!knows(s, player, origin) || !knows(s, player, dest)) return deny(player, "error-gone");
        if (!origin.world.equals(dest.world) && !cfg.allowCrossWorld()) return deny(player, "error-other-world");
        if (Bukkit.getWorld(dest.world) == null) return deny(player, "error-gone");
        double distance = TravelMath.horizontalDistance(origin.x, origin.z, dest.x, dest.z);
        if (TravelMath.tooFar(distance, cfg)) return deny(player, "error-route-too-long", cfg.maxDistance());
        int wait = plugin.journeys().cooldownSecondsLeft(player);
        if (wait > 0) return deny(player, "error-cooldown", wait);
        int lock = plugin.journeys().combatLockSecondsLeft(player);
        if (lock > 0) return deny(player, "error-combat", lock);
        Fare fare = plugin.fare();
        int cost = travelsFree(player) ? 0 : fare != null ? TravelMath.itemCost(distance, cfg) : TravelMath.cost(distance, cfg);
        if (fare != null && fare.count(player) < cost) return deny(player, "error-no-items", cost, fare.name());
        if (fare == null && player.getLevel() < cost) return deny(player, "error-no-xp", cost);
        if (cfg.instantTravel()) plugin.journeys().travelInstantly(player, origin, dest, fare, cost);
        else plugin.journeys().start(player, origin, dest, fare, cost, TravelMath.ticks(distance, cfg));
        return null;
    }

    public boolean travelsFree(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && plugin.settings().creativeTravelsFree();
    }

    private static Denial deny(Player player, String key, Object... args) {
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.6f, 1.0f);
        return Denial.of(key, args);
    }

    /** With onlyOwnerCanBreak off, anyone may. */
    public boolean mayBreak(Player player, CoachRecord record) {
        return !plugin.settings().onlyOwnerCanBreak() || player.hasPermission(PERM_ADMIN)
                || record.isOwner(player.getUniqueId());
    }

    /** Owner rule first, then a BlockBreakEvent for protection plugins; no item in creative. */
    public boolean breakCoach(Player player, CoachRecord record, Block hit) {
        if (!mayBreak(player, record)) {
            plugin.messages().actionBar(player, "error-not-owner-break");
            return false;
        }
        BlockBreakEvent check = new BlockBreakEvent(hit, player);
        CoachStructure.firingOwnEvent = true;
        try {
            Bukkit.getPluginManager().callEvent(check);
        } finally {
            CoachStructure.firingOwnEvent = false;
        }
        if (check.isCancelled()) {
            plugin.messages().actionBar(player, "error-protected-break");
            return false;
        }
        World world = hit.getWorld();
        Location at = CoachDisplays.anchor(world, record).add(0, 0.5, 0);
        remove(world, record);
        if (player.getGameMode() != GameMode.CREATIVE) {
            Wood wood = Wood.byId(record.wood);
            world.dropItemNaturally(at, plugin.items().create(wood == null ? Wood.OAK : wood, 1, plugin.messages()));
        }
        plugin.getLogger().info(player.getName() + " took down stagecoach #" + record.id + " '" + record.name + "'");
        return true;
    }

    /** Journeys heading here end on the next tick. */
    public void remove(World world, CoachRecord record) {
        Wood wood = Wood.byId(record.wood);
        Location at = CoachDisplays.anchor(world, record).add(0, 0.8, 0);
        plugin.displays().remove(world, record);
        CoachStructure.clear(world, record);
        state().remove(record.id);
        state().flush();
        world.playSound(at, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        world.spawnParticle(Particle.BLOCK, at, 60, 1.0, 0.6, 1.0, 0,
                (wood == null ? Material.OAK_PLANKS : wood.planks()).createBlockData());
    }

    public enum Status { OK, CANT_AFFORD, OTHER_WORLD, TOO_FAR }

    public record Entry(CoachRecord coach, int distance, int cost, int seconds, boolean owned, Status status) {}

    /** Nearest first; costs are fare items when there's a fare, else levels. */
    public List<Entry> list(Player player, CoachRecord here) {
        NetworkState s = state();
        StagecoachConfig cfg = plugin.settings();
        Fare fare = plugin.fare();
        boolean free = travelsFree(player);
        int balance = fare != null ? fare.count(player) : player.getLevel();
        List<Entry> entries = new ArrayList<>();
        for (CoachRecord c : s.coaches()) {
            if (c.id == here.id || !knows(s, player, c)) continue;
            double distance = TravelMath.horizontalDistance(here.x, here.z, c.x, c.z);
            int cost = free ? 0 : fare != null ? TravelMath.itemCost(distance, cfg) : TravelMath.cost(distance, cfg);
            Status status;
            if ((!c.world.equals(here.world) && !cfg.allowCrossWorld()) || Bukkit.getWorld(c.world) == null) status = Status.OTHER_WORLD;
            else if (TravelMath.tooFar(distance, cfg)) status = Status.TOO_FAR;
            else if (balance < cost) status = Status.CANT_AFFORD;
            else status = Status.OK;
            entries.add(new Entry(c, (int) Math.round(distance), cost,
                    cfg.instantTravel() ? 0 : (int) Math.ceil(TravelMath.seconds(distance, cfg)), c.isOwner(player.getUniqueId()), status));
        }
        entries.sort(Comparator.comparingInt(Entry::distance));
        return entries;
    }

    /** Placed, discovered, or every coach when discoveryRequired is off. */
    public boolean knows(NetworkState s, Player player, CoachRecord c) {
        return !plugin.settings().discoveryRequired() || c.isOwner(player.getUniqueId()) || s.hasDiscovered(player.getUniqueId(), c.id);
    }

    /** Within useRange of the controller, in the same world. */
    public boolean near(Player player, CoachRecord c) {
        Location at = player.getLocation();
        if (!at.getWorld().getUID().equals(c.world)) return false;
        double dx = at.getX() - (c.x + 0.5), dy = at.getY() - (c.y + 0.5), dz = at.getZ() - (c.z + 0.5);
        double range = plugin.settings().useRange();
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    /** Its world is loaded and its controller cell still holds a barrier. */
    public static boolean exists(CoachRecord c) {
        World w = Bukkit.getWorld(c.world);
        if (w == null) return false;
        int[] o = CoachModel.offset(CoachModel.CONTROLLER, c.turns);
        return w.getBlockAt(c.x + o[0], c.y + o[1], c.z + o[2]).getType() == Material.BARRIER;
    }
}
