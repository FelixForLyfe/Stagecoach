package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.advancement.StagecoachTriggers;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.Destination;
import net.feelix.stagecoach.api.JourneyOptions;
import net.feelix.stagecoach.api.JourneyResult;
import net.feelix.stagecoach.api.event.CoachBreakEvent;
import net.feelix.stagecoach.api.event.CoachPlaceEvent;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.net.ServerNet;
import net.feelix.stagecoach.permission.Permissions;
import net.feelix.stagecoach.platform.EventHooks;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clients only send ids and a name; everything else is worked out and checked here. */
public final class StagecoachService {
    private StagecoachService() {}

    public record Outcome(JourneyResult result, @Nullable Text message) {
        static final Outcome STARTED = new Outcome(JourneyResult.STARTED, null);
        static final Outcome UNAVAILABLE = new Outcome(JourneyResult.UNAVAILABLE, null);
    }

    public static boolean canManage(ServerPlayerEntity player) {
        return Permissions.check(player, Permissions.Node.ADMIN_MANAGE);
    }

    /** Rules only the server knows; null lets the placement go ahead. */
    public static @Nullable Text placementRefusal(ServerWorld world, @Nullable PlayerEntity player, BlockPos pos, BlockState controller) {
        StagecoachConfig cfg = StagecoachConfig.get();
        if (cfg.blockedDimensions.contains(world.getRegistryKey().getValue().toString())) return Stagecoach.text("place.dimension");
        ServerPlayerEntity sp = player instanceof ServerPlayerEntity p ? p : null;
        if (sp != null) {
            if (!Permissions.check(sp, Permissions.Node.PLACE)) return Stagecoach.text("place.no_permission");
            if (cfg.maxCoachesPerPlayer > 0 && !Permissions.check(sp, Permissions.Node.BYPASS_LIMIT)
                    && StagecoachState.get(world.getServer()).countOwnedBy(sp.getUuid()) >= cfg.maxCoachesPerPlayer) {
                return Stagecoach.text("place.limit", cfg.maxCoachesPerPlayer);
            }
        }
        StagecoachWood wood = ((StagecoachBlock) controller.getBlock()).wood();
        CoachPlaceEvent event = new CoachPlaceEvent(sp, world, pos, controller.get(StagecoachBlock.FACING), wood.asString(), null);
        if (EventHooks.post(event)) return event.getFailMessage() != null ? event.getFailMessage() : Stagecoach.text("place.cancelled");
        return null;
    }

    public static void onPlaced(ServerWorld world, BlockPos pos, BlockState state, @Nullable ServerPlayerEntity placer) {
        StagecoachState network = StagecoachState.get(world.getServer());
        StagecoachWood wood = ((StagecoachBlock) state.getBlock()).wood();
        CoachRecord record = network.register(pos, world.getRegistryKey(), state.get(StagecoachBlock.FACING), wood,
                placer == null ? null : placer.getUuid(), CreatedBy.PLAYER, null);
        if (world.getBlockEntity(pos) instanceof StagecoachBlockEntity be) be.setCoachId(record.id);
        ServerNet.knownStopsChanged();
        if (placer == null) return;
        network.discover(placer.getUuid(), record.id);
        StagecoachTriggers.coachPlaced(placer, wood.asString(), world.getRegistryKey().getValue().toString());
        ServerNet.placeEffect(world, record);
        ServerNet.openNaming(placer, record, false);
    }

    /** Only when the record really is this coach: a structure copy can carry another coach's id. */
    public static void onControllerRemoved(ServerWorld world, BlockPos pos, long coachId) {
        StagecoachState network = StagecoachState.get(world.getServer());
        CoachRecord record = network.coach(coachId);
        if (record == null || record.dimension != world.getRegistryKey() || !record.pos.equals(pos)) return;
        network.remove(coachId);
        ServerNet.knownStopsChanged();
    }

    /** Null lets the player break it; an unregistered coach has nothing to protect. */
    public static @Nullable Text breakRefusal(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return null;
        CoachRecord record = recordAt(world, pos, state);
        if (record == null) return null;
        if (record.locked) return Stagecoach.text("error.locked_break");
        boolean allowed;
        if (record.owner == null) allowed = canManage(sp) || Permissions.check(sp, Permissions.Node.BREAK_ANY);
        else if (record.isOwner(sp.getUuid())) allowed = Permissions.check(sp, Permissions.Node.BREAK_OWN);
        else allowed = !StagecoachConfig.get().onlyOwnerCanBreak || Permissions.check(sp, Permissions.Node.BREAK_ANY) || canManage(sp);
        if (!allowed) return Stagecoach.text("error.not_owner_break");
        CoachBreakEvent event = new CoachBreakEvent(sp, record.view(), null);
        if (EventHooks.post(event)) return event.getFailMessage() != null ? event.getFailMessage() : Stagecoach.text("error.break_cancelled");
        return null;
    }

