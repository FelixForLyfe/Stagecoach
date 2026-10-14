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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clients only send ids and a name; everything else is worked out and checked here. */
public final class StagecoachService {
    private StagecoachService() {}

    public record Outcome(JourneyResult result, @Nullable Component message) {
        static final Outcome STARTED = new Outcome(JourneyResult.STARTED, null);
        static final Outcome UNAVAILABLE = new Outcome(JourneyResult.UNAVAILABLE, null);
    }

    public static boolean canManage(ServerPlayer player) {
        return Permissions.check(player, Permissions.Node.ADMIN_MANAGE);
    }

    /** Rules only the server knows; null lets the placement go ahead. */
    public static @Nullable Component placementRefusal(ServerLevel level, @Nullable Player player, BlockPos pos, BlockState controller) {
        StagecoachConfig cfg = StagecoachConfig.get();
        if (cfg.blockedDimensions.contains(level.dimension().identifier().toString())) return Stagecoach.text("place.dimension");
        ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
        if (sp != null) {
            if (!Permissions.check(sp, Permissions.Node.PLACE)) return Stagecoach.text("place.no_permission");
            if (cfg.maxCoachesPerPlayer > 0 && !Permissions.check(sp, Permissions.Node.BYPASS_LIMIT)
                    && StagecoachState.get(level.getServer()).countOwnedBy(sp.getUUID()) >= cfg.maxCoachesPerPlayer) {
                return Stagecoach.text("place.limit", cfg.maxCoachesPerPlayer);
            }
        }
        StagecoachWood wood = ((StagecoachBlock) controller.getBlock()).wood();
        CoachPlaceEvent event = new CoachPlaceEvent(sp, level, pos, controller.getValue(StagecoachBlock.FACING), wood.getSerializedName(), null);
        if (EventHooks.post(event)) return event.getFailMessage() != null ? event.getFailMessage() : Stagecoach.text("place.cancelled");
        return null;
    }

    public static void onPlaced(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer placer) {
        StagecoachState network = StagecoachState.get(level.getServer());
        StagecoachWood wood = ((StagecoachBlock) state.getBlock()).wood();
        CoachRecord record = network.register(pos, level.dimension(), state.getValue(StagecoachBlock.FACING), wood,
                placer == null ? null : placer.getUUID(), CreatedBy.PLAYER, null);
        if (level.getBlockEntity(pos) instanceof StagecoachBlockEntity be) be.setCoachId(record.id);
        ServerNet.knownStopsChanged();
        if (placer == null) return;
        network.discover(placer.getUUID(), record.id);
        StagecoachTriggers.coachPlaced(placer, wood.getSerializedName(), level.dimension().identifier().toString());
        ServerNet.placeEffect(level, record);
        ServerNet.openNaming(placer, record, false);
    }

    /** Only when the record really is this coach: a structure copy can carry another coach's id. */
    public static void onControllerRemoved(ServerLevel level, BlockPos pos, long coachId) {
        StagecoachState network = StagecoachState.get(level.getServer());
        CoachRecord record = network.coach(coachId);
        if (record == null || record.dimension != level.dimension() || !record.pos.equals(pos)) return;
        network.remove(coachId);
        ServerNet.knownStopsChanged();
    }

