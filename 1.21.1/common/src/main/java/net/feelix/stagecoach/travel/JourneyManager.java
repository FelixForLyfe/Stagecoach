package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachSounds;
import net.feelix.stagecoach.advancement.StagecoachTriggers;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.EndReason;
import net.feelix.stagecoach.api.StopView;
import net.feelix.stagecoach.api.event.JourneyCompletedEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyStartedEvent;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.mixin.PlayerManagerAccessor;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.net.ServerNet;
import net.feelix.stagecoach.platform.EventHooks;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Crash-safe order: record to disk before changing the player; on the way out restore, drop, save, flush. */
public final class JourneyManager {
    private JourneyManager() {}

    /** Readable from any thread: the spectator-teleport mixin runs on the network thread. */
    private static final Set<UUID> TRAVELLING = ConcurrentHashMap.newKeySet();
    /** Same players by index, so the tick needs no iterator. */
    private static final ObjectArrayList<UUID> ON_ROAD = new ObjectArrayList<>();
    private static final Object2IntOpenHashMap<UUID> LAST_HURT = new Object2IntOpenHashMap<>();
    private static final Object2IntOpenHashMap<UUID> LAST_ARRIVAL = new Object2IntOpenHashMap<>();
    /** Read from the player's own file at login, used by the join handler. */
    private static final Map<UUID, Journey> LOADED_MIRRORS = new ConcurrentHashMap<>();

    public static final String MIRROR_KEY = "StagecoachJourney";

    // leaving is PlayerManagerMixin, not a loader event: Fabric also fires DISCONNECT on Netty