    public static void onUse(ServerWorld world, BlockPos controllerPos, ServerPlayerEntity player) {
        if (world.getBlockEntity(controllerPos) instanceof StagecoachBlockEntity be && be.isDecoration()) {
            player.sendMessage(Stagecoach.text("decoration").formatted(Formatting.GRAY), true);
            return;
        }
        CoachRecord record = PendingCoaches.check(world, controllerPos);
        if (record == null) return;
        StagecoachState network = StagecoachState.get(world.getServer());
        if (StagecoachConfig.get().discoveryRequired && !knows(network, player, record)) {
            discoverByHand(player, record, network);
            return;
        }
        network.discover(player.getUuid(), record.id);
        ServerNet.openList(player, record, destinations(player, record));
    }

    private static void discoverByHand(ServerPlayerEntity player, CoachRecord record, StagecoachState network) {
        if (!Permissions.check(player, Permissions.Node.DISCOVER)) {
            deny(player, Stagecoach.text("error.no_discover"));
            return;
        }
        if (EventHooks.post(new StopDiscoverEvent(player, record.view()))) return;
        network.discover(player.getUuid(), record.id);
        StagecoachTriggers.stopDiscovered(player, record.name, record.id, network.discoveredCount(player.getUuid()),
                record.dimension.getValue().toString());
        player.sendMessage(Stagecoach.text("discovered", record.name).formatted(Formatting.GOLD), true);
        ServerNet.discoverEffect(player, record);
        ServerNet.knownStops(player);
    }

    /** {@code /stagecoach discover}: listeners may still refuse; no discovery effect. True if it was new. */
    public static boolean discoverByCommand(ServerPlayerEntity player, CoachRecord record) {
        StagecoachState network = StagecoachState.get(player.getServer());
        if (network.hasDiscovered(player.getUuid(), record.id)) return false;
        if (EventHooks.post(new StopDiscoverEvent(player, record.view()))) return false;
        network.discover(player.getUuid(), record.id);
        StagecoachTriggers.stopDiscovered(player, record.name, record.id, network.discoveredCount(player.getUuid()),
                record.dimension.getValue().toString());
        return true;
    }

    public static void rename(ServerPlayerEntity player, Packets.SetName request) {
        StagecoachState network = StagecoachState.get(player.getServer());
        CoachRecord record = network.coach(request.coachId());
        if (record == null || !exists(player.getServer().getWorld(record.dimension), record) || !near(player, record)) return;
        if (JourneyManager.isTravelling(player.getUuid())) return;
        if (!mayRename(player, record)) {
            player.sendMessage(Stagecoach.text("error.not_owner").formatted(Formatting.RED), true);
            return;
        }
        int max = StagecoachConfig.get().maxNameLength;
        String name = CoachNames.clean(request.name(), max);
        if (name == null) {
            player.sendMessage(Stagecoach.text("error.bad_name", max).formatted(Formatting.RED), true);
            return;
        }
        network.rename(record, name);
        player.sendMessage(Stagecoach.text("renamed", name).formatted(Formatting.GOLD), true);
        if (request.fromList()) ServerNet.openList(player, record, destinations(player, record));
    }

    private static boolean mayRename(ServerPlayerEntity player, CoachRecord record) {
        return record.isOwner(player.getUuid()) ? Permissions.check(player, Permissions.Node.RENAME_OWN) : canManage(player);
    }

    /** A request from the destination list: only an OK entry of the list the server builds now can be travelled to. */
    public static void travel(ServerPlayerEntity player, Packets.Travel request) {
        if (JourneyManager.isTravelling(player.getUuid()) || player.isSpectator() || !player.isAlive()) return;
        StagecoachState network = StagecoachState.get(player.getServer());
        CoachRecord origin = network.coach(request.originId());
        if (origin == null || origin.id == request.destId()) return;
        if (!exists(player.getServer().getWorld(origin.dimension), origin) || !near(player, origin) || !knows(network, player, origin)) return;
        Destination entry = null;
        for (Destination d : destinations(player, origin)) {
            if (d.stop().id() == request.destId()) {
                entry = d;
                break;
            }
        }
        CoachRecord dest = network.coach(request.destId());
        if (entry == null || dest == null) return;
        // affordability is checked again after JourneyPreStart, which may change the fare
        Text refusal = entry.status() == Destination.Status.CANT_AFFORD ? null : statusRefusal(entry);
        if (refusal != null) {
            deny(player, refusal);
            return;
        }
        Outcome outcome = begin(player, Journey.Origin.of(origin), origin, dest, entry, JourneyOptions.NORMAL);
        if (outcome.message() != null) deny(player, outcome.message());
    }

