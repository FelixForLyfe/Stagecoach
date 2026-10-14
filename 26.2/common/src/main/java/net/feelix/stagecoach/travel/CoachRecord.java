package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** The block entity carries only the id; everything else lives here. */
public final class CoachRecord {
    public final long id;
    public String name;
    public final BlockPos pos;
    public final ResourceKey<Level> dimension;
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

    public CoachRecord(long id, String name, BlockPos pos, ResourceKey<Level> dimension, Direction facing, @Nullable UUID owner,
                       @Nullable StagecoachWood wood, CreatedBy createdBy) {
        this.id = id;
        this.name = name;
        this.pos = pos.immutable();
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
        return new StopView(id, name, dimension, pos, facing, woodOrOak().getSerializedName(), Optional.ofNullable(owner), isPublic,
                hidden, locked, free, costMultiplier, Optional.ofNullable(scene), createdBy);
    }

    public CompoundTag toNbt() {
        CompoundTag n = new CompoundTag();
        n.putLong("id", id);
        n.putString("name", name);
        n.putLong("pos", pos.asLong());
        n.putString("dim", dimension.identifier().toString());
        n.putString("facing", facing.getSerializedName());
        if (owner != null) n.store("owner", UUIDUtil.CODEC, owner);
        if (wood != null) n.putString("wood", wood.getSerializedName());
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
    public static @Nullable CoachRecord fromNbt(CompoundTag n) {
        Identifier dim = Identifier.tryParse(n.getStringOr("dim", ""));
        Direction facing = Direction.byName(n.getStringOr("facing", ""));
        if (dim == null || facing == null || facing.getAxis().isVertical()) return null;
        CoachRecord c = new CoachRecord(n.getLongOr("id", 0), n.getStringOr("name", ""), BlockPos.of(n.getLongOr("pos", 0)),
                ResourceKey.create(Registries.DIMENSION, dim), facing, n.read("owner", UUIDUtil.CODEC).orElse(null),
                StagecoachWood.byId(n.getStringOr("wood", "")), createdBy(n.getStringOr("createdBy", "")));
        c.isPublic = n.getBooleanOr("public", false);
        c.hidden = n.getBooleanOr("hidden", false);
        c.locked = n.getBooleanOr("locked", false);
        c.free = n.getBooleanOr("free", false);
        if (n.contains("cost")) c.costMultiplier = Math.max(0, n.getDoubleOr("cost", 0));
        if (n.contains("scene")) c.scene = Identifier.tryParse(n.getStringOr("scene", ""));
        if (n.contains("structure")) c.structureKey = n.getStringOr("structure", "");
        return c;
    }

    private static CreatedBy createdBy(String name) {
        for (CreatedBy c : CreatedBy.values()) if (c.name().equals(name)) return c;
        return CreatedBy.PLAYER;
    }
}