    public static void onServerTick(MinecraftServer server) {
        PendingCoaches.onServerTick(server);
        ServerNet.onServerTick(server);
        if (ON_ROAD.isEmpty()) return;
        StagecoachState network = StagecoachState.get(server);
        long now = server.getOverworld().getTime();
        for (int i = ON_ROAD.size() - 1; i >= 0; i--) {
            UUID id = ON_ROAD.get(i);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            Journey journey = network.journey(id);
            if (player == null || journey == null) {
                setTravelling(id, false);
            } else if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
                // something else (/gamemode) changed it: that mode wins
                endEarly(player, journey, EndReason.GAME_MODE_CHANGED);
            } else if (network.coach(journey.destId) == null) {
                endEarly(player, journey, EndReason.DESTINATION_GONE);
            } else if (now >= journey.endTime()) {
                arrive(player, journey);
            } else {
                hold(player, journey);
            }
        }
    }

    /** Before players are saved and kicked. */
    public static void onServerStopping(MinecraftServer server) {
        StagecoachState network = StagecoachState.get(server);
        for (int i = ON_ROAD.size() - 1; i >= 0; i--) {
            UUID id = ON_ROAD.get(i);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            Journey journey = network.journey(id);
            if (player != null && journey != null) endEarly(player, journey, EndReason.SERVER_STOPPED);
        }
    }

    public static void onServerStopped() {
        TRAVELLING.clear();
        ON_ROAD.clear();
        LAST_HURT.clear();
        LAST_ARRIVAL.clear();
        LOADED_MIRRORS.clear();
        PendingCoaches.onServerStopped();
        ServerNet.onServerStopped();
    }

    /** {@code alive} is true for the End-portal return, false after a death. */
    public static void onRespawn(ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) onDied(newPlayer);
    }

    /** Only noted, never changed. */
    public static void onDamaged(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player && source.getAttacker() != null && source.getAttacker() != player) {
            LAST_HURT.put(player.getUuid(), player.getServer().getTicks());
        }
    }

    public static boolean isTravelling(UUID player) {
        return TRAVELLING.contains(player);
    }

    public static int combatLockSecondsLeft(ServerPlayerEntity player) {
        return secondsLeft(player, LAST_HURT, StagecoachConfig.get().combatLockSeconds);
    }

    public static int cooldownSecondsLeft(ServerPlayerEntity player) {
        return secondsLeft(player, LAST_ARRIVAL, StagecoachConfig.get().travelCooldownSeconds);
    }

    private static int secondsLeft(ServerPlayerEntity player, Object2IntOpenHashMap<UUID> since, int seconds) {
        if (seconds <= 0 || !since.containsKey(player.getUuid())) return 0;
        int left = seconds * 20 - (player.getServer().getTicks() - since.getInt(player.getUuid()));
        return left > 0 ? (left + 19) / 20 : 0;
    }

    private static void setTravelling(UUID id, boolean on) {
        if (on) {
            if (TRAVELLING.add(id)) ON_ROAD.add(id);
        } else if (TRAVELLING.remove(id)) {
            ON_ROAD.remove(id);
        }
    }

    /** Everything has been checked; this only performs the start, in the crash-safe order. */
    public static void start(ServerPlayerEntity player, Journey.Origin from, CoachRecord dest, boolean itemFare, int cost, int ticks,
                             int distance, int[] stops) {
        MinecraftServer server = player.getServer();
        StagecoachState network = StagecoachState.get(server);
        int xp = itemFare ? 0 : cost;
        List<ItemStack> items = itemFare && cost > 0 ? Fare.peek(player, cost) : List.of();
        Journey journey = new Journey(player.getRandom().nextLong(), player.getUuid(), player.interactionManager.getGameMode(),
                player.getAbilities().flying, player.experienceLevel, player.experienceProgress, player.totalExperience, xp,
                items, from, dest, server.getOverworld().getTime(), ticks, distance);

        network.putJourney(journey);
        network.flush(server);                        // on disk before the player changes
        setTravelling(player.getUuid(), true);
        if (xp > 0) player.addExperienceLevels(-xp);
        if (!items.isEmpty()) Fare.take(player, cost);
        player.changeGameMode(GameMode.SPECTATOR);
        player.setCameraEntity(player);
        hold(player, journey);
        savePlayer(player);                           // the player's file now agrees with the record, and carries it
        ServerNet.journeyStart(player, journey, stops, dest);
        Vec3d at = journey.holdPoint;
        player.getServerWorld().playSound(null, at.x, at.y, at.z, StagecoachSounds.JOURNEY_DEPART, SoundCategory.NEUTRAL, 0.8f, 1.0f);
        Stagecoach.LOGGER.info("{} set off from '{}' to '{}' ({} ticks, paid {} {})", player.getName().getString(),
                from.isCoach() ? from.name() : "where they stood", dest.name, ticks, cost, itemFare ? "items" : "levels");
        StopView fromView = fromView(network, journey), toView = dest.view();
        StagecoachTriggers.journeyStarted(player, nameOf(fromView), dest.name, distance, cost, from.dimension() != dest.dimension);
        EventHooks.post(new JourneyStartedEvent(player, fromView, toView, cost, itemFare, (ticks + 19) / 20, false));
    }

    /** Nothing to recover, so no record. */
    public static void travelInstantly(ServerPlayerEntity player, Journey.Origin from, CoachRecord dest, boolean itemFare, int cost,
                                       int distance) {
        ServerWorld world = player.getServer().getWorld(dest.dimension);
        if (world == null) return;
        if (itemFare && cost > 0) Fare.take(player, cost);
        else if (cost > 0) player.addExperienceLevels(-cost);
        Vec3d spot = SafeSpot.near(world, dest.pos, dest.facing);
        player.teleport(world, spot.x, spot.y, spot.z, dest.facing.asRotation(), 0);
        player.fallDistance = 0;
        arrivalSounds(world, spot);
        LAST_ARRIVAL.put(player.getUuid(), player.getServer().getTicks());
        savePlayer(player);
        player.sendMessage(Stagecoach.text("arrived", dest.name).formatted(Formatting.GOLD), true);
        Stagecoach.LOGGER.info("{} travelled from '{}' to '{}' instantly (paid {} {})", player.getName().getString(),
                from.isCoach() ? from.name() : "where they stood", dest.name, cost, itemFare ? "items" : "levels");
        StagecoachState network = StagecoachState.get(player.getServer());
        CoachRecord origin = from.isCoach() ? network.coach(from.id()) : null;
        StopView fromView = origin == null ? null : origin.view(), toView = dest.view();
        boolean cross = from.dimension() != dest.dimension;
        StagecoachTriggers.journeyStarted(player, nameOf(fromView), dest.name, distance, cost, cross);
        EventHooks.post(new JourneyStartedEvent(player, fromView, toView, cost, itemFare, 0, true));
        StagecoachTriggers.journeyCompleted(player, nameOf(fromView), dest.name, distance, cross);
        EventHooks.post(new JourneyCompletedEvent(player, fromView, toView, distance));
        EventHooks.post(new JourneyEndedEvent(player, fromView, toView, EndReason.COMPLETED));
    }

    /** Only sends a packet when they actually moved. */
    private static void hold(ServerPlayerEntity player, Journey journey) {
        ServerWorld world = player.getServer().getWorld(journey.originDim);
        if (world == null) world = player.getServerWorld();
        Vec3d p = journey.holdPoint;
        if (player.getWorld() != world) {
            player.teleport(world, p.x, p.y, p.z, player.getYaw(), player.getPitch());
        } else if (player.squaredDistanceTo(p) > 0.0025) {
            player.networkHandler.requestTeleport(p.x, p.y, p.z, player.getYaw(), player.getPitch());
        }
        if (player.getCameraEntity() != player) player.setCameraEntity(player);
    }

    private static void arrive(ServerPlayerEntity player, Journey journey) {
        MinecraftServer server = player.getServer();
        journey.phase = Journey.Phase.ARRIVING;       // from here on, recovery finishes at the destination
        StagecoachState network = StagecoachState.get(server);
        network.flush(server);
        setTravelling(player.getUuid(), false);
        ServerWorld dest = server.getWorld(journey.destDim);
        if (dest == null) {
            endEarly(player, journey, EndReason.DESTINATION_GONE);
            return;
        }
        Vec3d spot = SafeSpot.near(dest, journey.destPos, journey.destFacing);
        player.teleport(dest, spot.x, spot.y, spot.z, journey.destFacing.asRotation(), 0);
        restoreMode(player, journey);
        arrivalSounds(dest, spot);
        LAST_ARRIVAL.put(player.getUuid(), server.getTicks());
        StopView fromView = fromView(network, journey), toView = toView(network, journey);
        finish(player, journey);
        ServerNet.journeyEnd(player, Packets.END_ARRIVED);
        player.sendMessage(Stagecoach.text("arrived", journey.destName).formatted(Formatting.GOLD), true);
        completed(player, journey, fromView, toView);
    }

    private static void completed(ServerPlayerEntity player, Journey journey, @Nullable StopView fromView, StopView toView) {
        StagecoachTriggers.journeyCompleted(player, nameOf(fromView), journey.destName, journey.distance, journey.originDim != journey.destDim);
        EventHooks.post(new JourneyCompletedEvent(player, fromView, toView, journey.distance));
        EventHooks.post(new JourneyEndedEvent(player, fromView, toView, EndReason.COMPLETED));
    }

    private static void arrivalSounds(ServerWorld world, Vec3d spot) {
        world.playSound(null, spot.x, spot.y, spot.z, StagecoachSounds.JOURNEY_ARRIVE, SoundCategory.BLOCKS, 0.8f, 1.1f);
        world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENTITY_HORSE_BREATHE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }

    public static void cancel(ServerPlayerEntity player) {
        Journey journey = StagecoachState.get(player.getServer()).journey(player.getUuid());
        if (journey != null && isTravelling(player.getUuid())) endEarly(player, journey, EndReason.CANCELLED);
    }

    /** {@code /stagecoach journey cancel}: always a full refund. */
    public static boolean cancelByAdmin(ServerPlayerEntity player) {
        Journey journey = StagecoachState.get(player.getServer()).journey(player.getUuid());
        if (journey == null || !isTravelling(player.getUuid())) return false;
        endEarly(player, journey, EndReason.ADMIN);
        return true;
    }

    /** The origin coach may be gone already; the stored position still holds. */
    private static void endEarly(ServerPlayerEntity player, Journey journey, EndReason why) {
        MinecraftServer server = player.getServer();
        StagecoachState network = StagecoachState.get(server);
        StopView fromView = fromView(network, journey), toView = toView(network, journey);
        setTravelling(player.getUuid(), false);
        ServerWorld origin = server.getWorld(journey.originDim);
        if (origin == null) origin = player.getServerWorld();
        Vec3d spot = returnSpot(origin, journey);
        player.teleport(origin, spot.x, spot.y, spot.z, journey.originFacing.getOpposite().asRotation(), 0);
        if (why != EndReason.GAME_MODE_CHANGED) restoreMode(player, journey);
        int percent = why == EndReason.CANCELLED ? StagecoachConfig.get().cancelRefundPercent : 100;
        refund(player, journey, percent, true);
        finish(player, journey);
        if (why != EndReason.DISCONNECTED && why != EndReason.SERVER_STOPPED) {
            ServerNet.journeyEnd(player, why == EndReason.CANCELLED ? Packets.END_CANCELLED : Packets.END_INTERRUPTED);
            player.sendMessage((percent < 100 ? Stagecoach.text("end.cancelled_partial", journey.destName, percent)
                    : Stagecoach.text("end." + why.name().toLowerCase(), journey.destName))
                    .formatted(why == EndReason.CANCELLED ? Formatting.YELLOW : Formatting.RED), false);
        }
        Stagecoach.LOGGER.info("{}'s journey to '{}' ended early: {}", player.getName().getString(), journey.destName, why);
        if (why == EndReason.CANCELLED) StagecoachTriggers.journeyCancelled(player, journey.destName);
        EventHooks.post(new JourneyEndedEvent(player, fromView, toView, why));
    }

    /** In front of the coach they left, or where they were sent from. */
    private static Vec3d returnSpot(ServerWorld world, Journey journey) {
        return journey.fromCoach() ? SafeSpot.near(world, journey.originPos, journey.originFacing) : SafeSpot.around(world, journey.originPos);
    }

    /** From the head of PlayerManager#remove, so the file vanilla saves next gets the refund. */
    public static void onRemoved(ServerPlayerEntity player) {
        ServerNet.onLeft(player);
        Journey journey = StagecoachState.get(player.getServer()).journey(player.getUuid());
        if (journey != null && isTravelling(player.getUuid())) endEarly(player, journey, EndReason.DISCONNECTED);
    }

    /** Died on the road: stay at the respawn point, get the mode and the fare back. */
    private static void onDied(ServerPlayerEntity player) {
        StagecoachState network = StagecoachState.get(player.getServer());
        Journey journey = network.journey(player.getUuid());
        if (journey == null || !isTravelling(player.getUuid())) return;
        StopView fromView = fromView(network, journey), toView = toView(network, journey);
        setTravelling(player.getUuid(), false);
        restoreMode(player, journey);
        refund(player, journey, 100, true);
        finish(player, journey);
        ServerNet.journeyEnd(player, Packets.END_INTERRUPTED);
        player.sendMessage(Stagecoach.text("end.died", journey.destName).formatted(Formatting.RED), false);
        EventHooks.post(new JourneyEndedEvent(player, fromView, toView, EndReason.DIED));
    }

    /** A login journey is always a leftover; items come back only if the player's file shows them paid. */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        StagecoachState network = StagecoachState.get(server);
        ServerNet.serverSettings(player);
        Journey mirror = LOADED_MIRRORS.remove(player.getUuid());
        Journey journey = network.journey(player.getUuid());
        boolean paidInFile = mirror != null && (journey == null || mirror.id == journey.id);
        if (journey == null) journey = mirror;
        if (journey == null) return;
        setTravelling(player.getUuid(), false);
        StopView fromView = fromView(network, journey), toView = toView(network, journey);
        ServerWorld dest = server.getWorld(journey.destDim);
        if (journey.phase == Journey.Phase.ARRIVING && dest != null) {
            Vec3d spot = SafeSpot.near(dest, journey.destPos, journey.destFacing);
            player.teleport(dest, spot.x, spot.y, spot.z, journey.destFacing.asRotation(), 0);
            restoreMode(player, journey);
            finish(player, journey);
            player.sendMessage(Stagecoach.text("arrived", journey.destName).formatted(Formatting.GOLD), false);
            completed(player, journey, fromView, toView);
        } else {
            ServerWorld origin = server.getWorld(journey.originDim);
            Vec3d spot;
            if (origin != null && (!journey.fromCoach() || network.coach(journey.originId) != null)) {
                spot = returnSpot(origin, journey);
            } else {
                origin = player.getServerWorld();
                spot = SafeSpot.around(origin, player.getBlockPos());
            }
            player.teleport(origin, spot.x, spot.y, spot.z, player.getYaw(), 0);
            restoreMode(player, journey);
            refund(player, journey, 100, paidInFile);
            finish(player, journey);
            player.sendMessage(Stagecoach.text("end.recovered", journey.destName).formatted(Formatting.RED), false);
            EventHooks.post(new JourneyEndedEvent(player, fromView, toView, EndReason.RECOVERED));
        }
        Stagecoach.LOGGER.info("Recovered {}'s interrupted journey to '{}' ({})", player.getName().getString(), journey.destName, journey.phase);
    }

    private static @Nullable StopView fromView(StagecoachState network, Journey journey) {
        CoachRecord origin = journey.fromCoach() ? network.coach(journey.originId) : null;
        return origin == null ? null : origin.view();
    }

    /** The stop as it is now, or as the journey remembers it when it's gone. */
    private static StopView toView(StagecoachState network, Journey journey) {
        CoachRecord dest = network.coach(journey.destId);
        if (dest != null) return dest.view();
        return new StopView(journey.destId, journey.destName, journey.destDim, journey.destPos, journey.destFacing, "oak", Optional.empty(),
                false, false, false, false, 1.0, Optional.empty(), CreatedBy.PLAYER);
    }

    private static String nameOf(@Nullable StopView view) {
        return view == null ? "" : view.name();
    }

    private static void restoreMode(ServerPlayerEntity player, Journey journey) {
        GameMode mode = journey.savedMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : journey.savedMode;
        player.changeGameMode(mode);                  // sets that mode's abilities (flight allowed, invulnerable, ...)
        if (mode == GameMode.CREATIVE) {
            player.getAbilities().flying = journey.wasFlying;
            player.sendAbilitiesUpdate();
        } else if (!player.getAbilities().allowFlying && player.getAbilities().flying) {
            // NeoForge keeps the spectator's `flying` across the mode change until the player's next tick
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
        player.fallDistance = 0;
    }

    /** XP is set, never added, so doing it twice is harmless; the caller says whether items are owed. */
    private static void refund(ServerPlayerEntity player, Journey journey, int percent, boolean returnItems) {
        if (journey.xpPaid > 0) {
            player.setExperienceLevel(journey.xpLevel);
            player.experienceProgress = journey.xpProgress;
            player.totalExperience = journey.xpTotal;
            int kept = journey.xpPaid - TravelMath.refundShare(journey.xpPaid, percent);
            if (kept > 0) player.addExperienceLevels(-kept);
        }
        if (returnItems && !journey.itemsPaid.isEmpty()) {
            Fare.giveBack(player, journey.itemsPaid, TravelMath.refundShare(Fare.total(journey.itemsPaid), percent));
        }
    }

    /** Drop the record, save the player, then write the state file. */
    private static void finish(ServerPlayerEntity player, Journey journey) {
        MinecraftServer server = player.getServer();
        StagecoachState network = StagecoachState.get(server);
        network.removeJourney(journey.player);
        savePlayer(player);
        network.flush(server);
    }

    private static void savePlayer(ServerPlayerEntity player) {
        if (Platform.get().isFakePlayer(player)) return;
        if (player.getServer().getPlayerManager().getPlayer(player.getUuid()) != player) return;
        ((PlayerManagerAccessor) player.getServer().getPlayerManager()).stagecoach$savePlayerData(player);
    }

    /** From ServerPlayerEntityMixin whenever the player is written to NBT. */
    public static void writeMirror(ServerPlayerEntity player, NbtCompound nbt) {
        if (player.getServer() == null) return;
        Journey journey = StagecoachState.get(player.getServer()).journey(player.getUuid());
        if (journey != null) nbt.put(MIRROR_KEY, journey.toNbt(player.getRegistryManager()));
    }

    public static void readMirror(ServerPlayerEntity player, NbtCompound nbt) {
        if (!nbt.contains(MIRROR_KEY)) return;
        Journey journey = Journey.fromNbt(nbt.getCompound(MIRROR_KEY), player.getRegistryManager());
        if (journey != null) LOADED_MIRRORS.put(player.getUuid(), journey);
    }

    /** For GameTests: forget the runtime state the way a crash would. */
    public static void simulateCrash(UUID player) {
        setTravelling(player, false);
    }

    public static @Nullable Journey journeyOf(ServerPlayerEntity player) {
        return StagecoachState.get(player.getServer()).journey(player.getUuid());
    }
}
