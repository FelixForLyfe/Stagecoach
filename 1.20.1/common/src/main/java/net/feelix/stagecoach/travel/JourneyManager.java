package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
import net.feelix.stagecoach.permission.Permissions;
import net.feelix.stagecoach.platform.EventHooks;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    /** Mobs coming along, and the tick bandits strike: runtime only, so after a crash both are simply forgotten. */
    private static final Map<UUID, List<UUID>> COMPANIONS = new HashMap<>();
    /** Companions that were on the traveller's lead: a long ride can snap it, so it's tied again on arrival. */
    private static final Set<UUID> LEASHED = new HashSet<>();
    private static final Object2LongOpenHashMap<UUID> AMBUSH_AT = new Object2LongOpenHashMap<>();

    static {
        AMBUSH_AT.defaultReturnValue(Long.MAX_VALUE);
    }

    public static final String MIRROR_KEY = "StagecoachJourney";

    // leaving is PlayerManagerMixin, not a loader event: Fabric also fires DISCONNECT on Netty

    public static void onServerTick(MinecraftServer server) {
        PendingCoaches.onServerTick(server);
        RoadNetwork.onServerTick(server);
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
            } else if (now >= AMBUSH_AT.getLong(id)) {
                ambush(player, journey, now);
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
        COMPANIONS.clear();
        LEASHED.clear();
        AMBUSH_AT.clear();
        PendingCoaches.onServerStopped();
        RoadNetwork.forget();
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
        return secondsLeft(player, LAST_HURT, Permissions.config(player).combatLockSeconds);
    }

    public static int cooldownSecondsLeft(ServerPlayerEntity player) {
        return secondsLeft(player, LAST_ARRIVAL, Permissions.config(player).travelCooldownSeconds);
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
        StagecoachConfig cfg = Permissions.config(player);
        List<UUID> companions = companions(player, from, cfg);
        boolean canBeAmbushed = from.dimension() == dest.dimension && player.getServerWorld().getDifficulty() != Difficulty.PEACEFUL
                && !player.isCreative() && TravelMath.ambushes(distance, cfg, player.getRandom().nextDouble());
        Journey journey = new Journey(player.getRandom().nextLong(), player.getUuid(), player.interactionManager.getGameMode(),
                player.getAbilities().flying, player.experienceLevel, player.experienceProgress, player.totalExperience, xp,
                items, from, dest, server.getOverworld().getTime(), ticks, distance);

        network.putJourney(journey);
        network.flush(server);                        // on disk before the player changes
        setTravelling(player.getUuid(), true);
        if (!companions.isEmpty()) COMPANIONS.put(player.getUuid(), companions);
        if (canBeAmbushed) AMBUSH_AT.put(player.getUuid(), TravelMath.ambushTick(journey.startTime, ticks, player.getRandom().nextDouble()));
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
        List<UUID> companions = companions(player, from, Permissions.config(player));
        RegistryKey<World> leftFrom = player.getWorld().getRegistryKey();
        if (itemFare && cost > 0) Fare.take(player, cost);
        else if (cost > 0) player.addExperienceLevels(-cost);
        Vec3d spot = SafeSpot.near(world, dest.pos, dest.facing);
        bring(player, companions, leftFrom, world, spot);
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
        bring(player, COMPANIONS.get(player.getUuid()), journey.originDim, dest, spot);
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
        int percent = why == EndReason.CANCELLED ? Permissions.config(player).cancelRefundPercent : 100;
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

    /** Bandits stop the coach part-way: set down there with the unridden share of the fare back, and they attack. */
    private static void ambush(ServerPlayerEntity player, Journey journey, long now) {
        AMBUSH_AT.removeLong(player.getUuid());
        ServerWorld world = player.getServer().getWorld(journey.originDim);
        if (world == null) return;
        double t = MathHelper.clamp((double) (now - journey.startTime) / journey.durationTicks, 0, 1);
        int x = MathHelper.floor(MathHelper.lerp(t, journey.originPos.getX(), journey.destPos.getX()));
        int z = MathHelper.floor(MathHelper.lerp(t, journey.originPos.getZ(), journey.destPos.getZ()));
        int y = MathHelper.floor(MathHelper.lerp(t, journey.originPos.getY(), journey.destPos.getY()));
        // the road runs at the stops' height; a heightmap finds roofs, treetops and the Nether's ceiling
        Vec3d spot = SafeSpot.tryAround(world, new BlockPos(x, y, z));
        if (spot == null && !world.getDimension().hasCeiling()) {
            spot = SafeSpot.tryAround(world, world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)));
        }
        if (spot == null) return;                     // open water or worse: the coach drives on

        StagecoachState network = StagecoachState.get(player.getServer());
        StopView fromView = fromView(network, journey), toView = toView(network, journey);
        setTravelling(player.getUuid(), false);
        bring(player, COMPANIONS.get(player.getUuid()), journey.originDim, world, spot);
        player.teleport(world, spot.x, spot.y, spot.z, player.getYaw(), 0);
        restoreMode(player, journey);
        int percent = TravelMath.unriddenPercent(journey.startTime, journey.durationTicks, now);
        refund(player, journey, percent, true);
        finish(player, journey);
        ServerNet.journeyEnd(player, Packets.END_INTERRUPTED);
        player.sendMessage(Stagecoach.text("end.ambushed", journey.destName, percent).formatted(Formatting.RED), false);
        spawnBandits(world, player, spot);
        Stagecoach.LOGGER.info("{}'s coach to '{}' was ambushed at {}, {}", player.getName().getString(), journey.destName, x, z);
        EventHooks.post(new JourneyEndedEvent(player, fromView, toView, EndReason.AMBUSHED));
    }

    private static void spawnBandits(ServerWorld world, ServerPlayerEntity player, Vec3d spot) {
        StagecoachConfig cfg = Permissions.config(player);
        List<EntityType<?>> types = new ArrayList<>();
        for (String id : cfg.ambushMobs) {
            Optional<EntityType<?>> type = EntityType.get(id);
            if (type.isPresent()) types.add(type.get());
            else Stagecoach.LOGGER.warn("ambushMobs: there is no entity called '{}'", id);
        }
        if (types.isEmpty()) return;
        for (int i = 0; i < cfg.ambushMobCount; i++) {
            float angle = world.random.nextFloat() * MathHelper.TAU;
            double r = 6 + world.random.nextDouble() * 4;
            Vec3d at = SafeSpot.tryAround(world, BlockPos.ofFloored(spot.x + MathHelper.cos(angle) * r, spot.y, spot.z + MathHelper.sin(angle) * r));
            if (at == null) at = spot;
            // this create runs initialize the way each loader wants it (Forge and NeoForge fire their spawn event there)
            Entity e = types.get(world.random.nextInt(types.size())).create(world, null, null, BlockPos.ofFloored(at), SpawnReason.EVENT, false, false);
            if (!(e instanceof MobEntity mob)) {
                if (e != null) e.discard();
                continue;
            }
            world.spawnEntityAndPassengers(mob);
            mob.setTarget(player);                    // after joining: NeoForge drops a target set before
        }
    }

    /** Leashed to the traveller, their own pets that aren't sitting, and any non-hostile mob standing on the coach. */
    private static List<UUID> companions(ServerPlayerEntity player, Journey.Origin from, StagecoachConfig cfg) {
        if (!cfg.companionsTravel) return List.of();
        List<UUID> out = new ArrayList<>();
        for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(cfg.companionRange),
                m -> m.isAlive() && !m.hasVehicle())) {
            if (mob.getHoldingEntity() == player || mob instanceof TameableEntity pet && pet.isOwner(player) && !pet.isSitting()
                    || !(mob instanceof Monster) && from.isCoach() && onCoach(mob, from.pos())) {
                out.add(mob.getUuid());
                if (mob.getHoldingEntity() == player) LEASHED.add(mob.getUuid());
            }
        }
        return out;
    }

    private static boolean onCoach(MobEntity mob, BlockPos controller) {
        BlockPos under = mob.getSteppingPos();
        BlockState state = mob.getWorld().getBlockState(under);
        return Hitching.isCoach(state) && Hitching.controllerOf(under, state).equals(controller);
    }

    /** shortcut: within one dimension only; moving mobs between dimensions differs per loader, so they stay behind. */
    private static void bring(ServerPlayerEntity player, @Nullable List<UUID> ids, RegistryKey<World> from, ServerWorld to, Vec3d spot) {
        if (ids == null || from != to.getRegistryKey()) return;
        for (UUID id : ids) {
            if (!(to.getEntity(id) instanceof MobEntity mob) || !mob.isAlive()) continue;
            mob.requestTeleport(spot.x, spot.y, spot.z);
            mob.fallDistance = 0;
            mob.getNavigation().stop();
            if (LEASHED.remove(id) && !mob.isLeashed()) mob.attachLeash(player, true);
        }
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
        Permissions.forget(player.getUuid());
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
        COMPANIONS.remove(journey.player);
        AMBUSH_AT.removeLong(journey.player);
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
        if (journey != null) nbt.put(MIRROR_KEY, journey.toNbt());
    }

    public static void readMirror(ServerPlayerEntity player, NbtCompound nbt) {
        if (!nbt.contains(MIRROR_KEY)) return;
        Journey journey = Journey.fromNbt(nbt.getCompound(MIRROR_KEY));
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