    /** The API and {@code journey send}: from the nearest known stop in reach, or with ignoreRange from where they stand. */
    public static Outcome startJourney(ServerPlayerEntity player, CoachRecord dest, JourneyOptions options) {
        if (JourneyManager.isTravelling(player.getUuid())) return new Outcome(JourneyResult.ALREADY_TRAVELLING, Stagecoach.text("error.travelling"));
        if (player.isSpectator() || !player.isAlive() || Platform.get().isFakePlayer(player)) return Outcome.UNAVAILABLE;
        StagecoachState network = StagecoachState.get(player.getServer());
        CoachRecord originCoach = null;
        Journey.Origin from;
        if (options.ignoreRange()) {
            from = Journey.Origin.here(player.getWorld().getRegistryKey(), player.getPos(), player.getHorizontalFacing());
        } else {
            originCoach = nearestKnown(player, network, options.ignoreDiscovery());
            if (originCoach == null) return new Outcome(JourneyResult.OUT_OF_RANGE, Stagecoach.text("error.no_stop_near"));
            if (originCoach.id == dest.id) return Outcome.UNAVAILABLE;
            from = Journey.Origin.of(originCoach);
        }
        if (!options.ignoreDiscovery() && !knows(network, player, dest)) {
            return new Outcome(JourneyResult.NOT_DISCOVERED, Stagecoach.text("error.not_discovered", dest.name));
        }
        Destination entry = quote(player, from, dest, options.free(), Fare.itemMode(), balance(player));
        return switch (entry.status()) {
            case OTHER_DIMENSION -> new Outcome(JourneyResult.CROSS_DIMENSION_DISABLED, statusRefusal(entry));
            case TOO_FAR -> new Outcome(JourneyResult.TOO_FAR, statusRefusal(entry));
            case LOCKED -> new Outcome(JourneyResult.LOCKED_BY_RULE, statusRefusal(entry));
            default -> begin(player, from, originCoach, dest, entry, options);
        };
    }

    /** The checks the list can't make (they change from tick to tick), then the event, then payment. */
    private static Outcome begin(ServerPlayerEntity player, Journey.Origin from, @Nullable CoachRecord originCoach, CoachRecord dest,
                                 Destination entry, JourneyOptions options) {
        StagecoachConfig cfg = StagecoachConfig.get();
        if (!Permissions.check(player, Permissions.Node.TRAVEL)) {
            return new Outcome(JourneyResult.NO_PERMISSION, Stagecoach.text("error.no_permission_travel"));
        }
        int wait = JourneyManager.cooldownSecondsLeft(player);
        if (wait > 0) return new Outcome(JourneyResult.COOLDOWN, Stagecoach.text("error.cooldown", wait));
        int lock = options.ignoreCombatLock() ? 0 : JourneyManager.combatLockSecondsLeft(player);
        if (lock > 0) return new Outcome(JourneyResult.COMBAT_LOCKED, Stagecoach.text("error.combat", lock));

        boolean itemFare = Fare.itemMode();
        boolean instant = options.instant() || cfg.instantTravel;
        JourneyPreStartEvent event = new JourneyPreStartEvent(player, originCoach == null ? null : originCoach.view(), dest.view(),
                entry.cost(), itemFare, entry.seconds(), instant, null);
        if (EventHooks.post(event)) {
            return new Outcome(JourneyResult.CANCELLED_BY_EVENT,
                    event.getFailMessage() != null ? event.getFailMessage() : Stagecoach.text("error.cancelled"));
        }
        int cost = Math.max(0, event.getCost());
        if (itemFare && cost > 0 && Fare.count(player) < cost) {
            return new Outcome(JourneyResult.NOT_ENOUGH_ITEMS, Stagecoach.text("error.no_items", cost, Fare.icon().getName()));
        }
        if (!itemFare && cost > 0 && player.experienceLevel < cost) {
            return new Outcome(JourneyResult.NOT_ENOUGH_LEVELS, Stagecoach.text("error.no_xp", cost));
        }
        double distance = distance(from, dest, cfg);
        int ticks = event.getTravelSeconds() == entry.seconds() ? TravelMath.ticks(distance, cfg) : Math.max(0, event.getTravelSeconds()) * 20;
        if (instant) JourneyManager.travelInstantly(player, from, dest, itemFare, cost, entry.distance());
        else JourneyManager.start(player, from, dest, itemFare, cost, ticks, entry.distance(), stops(player, from, dest));
        return Outcome.STARTED;
    }

