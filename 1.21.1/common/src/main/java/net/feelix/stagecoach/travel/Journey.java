package net.feelix.stagecoach.travel;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Journey {
    /** Undoing TRAVELLING means back to the origin with a refund; ARRIVING means finishing the trip. */
    public enum Phase { TRAVELLING, ARRIVING }

    /** Random, so the state record can be matched with the copy in the player's file. */
    public final long id;
    public final UUID player;
    public final GameMode savedMode;
    public final boolean wasFlying;
    /** XP exactly as it was before paying: restoring it is the refund, and restoring twice is harmless. */
    public final int xpLevel;
    public final float xpProgress;
    public final int xpTotal;
    public final int xpPaid;
    public final List<ItemStack> itemsPaid;
    public final long originId;
    public final String originName;
    public final RegistryKey<World> originDim;
    public final BlockPos originPos;
    public final Direction originFacing;
    public final long destId;
    public final String destName;
    public final RegistryKey<World> destDim;
    public final BlockPos destPos;
    public final Direction destFacing;
    public final long startTime;
    public final int durationTicks;
    public final int distance;
    /** Just past the origin coach's shaft (2.8 blocks out), or where the player stood when sent without a coach. */
    public final Vec3d holdPoint;
    public Phase phase;

    public Journey(long id, UUID player, GameMode savedMode, boolean wasFlying, int xpLevel, float xpProgress, int xpTotal,
                   int xpPaid, List<ItemStack> itemsPaid, Origin origin, CoachRecord dest, long startTime, int durationTicks,
                   int distance) {
        this(id, player, savedMode, wasFlying, xpLevel, xpProgress, xpTotal, xpPaid, itemsPaid, origin.id(), origin.name(),
                origin.dimension(), origin.pos(), origin.facing(), origin.hold(), dest.id, dest.name, dest.dimension, dest.pos,
                dest.facing, startTime, durationTicks, distance, Phase.TRAVELLING);
    }

    /** Where a journey leaves from: a coach, or (id -1) the spot a player was sent from. */
    public record Origin(long id, String name, RegistryKey<World> dimension, BlockPos pos, Direction facing, Vec3d hold) {
        public static Origin of(CoachRecord coach) {
            return new Origin(coach.id, coach.name, coach.dimension, coach.pos, coach.facing, holdPoint(coach.pos, coach.facing));
        }

        public static Origin here(RegistryKey<World> dimension, Vec3d pos, Direction facing) {
            return new Origin(-1, "", dimension, BlockPos.ofFloored(pos), facing, pos);
        }

        public boolean isCoach() {
            return id >= 0;
        }
    }

    public static Vec3d holdPoint(BlockPos coach, Direction facing) {
        return Vec3d.ofBottomCenter(coach.offset(facing, 4)).add(0, 1, 0);
    }

    public boolean fromCoach() {
        return originId >= 0;
    }

    private Journey(long id, UUID player, GameMode savedMode, boolean wasFlying, int xpLevel, float xpProgress, int xpTotal,
                    int xpPaid, List<ItemStack> itemsPaid, long originId, String originName, RegistryKey<World> originDim,
                    BlockPos originPos, Direction originFacing, Vec3d holdPoint, long destId, String destName,
                    RegistryKey<World> destDim, BlockPos destPos, Direction destFacing, long startTime, int durationTicks,
                    int distance, Phase phase) {
        this.id = id;
        this.player = player;
        this.savedMode = savedMode;
        this.wasFlying = wasFlying;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
        this.xpTotal = xpTotal;
        this.xpPaid = xpPaid;
        this.itemsPaid = List.copyOf(itemsPaid);
        this.originId = originId;
        this.originName = originName;
        this.originDim = originDim;
        this.originPos = originPos;
        this.originFacing = originFacing;
        this.destId = destId;
        this.destName = destName;
        this.destDim = destDim;
        this.destPos = destPos;
        this.destFacing = destFacing;
        this.startTime = startTime;
        this.durationTicks = durationTicks;
        this.distance = distance;
        this.holdPoint = holdPoint;
        this.phase = phase;
    }

    public long endTime() {
        return startTime + durationTicks;
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound n = new NbtCompound();
        n.putLong("seed", id);
        n.putUuid("player", player);
        n.putString("mode", savedMode.getName());
        n.putBoolean("flying", wasFlying);
        n.putInt("xpLevel", xpLevel);
        n.putFloat("xpProgress", xpProgress);
        n.putInt("xpTotal", xpTotal);
        n.putInt("xpPaid", xpPaid);
        NbtList items = new NbtList();
        for (ItemStack stack : itemsPaid) items.add(stack.encode(registries));
        n.put("items", items);
        n.putLong("originId", originId);
        n.putString("originName", originName);
        n.putString("originDim", originDim.getValue().toString());
        n.putLong("originPos", originPos.asLong());
        n.putString("originFacing", originFacing.asString());
        n.putLong("destId", destId);
        n.putString("destName", destName);
        n.putString("destDim", destDim.getValue().toString());
        n.putLong("destPos", destPos.asLong());
        n.putString("destFacing", destFacing.asString());
        n.putLong("start", startTime);
        n.putInt("duration", durationTicks);
        n.putInt("distance", distance);
        if (!fromCoach()) {
            n.putDouble("holdX", holdPoint.x);
            n.putDouble("holdY", holdPoint.y);
            n.putDouble("holdZ", holdPoint.z);
        }
        n.putString("phase", phase.name());
        return n;
    }

    /** Null if unreadable: a leftover journey must never break a join. */
    public static @Nullable Journey fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
        try {
            GameMode mode = GameMode.byName(n.getString("mode"), null);
            Identifier od = Identifier.tryParse(n.getString("originDim")), dd = Identifier.tryParse(n.getString("destDim"));
            Direction of = Direction.byName(n.getString("originFacing")), df = Direction.byName(n.getString("destFacing"));
            if (!n.containsUuid("player") || mode == null || od == null || dd == null || of == null || df == null) return null;
            List<ItemStack> items = new ArrayList<>();
            for (NbtElement e : n.getList("items", NbtElement.COMPOUND_TYPE)) ItemStack.fromNbt(registries, e).ifPresent(items::add);
            Phase phase = "ARRIVING".equals(n.getString("phase")) ? Phase.ARRIVING : Phase.TRAVELLING;
            long originId = n.getLong("originId");
            BlockPos originPos = BlockPos.fromLong(n.getLong("originPos"));
            Vec3d hold = originId >= 0 ? holdPoint(originPos, of) : new Vec3d(n.getDouble("holdX"), n.getDouble("holdY"), n.getDouble("holdZ"));
            return new Journey(n.getLong("seed"), n.getUuid("player"), mode, n.getBoolean("flying"), n.getInt("xpLevel"),
                    n.getFloat("xpProgress"), n.getInt("xpTotal"), n.getInt("xpPaid"), items, originId,
                    n.getString("originName"), RegistryKey.of(RegistryKeys.WORLD, od), originPos, of, hold,
                    n.getLong("destId"), n.getString("destName"), RegistryKey.of(RegistryKeys.WORLD, dd),
                    BlockPos.fromLong(n.getLong("destPos")), df, n.getLong("start"), n.getInt("duration"), n.getInt("distance"), phase);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
