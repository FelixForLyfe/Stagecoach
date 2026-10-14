package net.feelix.stagecoach.net;

import net.feelix.stagecoach.Stagecoach;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Packets {
    private Packets() {}

    public static final Identifier OPEN_NAMING = Stagecoach.id("open_naming");
    public static final Identifier OPEN_LIST = Stagecoach.id("open_list");
    public static final Identifier LIST_MORE = Stagecoach.id("list_more");
    public static final Identifier JOURNEY_START = Stagecoach.id("journey_start");
    public static final Identifier JOURNEY_END = Stagecoach.id("journey_end");
    public static final Identifier SERVER_SETTINGS = Stagecoach.id("server_settings");
    public static final Identifier STOP_EFFECT = Stagecoach.id("stop_effect");
    public static final Identifier KNOWN_STOPS = Stagecoach.id("known_stops");
    public static final Identifier SET_NAME = Stagecoach.id("set_name");
    public static final Identifier TRAVEL = Stagecoach.id("travel");
    public static final Identifier CANCEL = Stagecoach.id("cancel");
    public static final Identifier WANT_STOPS = Stagecoach.id("want_stops");

    /** 1.20.5+ loaders register every channel as a payload type up front (see RawPayload). */
    public static final List<Identifier> TO_CLIENT = List.of(OPEN_NAMING, OPEN_LIST, LIST_MORE, JOURNEY_START, JOURNEY_END,
            SERVER_SETTINGS, STOP_EFFECT, KNOWN_STOPS);
    public static final List<Identifier> TO_SERVER = List.of(SET_NAME, TRAVEL, CANCEL, WANT_STOPS);

    /** A 64 code point name is at most 128 UTF-16 chars. */
    private static final int MAX_STRING = 256;
    /** So a hostile buffer can't allocate without limit. */
    private static final int MAX_ENTRIES = 4096;
    public static final int MAX_STOPS = 1024;
    /** Destination lists go out in pages this size; the screen fills in as they arrive. */
    public static final int PAGE = 200;

    public static final byte STATUS_OK = 0;
    public static final byte STATUS_CANT_AFFORD = 1;
    public static final byte STATUS_OTHER_DIMENSION = 2;
    public static final byte STATUS_TOO_FAR = 3;
    public static final byte STATUS_LOCKED = 4;

    public static final byte END_ARRIVED = 0;
    public static final byte END_CANCELLED = 1;
    public static final byte END_INTERRUPTED = 2;

    public static final byte EFFECT_DISCOVER = 0;
    public static final byte EFFECT_PLACE = 1;

    public record OpenNaming(long coachId, String name, boolean rename, int maxLength) {
        public void write(PacketByteBuf buf) {
            buf.writeLong(coachId);
            buf.writeString(name, MAX_STRING);
            buf.writeBoolean(rename);
            buf.writeVarInt(maxLength);
        }

        public static OpenNaming read(PacketByteBuf buf) {
            return new OpenNaming(buf.readLong(), buf.readString(MAX_STRING), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** {@code fromList}: the server sends the list again afterwards. */
    public record SetName(long coachId, String name, boolean fromList) {
        public void write(PacketByteBuf buf) {
            buf.writeLong(coachId);
            buf.writeString(name, MAX_STRING);
            buf.writeBoolean(fromList);
        }

        /** Not trusted; the server runs it through CoachNames.clean. */
        public static SetName read(PacketByteBuf buf) {
            return new SetName(buf.readLong(), buf.readString(MAX_STRING), buf.readBoolean());
        }
    }

    /** {@code cost} is levels, or items when {@code itemFare} is set; {@code lockMessage} goes with STATUS_LOCKED. */
    public record Entry(long id, String name, BlockPos pos, Identifier dimension, int distance, int cost, int seconds,
                        boolean owned, byte status, @Nullable Text lockMessage) {
        void write(PacketByteBuf buf) {
            buf.writeLong(id);
            buf.writeString(name, MAX_STRING);
            buf.writeBlockPos(pos);
            buf.writeIdentifier(dimension);
            buf.writeVarInt(distance);
            buf.writeVarInt(cost);
            buf.writeVarInt(seconds);
            buf.writeBoolean(owned);
            buf.writeByte(status);
            buf.writeBoolean(lockMessage != null);
            if (lockMessage != null) buf.writeString(Text.Serialization.toJsonString(lockMessage, DynamicRegistryManager.EMPTY), MAX_STRING * 4);
        }

        static Entry read(PacketByteBuf buf) {
            long id = buf.readLong();
            String name = buf.readString(MAX_STRING);
            BlockPos pos = buf.readBlockPos();
            Identifier dimension = buf.readIdentifier();
            int distance = buf.readVarInt(), cost = buf.readVarInt(), seconds = buf.readVarInt();
            boolean owned = buf.readBoolean();
            byte status = buf.readByte();
            Text lock = buf.readBoolean() ? Text.Serialization.fromJson(buf.readString(MAX_STRING * 4), DynamicRegistryManager.EMPTY) : null;
            return new Entry(id, name, pos, dimension, distance, cost, seconds, owned, status, lock);
        }

        static List<Entry> readList(PacketByteBuf buf) {
            int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) entries.add(read(buf));
            return entries;
        }

        static void writeList(PacketByteBuf buf, List<Entry> entries) {
            buf.writeVarInt(entries.size());
            for (Entry e : entries) e.write(buf);
        }
    }

    /** {@code balance} is levels, or the fare items carried; {@code entries} may be only the first page of {@code total}. */
    public record DestinationList(long coachId, String name, boolean canRename, boolean free, boolean itemFare,
                                  Identifier fareItem, int balance, int maxDistance, int maxNameLength, int total, List<Entry> entries) {
        public void write(PacketByteBuf buf) {
            buf.writeLong(coachId);
            buf.writeString(name, MAX_STRING);
            buf.writeBoolean(canRename);
            buf.writeBoolean(free);
            buf.writeBoolean(itemFare);
            buf.writeIdentifier(fareItem);
            buf.writeVarInt(balance);
            buf.writeVarInt(maxDistance);
            buf.writeVarInt(maxNameLength);
            buf.writeVarInt(total);
            Entry.writeList(buf, entries);
        }

        public static DestinationList read(PacketByteBuf buf) {
            long id = buf.readLong();
            String name = buf.readString(MAX_STRING);
            boolean canRename = buf.readBoolean(), free = buf.readBoolean(), itemFare = buf.readBoolean();
            Identifier icon = buf.readIdentifier();
            int balance = buf.readVarInt(), maxDistance = buf.readVarInt(), maxNameLength = buf.readVarInt();
            int total = Math.min(buf.readVarInt(), MAX_ENTRIES);
            return new DestinationList(id, name, canRename, free, itemFare, icon, balance, maxDistance, maxNameLength, total,
                    Entry.readList(buf));
        }

        public DestinationList firstPage() {
            return new DestinationList(coachId, name, canRename, free, itemFare, fareItem, balance, maxDistance, maxNameLength, total,
                    entries.subList(0, Math.min(entries.size(), PAGE)));
        }
    }

    /** A later page of the list the player has open. */
    public record ListMore(long coachId, List<Entry> entries) {
        public void write(PacketByteBuf buf) {
            buf.writeLong(coachId);
            Entry.writeList(buf, entries);
        }

        public static ListMore read(PacketByteBuf buf) {
            return new ListMore(buf.readLong(), Entry.readList(buf));
        }
    }

    public record Travel(long originId, long destId) {
        public void write(PacketByteBuf buf) {
            buf.writeLong(originId);
            buf.writeLong(destId);
        }

        public static Travel read(PacketByteBuf buf) {
            return new Travel(buf.readLong(), buf.readLong());
        }
    }

    /** {@code stops} are the other coaches' x,z; the start is server game time; the scene fields let the client pick its scene. */
    public record JourneyStart(String originName, String destName, int originX, int originZ, int destX, int destZ,
                               int[] stops, long startTime, int durationTicks, long seed, int refundPercent, String stopScene,
                               String defaultScene, Identifier destBiome, Identifier destDimension) {
        public void write(PacketByteBuf buf) {
            buf.writeString(originName, MAX_STRING);
            buf.writeString(destName, MAX_STRING);
            buf.writeInt(originX);
            buf.writeInt(originZ);
            buf.writeInt(destX);
            buf.writeInt(destZ);
            buf.writeVarInt(stops.length / 2);
            for (int v : stops) buf.writeInt(v);
            buf.writeLong(startTime);
            buf.writeVarInt(durationTicks);
            buf.writeLong(seed);
            buf.writeVarInt(refundPercent);
            buf.writeString(stopScene, MAX_STRING);
            buf.writeString(defaultScene, MAX_STRING);
            buf.writeIdentifier(destBiome);
            buf.writeIdentifier(destDimension);
        }

        public static JourneyStart read(PacketByteBuf buf) {
            String origin = buf.readString(MAX_STRING), dest = buf.readString(MAX_STRING);
            int ox = buf.readInt(), oz = buf.readInt(), dx = buf.readInt(), dz = buf.readInt();
            int n = Math.min(buf.readVarInt(), MAX_STOPS);
            int[] stops = new int[n * 2];
            for (int i = 0; i < stops.length; i++) stops[i] = buf.readInt();
            return new JourneyStart(origin, dest, ox, oz, dx, dz, stops, buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                    buf.readString(MAX_STRING), buf.readString(MAX_STRING), buf.readIdentifier(), buf.readIdentifier());
        }
    }

    /** {@code travelStyle}: none, road, map or minimal (forceClientTravelStyle). */
    public record ServerSettings(String travelStyle) {
        public void write(PacketByteBuf buf) {
            buf.writeString(travelStyle, 32);
        }

        public static ServerSettings read(PacketByteBuf buf) {
            return new ServerSettings(buf.readString(32));
        }
    }

    /** The client spawns the particles itself, by its own particle settings. */
    public record StopEffect(byte kind, String name, BlockPos pos, Direction facing) {
        public void write(PacketByteBuf buf) {
            buf.writeByte(kind);
            buf.writeString(name, MAX_STRING);
            buf.writeBlockPos(pos);
            buf.writeByte(facing.getHorizontal());
        }

        public static StopEffect read(PacketByteBuf buf) {
            return new StopEffect(buf.readByte(), buf.readString(MAX_STRING), buf.readBlockPos(), Direction.fromHorizontal(buf.readByte()));
        }
    }

    public record KnownStop(long id, String name, Identifier dimension, BlockPos pos) {
    }

    /** Every stop this player knows, for map waypoints; sent again whenever that set changes. */
    public record KnownStops(List<KnownStop> stops) {
        public void write(PacketByteBuf buf) {
            buf.writeVarInt(stops.size());
            for (KnownStop s : stops) {
                buf.writeLong(s.id());
                buf.writeString(s.name(), MAX_STRING);
                buf.writeIdentifier(s.dimension());
                buf.writeBlockPos(s.pos());
            }
        }

        public static KnownStops read(PacketByteBuf buf) {
            int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<KnownStop> stops = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                stops.add(new KnownStop(buf.readLong(), buf.readString(MAX_STRING), buf.readIdentifier(), buf.readBlockPos()));
            }
            return new KnownStops(stops);
        }
    }
}
