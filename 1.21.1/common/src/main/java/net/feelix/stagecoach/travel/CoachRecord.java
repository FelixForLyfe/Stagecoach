package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** The block entity carries only the id; everything else lives here. */
public final class CoachRecord {
    public final long id;
    public String name;
    public final BlockPos pos;
    public final RegistryKey<World> dimension;
    public final Direction facing;
    /** Null is an ownerless stop: only operators may break, rename or edit it. */
    public @Nullable UUID owner;
    /** Null for a stop from an older save until its chunk loads again. */
    public @Nullable StagecoachWood wood;
    public boolean isPublic;
    public boolean hidden;
    public boolean locked;
    public boolean free;
    public double costMultiplier = 1.0;
    public @Nullable Identifier scene;
    public CreatedBy createdBy;
    /** Structure id and start chunk of the settlement a generated stop belongs to. */
    public @Nullable String structureKey;

    public CoachRecord(long id, String name, BlockPos pos, RegistryKey<World> dimension, Direction facing, @Nullable UUID owner,
                       @Nullable StagecoachWood wood, CreatedBy createdBy) {
        this.id = id;
        this.name = name;
        this.pos = pos.toImmutable();
        this.dimension = dimension;
        this.facing = facing;
        this.owner = owner;
        this.wood = wood;
        this.createdBy = createdBy;
    }

    public boolean isOwner(UUID player) {
        return player.equals(owner);
    }

    public StagecoachWood woodOrOak() {
        return wood == null ? StagecoachWood.OAK : wood;
    }

    public StopView view() {
        return new StopView(id, name, dimension, pos, facing, woodOrOak().asString(), Optional.ofNullable(owner), isPublic,
                hidden, locked, free, costMultiplier, Optional.ofNullable(scene), createdBy);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putLong("id", id);
        n.putString("name", name);
        n.putLong("pos", pos.asLong());
        n.putString("dim", dimension.getValue().toString());
        n.putString("facing", facing.asString());
        if (owner != null) n.putUuid("owner", owner);
        if (wood != null) n.putString("wood", wood.asString());
        if (isPublic) n.putBoolean("public", true);
        if (hidden) n.putBoolean("hidden", true);
        if (locked) n.putBoolean("locked", true);
        if (free) n.putBoolean("free", true);
        if (costMultiplier != 1.0) n.putDouble("cost", costMultiplier);
        if (scene != null) n.putString("scene", scene.toString());
        n.putString("createdBy", createdBy.name());
        if (structureKey != null) n.putString("structure", structureKey);
        return n;
    }

    /** Older records lack fields; each takes its default. */
    public static @Nullable CoachRecord fromNbt(NbtCompound n) {
        Identifier dim = Identifier.tryParse(n.getString("dim"));
        Direction facing = Direction.byName(n.getString("facing"));
        if (dim == null || facing == null || facing.getAxis().isVertical()) return null;
        CoachRecord c = new CoachRecord(n.getLong("id"), n.getString("name"), BlockPos.fromLong(n.getLong("pos")),
                RegistryKey.of(RegistryKeys.WORLD, dim), facing, n.containsUuid("owner") ? n.getUuid("owner") : null,
                StagecoachWood.byId(n.getString("wood")), createdBy(n.getString("createdBy")));
        c.isPublic = n.getBoolean("public");
        c.hidden = n.getBoolean("hidden");
        c.locked = n.getBoolean("locked");
        c.free = n.getBoolean("free");
        if (n.contains("cost")) c.costMultiplier = Math.max(0, n.getDouble("cost"));
        if (n.contains("scene")) c.scene = Identifier.tryParse(n.getString("scene"));
        if (n.contains("structure")) c.structureKey = n.getString("structure");
        return c;
    }

    private static CreatedBy createdBy(String name) {
        for (CreatedBy c : CreatedBy.values()) if (c.name().equals(name)) return c;
        return CreatedBy.PLAYER;
    }
}