    private static @Nullable Text statusRefusal(Destination d) {
        return switch (d.status()) {
            case OK -> null;
            case OTHER_DIMENSION -> Stagecoach.text("error.other_dimension");
            case TOO_FAR -> Stagecoach.text("error.too_far", StagecoachConfig.get().maxDistance);
            case LOCKED -> d.lockMessage() != null ? d.lockMessage() : Stagecoach.text("error.locked");
            case CANT_AFFORD -> Fare.itemMode() ? Stagecoach.text("error.no_items", d.cost(), Fare.icon().getName())
                    : Stagecoach.text("error.no_xp", d.cost());
        };
    }

    /** Free when the option or the stop says so, in creative while creativeTravelsFree is on, or with bypass.cost. */
    public static boolean travelsFree(ServerPlayerEntity player) {
        return StagecoachConfig.get().creativeTravelsFree && player.isCreative() || Permissions.check(player, Permissions.Node.BYPASS_COST);
    }

    private static void deny(ServerPlayerEntity player, Text why) {
        player.sendMessage(why.copy().formatted(Formatting.RED), true);
        player.playSoundToPlayer(SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.6f, 1.0f);
    }

    private static int balance(ServerPlayerEntity player) {
        return Fare.itemMode() ? Fare.count(player) : player.experienceLevel;
    }

    /** One price for the list and the charge: base, then each multiplier, rounded up once, then capped. */
    public static Destination quote(ServerPlayerEntity player, Journey.Origin from, CoachRecord to, boolean freeOption, boolean itemFare,
                                    int balance) {
        StagecoachConfig cfg = StagecoachConfig.get();
        boolean cross = from.dimension() != to.dimension;
        double distance = distance(from, to, cfg);
        TravelRules.Verdict verdict = TravelRules.evaluate(player, from, to);
        double multiplier = to.costMultiplier * verdict.multiplier()
                * (cross ? cfg.crossDimensionCostMultiplier : cfg.dimensionCostMultiplier(to.dimension.getValue().toString()));
        boolean free = freeOption || to.free || travelsFree(player);
        int cost = free ? 0 : TravelMath.fare(TravelMath.rawCost(distance, cfg, itemFare), multiplier, itemFare ? cfg.maxItemCost : cfg.maxCost);
        Destination.Status status;
        if (cross && !cfg.allowCrossDimension) status = Destination.Status.OTHER_DIMENSION;
        else if (TravelMath.tooFar(distance, cfg)) status = Destination.Status.TOO_FAR;
        else if (!verdict.allowed()) status = Destination.Status.LOCKED;
        else if (cost > 0 && balance < cost) status = Destination.Status.CANT_AFFORD;
        else status = Destination.Status.OK;
        return new Destination(to.view(), (int) Math.round(distance), cost, (int) Math.ceil(TravelMath.seconds(distance, cfg)), status,
                verdict.message());
    }

    /** Every stop this player may see from {@code here}, nearest first, after listeners have had their say. */
    public static List<Destination> destinations(ServerPlayerEntity player, CoachRecord here) {
        StagecoachState network = StagecoachState.get(player.getServer());
        boolean itemFare = Fare.itemMode();
        int balance = balance(player);
        Journey.Origin from = Journey.Origin.of(here);
        List<Destination> list = new ArrayList<>();
        for (CoachRecord c : network.coaches()) {
            if (c.id == here.id || c.hidden || !knows(network, player, c)) continue;
            list.add(quote(player, from, c, false, itemFare, balance));
        }
        list.sort(Comparator.comparingInt(Destination::distance));
        EventHooks.post(new DestinationListEvent(player, here.view(), list));
        for (int i = 0; i < list.size(); i++) {
            Destination d = list.get(i);
            if (d.status() != Destination.Status.OK && d.status() != Destination.Status.CANT_AFFORD) continue;
            Destination.Status s = d.cost() > 0 && balance < d.cost() ? Destination.Status.CANT_AFFORD : Destination.Status.OK;
            if (s != d.status()) list.set(i, d.withStatus(s, null));
        }
        return list;
    }

    public static Packets.DestinationList buildList(ServerPlayerEntity player, CoachRecord here) {
        return listPacket(player, here, destinations(player, here));
    }

