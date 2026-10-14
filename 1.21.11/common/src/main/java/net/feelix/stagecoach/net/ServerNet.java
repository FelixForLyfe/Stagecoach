package net.feelix.stagecoach.net;

import io.netty.buffer.Unpooled;
import net.feelix.stagecoach.api.Destination;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.Journey;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerNet {
    private ServerNet() {}

    private static final Identifier PLAINS = Identifier.fromNamespaceAndPath("minecraft", "plains");
    /** Players this close to a coach being placed see its dust. */
    private static final double EFFECT_RANGE = 32;
    /** Waypoint lists go only to clients that asked (they run a map mod), and at most this often. */
    private static final int KNOWN_STOPS_INTERVAL = 40;
    private static final Set<UUID> WANT_STOPS = ConcurrentHashMap.newKeySet();
    private static boolean knownStopsDirty;
    private static int lastKnownStopsTick;

    public static void init() {
        // read buffers on the network thread (they're released after), do the work on the server thread
        Platform net = Platform.get();
        net.registerServerReceiver(Packets.SET_NAME, (server, player, buf) -> {
            Packets.SetName request = Packets.SetName.read(buf);
            server.execute(() -> StagecoachService.rename(player, request));
        });
        net.registerServerReceiver(Packets.TRAVEL, (server, player, buf) -> {
            Packets.Travel request = Packets.Travel.read(buf);
            server.execute(() -> StagecoachService.travel(player, request));
        });
        net.registerServerReceiver(Packets.CANCEL, (server, player, buf) -> server.execute(() -> JourneyManager.cancel(player)));
        net.registerServerReceiver(Packets.WANT_STOPS, (server, player, buf) -> server.execute(() -> {
            WANT_STOPS.add(player.getUUID());
            knownStops(player);
        }));
    }

    public static void onServerTick(MinecraftServer server) {
        if (!knownStopsDirty || server.getTickCount() - lastKnownStopsTick < KNOWN_STOPS_INTERVAL) return;
        knownStopsDirty = false;
        lastKnownStopsTick = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) knownStops(p);
    }

    public static void onLeft(ServerPlayer player) {
        WANT_STOPS.remove(player.getUUID());
    }

    public static void onServerStopped() {
        WANT_STOPS.clear();
        knownStopsDirty = false;
        lastKnownStopsTick = 0;
    }

    public static void openNaming(ServerPlayer player, CoachRecord record, boolean rename) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new Packets.OpenNaming(record.id, record.name, rename, StagecoachConfig.get().maxNameLength).write(buf);
        Platform.get().sendToPlayer(player, Packets.OPEN_NAMING, buf);
    }

    /** The first page opens the screen; the rest follow at once in pages of {@link Packets#PAGE}. */
    public static void openList(ServerPlayer player, CoachRecord here, List<Destination> destinations) {
        Packets.DestinationList list = StagecoachService.listPacket(player, here, destinations);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        list.firstPage().write(buf);
        Platform.get().sendToPlayer(player, Packets.OPEN_LIST, buf);
        for (int from = Packets.PAGE; from < list.entries().size(); from += Packets.PAGE) {
            FriendlyByteBuf more = new FriendlyByteBuf(Unpooled.buffer());
            new Packets.ListMore(here.id, list.entries().subList(from, Math.min(list.entries().size(), from + Packets.PAGE))).write(more);
            Platform.get().sendToPlayer(player, Packets.LIST_MORE, more);
        }
    }

    public static void journeyStart(ServerPlayer player, Journey journey, int[] stops, CoachRecord dest) {
        StagecoachConfig cfg = StagecoachConfig.get();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new Packets.JourneyStart(journey.originName, journey.destName, journey.originPos.getX(), journey.originPos.getZ(),
                journey.destPos.getX(), journey.destPos.getZ(), stops, journey.startTime, journey.durationTicks, journey.id,
                cfg.cancelRefundPercent, dest.scene == null ? "" : dest.scene.toString(), cfg.defaultScene,
                biomeAt(player.level().getServer(), dest), dest.dimension.identifier()).write(buf);
        Platform.get().sendToPlayer(player, Packets.JOURNEY_START, buf);
    }

    /** Never loads a chunk: an unloaded one answers with the biome the generator would place there. */
    private static Identifier biomeAt(MinecraftServer server, CoachRecord dest) {
        ServerLevel level = server.getLevel(dest.dimension);
        if (level == null) return PLAINS;
        return level.getBiome(dest.pos).unwrapKey().map(ResourceKey::identifier).orElse(PLAINS);
    }

    public static void journeyEnd(ServerPlayer player, byte reason) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(reason);
        Platform.get().sendToPlayer(player, Packets.JOURNEY_END, buf);
    }

    public static void serverSettings(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new Packets.ServerSettings(StagecoachConfig.get().forceClientTravelStyle).write(buf);
        Platform.get().sendToPlayer(player, Packets.SERVER_SETTINGS, buf);
    }

    public static void discoverEffect(ServerPlayer player, CoachRecord record) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new Packets.StopEffect(Packets.EFFECT_DISCOVER, record.name, record.pos, record.facing).write(buf);
        Platform.get().sendToPlayer(player, Packets.STOP_EFFECT, buf);
    }

    public static void placeEffect(ServerLevel level, CoachRecord record) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(record.pos.getX() + 0.5, record.pos.getY(), record.pos.getZ() + 0.5) > EFFECT_RANGE * EFFECT_RANGE) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new Packets.StopEffect(Packets.EFFECT_PLACE, record.name, record.pos, record.facing).write(buf);
            Platform.get().sendToPlayer(p, Packets.STOP_EFFECT, buf);
        }
    }

    /** For map waypoints: every stop the player knows that isn't hidden. */
    public static void knownStops(ServerPlayer player) {
        if (!WANT_STOPS.contains(player.getUUID())) return;
        StagecoachState network = StagecoachState.get(player.level().getServer());
        List<Packets.KnownStop> known = new ArrayList<>();
        for (CoachRecord c : network.coaches()) {
            if (c.hidden || !StagecoachService.knows(network, player, c)) continue;
            known.add(new Packets.KnownStop(c.id, c.name, c.dimension.identifier(), c.pos));
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new Packets.KnownStops(known).write(buf);
        Platform.get().sendToPlayer(player, Packets.KNOWN_STOPS, buf);
    }

    /** After a stop appears, goes, is renamed or changes visibility; the lists go out on a later tick, batched. */
    public static void knownStopsChanged() {
        knownStopsDirty = true;
    }
}