    /** Null lets the player break it; an unregistered coach has nothing to protect. */
    public static @Nullable Component breakRefusal(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(player instanceof ServerPlayer sp)) return null;
        CoachRecord record = recordAt(level, pos, state);
        if (record == null) return null;
        if (record.locked) return Stagecoach.text("error.locked_break");
        boolean allowed;
        if (record.owner == null) allowed = canManage(sp) || Permissions.check(sp, Permissions.Node.BREAK_ANY);
        else if (record.isOwner(sp.getUUID())) allowed = Permissions.check(sp, Permissions.Node.BREAK_OWN);
        else allowed = !StagecoachConfig.get().onlyOwnerCanBreak || Permissions.check(sp, Permissions.Node.BREAK_ANY) || canManage(sp);
        if (!allowed) return Stagecoach.text("error.not_owner_break");
        CoachBreakEvent event = new CoachBreakEvent(sp, record.view(), null);
        if (EventHooks.post(event)) return event.getFailMessage() != null ? event.getFailMessage() : Stagecoach.text("error.break_cancelled");
        return null;
    }

    public static void onUse(ServerLevel level, BlockPos controllerPos, ServerPlayer player) {
        if (level.getBlockEntity(controllerPos) instanceof StagecoachBlockEntity be && be.isDecoration()) {
            player.displayClientMessage(Stagecoach.text("decoration").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CoachRecord record = PendingCoaches.check(level, controllerPos);
        if (record == null) return;
        StagecoachState network = StagecoachState.get(level.getServer());
        if (StagecoachConfig.get().discoveryRequired && !knows(network, player, record)) {
            discoverByHand(player, record, network);
            return;
        }
        network.discover(player.getUUID(), record.id);
        ServerNet.openList(player, record, destinations(player, record));
    }

    private static void discoverByHand(ServerPlayer player, CoachRecord record, StagecoachState network) {
        if (!Permissions.check(player, Permissions.Node.DISCOVER)) {
            deny(player, Stagecoach.text("error.no_discover"));
            return;
        }
        if (EventHooks.post(new StopDiscoverEvent(player, record.view()))) return;
        network.discover(player.getUUID(), record.id);
        StagecoachTriggers.stopDiscovered(player, record.name, record.id, network.discoveredCount(player.getUUID()),
                record.dimension.identifier().toString());
        player.displayClientMessage(Stagecoach.text("discovered", record.name).withStyle(ChatFormatting.GOLD), true);
        ServerNet.discoverEffect(player, record);
        ServerNet.knownStops(player);
    }

    /** {@code /stagecoach discover}: listeners may still refuse; no discovery effect. True if it was new. */
    public static boolean discoverByCommand(ServerPlayer player, CoachRecord record) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
        if (network.hasDiscovered(player.getUUID(), record.id)) return false;
        if (EventHooks.post(new StopDiscoverEvent(player, record.view()))) return false;
        network.discover(player.getUUID(), record.id);
        StagecoachTriggers.stopDiscovered(player, record.name, record.id, network.discoveredCount(player.getUUID()),
                record.dimension.identifier().toString());
        return true;
    }

    public static void rename(ServerPlayer player, Packets.SetName request) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
        CoachRecord record = network.coach(request.coachId());
        if (record == null || !exists(player.level().getServer().getLevel(record.dimension), record) || !near(player, record)) return;
        if (JourneyManager.isTravelling(player.getUUID())) return;
        if (!mayRename(player, record)) {
            player.displayClientMessage(Stagecoach.text("error.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        int max = StagecoachConfig.get().maxNameLength;
        String name = CoachNames.clean(request.name(), max);
        if (name == null) {
            player.displayClientMessage(Stagecoach.text("error.bad_name", max).withStyle(ChatFormatting.RED), true);
            return;
        }
        network.rename(record, name);
        player.displayClientMessage(Stagecoach.text("renamed", name).withStyle(ChatFormatting.GOLD), true);
        if (request.fromList()) ServerNet.openList(player, record, destinations(player, record));
    }

    private static boolean mayRename(ServerPlayer player, CoachRecord record) {
        return record.isOwner(player.getUUID()) ? Permissions.check(player, Permissions.Node.RENAME_OWN) : canManage(player);
    }

    /** A request from the destination list: only an OK entry of the list the server builds now can be travelled to. */
    public static void travel(ServerPlayer player, Packets.Travel request) {
        if (JourneyManager.isTravelling(player.getUUID()) || player.isSpectator() || !player.isAlive()) return;
        StagecoachState network = StagecoachState.get(player.level().getServer());
        CoachRecord origin = network.coach(request.originId());
        if (origin == null || origin.id == request.destId()) return;
        if (!exists(player.level().getServer().getLevel(origin.dimension), origin) || !near(player, origin) || !knows(network, player, origin)) return;
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
        Component refusal = entry.status() == Destination.Status.CANT_AFFORD ? null : statusRefusal(entry);
        if (refusal != null) {
            deny(player, refusal);
            return;
        }
        Outcome outcome = begin(player, Journey.Origin.of(origin), origin, dest, entry, JourneyOptions.NORMAL);
        if (outcome.message() != null) deny(player, outcome.message());
    }

    /** The API and {@code journey send}: from the nearest known stop in reach, or with ignoreRange from where they stand. */
    public static Outcome startJourney(ServerPlayer player, CoachRecord dest, JourneyOptions options) {
        if (JourneyManager.isTravelling(player.getUUID())) return new Outcome(JourneyResult.ALREADY_TRAVELLING, Stagecoach.text("error.travelling"));
        if (player.isSpectator() || !player.isAlive() || Platform.get().isFakePlayer(player)) return Outcome.UNAVAILABLE;
        StagecoachState network = StagecoachState.get(player.level().getServer());
        CoachRecord originCoach = null;
        Journey.Origin from;
        if (options.ignoreRange()) {
            from = Journey.Origin.here(player.level().dimension(), player.position(), player.getDirection());
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
    private static Outcome begin(ServerPlayer player, Journey.Origin from, @Nullable CoachRecord originCoach, CoachRecord dest,
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
            return new Outcome(JourneyResult.NOT_ENOUGH_ITEMS, Stagecoach.text("error.no_items", cost, Fare.icon().getHoverName()));
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

    private static @Nullable Component statusRefusal(Destination d) {
        return switch (d.status()) {
            case OK -> null;
            case OTHER_DIMENSION -> Stagecoach.text("error.other_dimension");
            case TOO_FAR -> Stagecoach.text("error.too_far", StagecoachConfig.get().maxDistance);
            case LOCKED -> d.lockMessage() != null ? d.lockMessage() : Stagecoach.text("error.locked");
            case CANT_AFFORD -> Fare.itemMode() ? Stagecoach.text("error.no_items", d.cost(), Fare.icon().getHoverName())
                    : Stagecoach.text("error.no_xp", d.cost());
        };
    }

    /** Free when the option or the stop says so, in creative while creativeTravelsFree is on, or with bypass.cost. */
    public static boolean travelsFree(ServerPlayer player) {
        return StagecoachConfig.get().creativeTravelsFree && player.isCreative() || Permissions.check(player, Permissions.Node.BYPASS_COST);
    }

    private static void deny(ServerPlayer player, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.HORSE_ANGRY), SoundSource.NEUTRAL,
                player.getX(), player.getY(), player.getZ(), 0.6f, 1.0f, player.getRandom().nextLong()));
    }

    private static int balance(ServerPlayer player) {
        return Fare.itemMode() ? Fare.count(player) : player.experienceLevel;
    }

    /** One price for the list and the charge: base, then each multiplier, rounded up once, then capped. */
    public static Destination quote(ServerPlayer player, Journey.Origin from, CoachRecord to, boolean freeOption, boolean itemFare,
                                    int balance) {
        StagecoachConfig cfg = StagecoachConfig.get();
        boolean cross = from.dimension() != to.dimension;
        double distance = distance(from, to, cfg);
        TravelRules.Verdict verdict = TravelRules.evaluate(player, from, to);
        double multiplier = to.costMultiplier * verdict.multiplier()
                * (cross ? cfg.crossDimensionCostMultiplier : cfg.dimensionCostMultiplier(to.dimension.identifier().toString()));
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
    public static List<Destination> destinations(ServerPlayer player, CoachRecord here) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
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

    public static Packets.DestinationList buildList(ServerPlayer player, CoachRecord here) {
        return listPacket(player, here, destinations(player, here));
    }

    public static Packets.DestinationList listPacket(ServerPlayer player, CoachRecord here, List<Destination> destinations) {
        StagecoachConfig cfg = StagecoachConfig.get();
        boolean itemFare = Fare.itemMode();
        List<Packets.Entry> entries = new ArrayList<>(destinations.size());
        for (Destination d : destinations) {
            entries.add(new Packets.Entry(d.stop().id(), d.stop().name(), d.stop().pos(), d.stop().dimension().identifier(), d.distance(),
                    d.cost(), d.seconds(), d.stop().owner().map(player.getUUID()::equals).orElse(false), status(d.status()),
                    d.lockMessage()));
        }
        Identifier icon = BuiltInRegistries.ITEM.getKey(itemFare ? Fare.icon().getItem() : Items.EXPERIENCE_BOTTLE);
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

    private static int[] stops(ServerPlayer player, Journey.Origin from, CoachRecord dest) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
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
    public static boolean knows(StagecoachState network, ServerPlayer player, CoachRecord c) {
        return !StagecoachConfig.get().discoveryRequired || c.isPublic || c.isOwner(player.getUUID())
                || network.hasDiscovered(player.getUUID(), c.id);
    }

    private static @Nullable CoachRecord nearestKnown(ServerPlayer player, StagecoachState network, boolean ignoreDiscovery) {
        CoachRecord best = null;
        double bestSq = Double.MAX_VALUE;
        double range = StagecoachConfig.get().useRange;
        for (CoachRecord c : network.coaches(player.level().dimension())) {
            if (!ignoreDiscovery && !knows(network, player, c)) continue;
            double sq = player.position().distanceToSqr(Vec3.atCenterOf(c.pos));
            if (sq <= range * range && sq < bestSq) {
                best = c;
                bestSq = sq;
            }
        }
        return best;
    }

    private static boolean near(ServerPlayer player, CoachRecord c) {
        double range = StagecoachConfig.get().useRange;
        return player.level().dimension() == c.dimension && player.position().distanceToSqr(Vec3.atCenterOf(c.pos)) <= range * range;
    }

    static boolean exists(@Nullable ServerLevel level, CoachRecord c) {
        return level != null && level.getBlockEntity(c.pos) instanceof StagecoachBlockEntity be && be.getCoachId() == c.id;
    }

    /** Called for every block an explosion reaches, so it bails out on anything that isn't a coach first. */
    public static boolean isLockedCoach(BlockGetter view, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StagecoachBlock) && !(state.getBlock() instanceof StagecoachPartBlock)) return false;
        if (!(view instanceof Level level) || level.getServer() == null) return false;
        CoachRecord record = recordAt(level, pos, state);
        return record != null && record.locked;
    }

    private static @Nullable CoachRecord recordAt(Level level, BlockPos pos, BlockState state) {
        BlockPos controller = state.getBlock() instanceof StagecoachPartBlock ? StagecoachStructure.controllerPos(pos, state) : pos;
        if (!(level.getBlockEntity(controller) instanceof StagecoachBlockEntity be) || level.getServer() == null) return null;
        CoachRecord record = StagecoachState.get(level.getServer()).coach(be.getCoachId());
        return record != null && record.pos.equals(controller) ? record : null;
    }
}