    public static Packets.DestinationList listPacket(ServerPlayerEntity player, CoachRecord here, List<Destination> destinations) {
        StagecoachConfig cfg = StagecoachConfig.get();
        boolean itemFare = Fare.itemMode();
        List<Packets.Entry> entries = new ArrayList<>(destinations.size());
        for (Destination d : destinations) {
            entries.add(new Packets.Entry(d.stop().id(), d.stop().name(), d.stop().pos(), d.stop().dimension().getValue(), d.distance(),
                    d.cost(), d.seconds(), d.stop().owner().map(player.getUuid()::equals).orElse(false), status(d.status()),
                    d.lockMessage()));
        }
        Identifier icon = Registries.ITEM.getId(itemFare ? Fare.icon().getItem() : Items.EXPERIENCE_BOTTLE);
        return new Packets.DestinationList(here.id, here.name, mayRename(player, here), travelsFree(player), itemFare, icon,
                balance(player), cfg.maxDistance, cfg.maxNameLength, entries.size(), entries);
    }

    private static byte status(Destination.Status s) {
        return switch (s) {
            case OK -> Packets.STATUS_OK;
            case CANT_AFFORD -> Packets.STATUS_CANT_AFFORD;
            case OTHER_DIMENSION -> Packets.STATUS_OTHER_DIMENSION;
            case TOO_FAR -> Packets.STATUS_TOO_FAR;
            case LOCKED -> Packets.STATUS_LOCKED;
        };
    }

    private static int[] stops(ServerPlayerEntity player, Journey.Origin from, CoachRecord dest) {
        StagecoachState network = StagecoachState.get(player.getServer());
        List<CoachRecord> same = network.coaches(from.dimension());
        int[] out = new int[Math.min(same.size(), Packets.MAX_STOPS) * 2];
        int n = 0;
        for (CoachRecord c : same) {
            if (n == out.length) break;
            if (c.id == from.id() || c.id == dest.id || c.hidden || !knows(network, player, c)) continue;
            out[n++] = c.pos.getX();
            out[n++] = c.pos.getZ();
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    /** Real distances mean nothing between dimensions, so those journeys use crossDimensionDistance. */
    static double distance(Journey.Origin from, CoachRecord to, StagecoachConfig cfg) {
        if (from.dimension() != to.dimension) return cfg.crossDimensionDistance;
        return TravelMath.horizontalDistance(from.pos().getX(), from.pos().getZ(), to.pos.getX(), to.pos.getZ());
    }

    /** Placed, discovered, public, or every coach when discoveryRequired is off. */
    public static boolean knows(StagecoachState network, ServerPlayerEntity player, CoachRecord c) {
        return !StagecoachConfig.get().discoveryRequired || c.isPublic || c.isOwner(player.getUuid())
                || network.hasDiscovered(player.getUuid(), c.id);
    }

    private static @Nullable CoachRecord nearestKnown(ServerPlayerEntity player, StagecoachState network, boolean ignoreDiscovery) {
        CoachRecord best = null;
        double bestSq = Double.MAX_VALUE;
        double range = StagecoachConfig.get().useRange;
        for (CoachRecord c : network.coaches(player.getWorld().getRegistryKey())) {
            if (!ignoreDiscovery && !knows(network, player, c)) continue;
            double sq = player.getPos().squaredDistanceTo(Vec3d.ofCenter(c.pos));
            if (sq <= range * range && sq < bestSq) {
                best = c;
                bestSq = sq;
            }
        }
        return best;
    }

    private static boolean near(ServerPlayerEntity player, CoachRecord c) {
        double range = StagecoachConfig.get().useRange;
        return player.getWorld().getRegistryKey() == c.dimension && player.getPos().squaredDistanceTo(Vec3d.ofCenter(c.pos)) <= range * range;
    }

    static boolean exists(@Nullable ServerWorld world, CoachRecord c) {
        return world != null && world.getBlockEntity(c.pos) instanceof StagecoachBlockEntity be && be.getCoachId() == c.id;
    }

    /** Called for every block an explosion reaches, so it bails out on anything that isn't a coach first. */
    public static boolean isLockedCoach(BlockView view, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StagecoachBlock) && !(state.getBlock() instanceof StagecoachPartBlock)) return false;
        if (!(view instanceof World world) || world.getServer() == null) return false;
        CoachRecord record = recordAt(world, pos, state);
        return record != null && record.locked;
    }

    private static @Nullable CoachRecord recordAt(World world, BlockPos pos, BlockState state) {
        BlockPos controller = state.getBlock() instanceof StagecoachPartBlock ? StagecoachStructure.controllerPos(pos, state) : pos;
        if (!(world.getBlockEntity(controller) instanceof StagecoachBlockEntity be) || world.getServer() == null) return null;
        CoachRecord record = StagecoachState.get(world.getServer()).coach(be.getCoachId());
        return record != null && record.pos.equals(controller) ? record : null;
    }
}
