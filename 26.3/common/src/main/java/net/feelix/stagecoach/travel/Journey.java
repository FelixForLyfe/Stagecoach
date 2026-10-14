package net.feelix.stagecoach.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    public final GameType savedMode;
    public final boolean wasFlying;
    /** XP exactly as it was before paying: restoring it is the refund, and restoring twice is harmless. */
    public final int xpLevel;
    public final float xpProgress;
    public final int xpTotal;
    public final int xpPaid;
    /** Already encoded: decoding needs the player's registries. */
    public final List<CompoundTag> itemsPaid;
    public final long originId;
    public final String originName;
    public final ResourceKey<Level> originDim;
    public final BlockPos originPos;
    public final Direction originFacing;
    public final long destId;
    public final String destName;
    public final ResourceKey<Level> destDim;
    public final BlockPos destPos;
    public final Direction destFacing;
    public final long startTime;
    public final int durationTicks;
    public final int distance;
    /** Just past the origin coach's shaft (2.8 blocks out), or where the player stood when sent without a coach. */
    public final Vec3 holdPoint;
    public Phase phase;

    public Journey(long id, UUID player, GameType savedMode, boolean wasFlying, int xpLevel, float xpProgress, int xpTotal,
                   int xpPaid, List<CompoundTag> itemsPaid, Origin origin, CoachRecord dest, long startTime, int durationTicks,
                   int distance) {
        this(id, player, savedMode, wasFlying, xpLevel, xpProgress, xpTotal, xpPaid, itemsPaid, origin.id(), origin.name(),
                origin.dimension(), origin.pos(), origin.facing(), origin.hold(), dest.id, dest.name, dest.dimension, dest.pos,
                dest.facing, startTime, durationTicks, distance, Phase.TRAVELLING);
    }

    /** Where a journey leaves from: a coach, or (id -1) the spot a player was sent from. */
    public record Origin(long id, String name, ResourceKey<Level> dimension, BlockPos pos, Direction facing, Vec3 hold) {
        public static Origin of(CoachRecord coach) {
            return new Origin(coach.id, coach.name, coach.dimension, coach.pos, coach.facing, holdPoint(coach.pos, coach.facing));
        }

        public static Origin here(ResourceKey<Level> dimension, Vec3 pos, Direction facing) {
            return new Origin(-1, "", dimension, BlockPos.containing(pos), facing, pos);
        }

        public boolean isCoach() {
            return id >= 0;
        }
    }

    public static Vec3 holdPoint(BlockPos coach, Direction facing) {
        return Vec3.atBottomCenterOf(coach.relative(facing, 4)).add(0, 1, 0);
    }

    public boolean fromCoach() {
        return originId >= 0;
    }

    private Journey(long id, UUID player, GameType savedMode, boolean wasFlying, int xpLevel, float xpProgress, int xpTotal,
                    int xpPaid, List<CompoundTag> itemsPaid, long originId, String originName, ResourceKey<Level> originDim,
                    BlockPos originPos, Direction originFacing, Vec3 holdPoint, long destId, String destName,
                    ResourceKey<Level> destDim, BlockPos destPos, Direction destFacing, long startTime, int durationTicks,
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

    public CompoundTag toNbt() {
        CompoundTag n = new CompoundTag();
        n.putLong("seed", id);
        n.store("player", UUIDUtil.CODEC, player);
        n.putString("mode", savedMode.getName());
        n.putBoolean("flying", wasFlying);
        n.putInt("xpLevel", xpLevel);
        n.putFloat("xpProgress", xpProgress);
        n.putInt("xpTotal", xpTotal);
        n.putInt("xpPaid", xpPaid);
        ListTag items = new ListTag();
        for (CompoundTag stack : itemsPaid) items.add(stack.copy());
        n.put("items", items);
        n.putLong("originId", originId);
        n.putString("originName", originName);
        n.putString("originDim", originDim.identifier().toString());
        n.putLong("originPos", originPos.asLong());
        n.putString("originFacing", originFacing.getSerializedName());
        n.putLong("destId", destId);
        n.putString("destName", destName);
        n.putString("destDim", destDim.identifier().toString());
        n.putLong("destPos", destPos.asLong());
        n.putString("destFacing", destFacing.getSerializedName());
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
    public static @Nullable Journey fromNbt(CompoundTag n) {
        try {
            GameType mode = GameType.byName(n.getStringOr("mode", ""), null);
            Identifier od = Identifier.tryParse(n.getStringOr("originDim", "")), dd = Identifier.tryParse(n.getStringOr("destDim", ""));
            Direction of = Direction.byName(n.getStringOr("originFacing", "")), df = Direction.byName(n.getStringOr("destFacing", ""));
            UUID player = n.read("player", UUIDUtil.CODEC).orElse(null);
            if (player == null || mode == null || od == null || dd == null || of == null || df == null) return null;
            List<CompoundTag> items = new ArrayList<>();
            for (CompoundTag stack : n.getListOrEmpty("items").compoundStream().toList()) items.add(stack);
            Phase phase = "ARRIVING".equals(n.getStringOr("phase", "")) ? Phase.ARRIVING : Phase.TRAVELLING;
            long originId = n.getLongOr("originId", 0);
            BlockPos originPos = BlockPos.of(n.getLongOr("originPos", 0));
            Vec3 hold = originId >= 0 ? holdPoint(originPos, of)
                    : new Vec3(n.getDoubleOr("holdX", 0), n.getDoubleOr("holdY", 0), n.getDoubleOr("holdZ", 0));
            return new Journey(n.getLongOr("seed", 0), player, mode, n.getBooleanOr("flying", false), n.getIntOr("xpLevel", 0),
                    n.getFloatOr("xpProgress", 0), n.getIntOr("xpTotal", 0), n.getIntOr("xpPaid", 0), items, originId,
                    n.getStringOr("originName", ""), ResourceKey.create(Registries.DIMENSION, od), originPos, of, hold,
                    n.getLongOr("destId", 0), n.getStringOr("destName", ""), ResourceKey.create(Registries.DIMENSION, dd),
                    BlockPos.of(n.getLongOr("destPos", 0)), df, n.getLongOr("start", 0), n.getIntOr("duration", 0),
                    n.getIntOr("distance", 0), phase);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
