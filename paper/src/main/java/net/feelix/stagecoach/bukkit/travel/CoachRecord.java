package net.feelix.stagecoach.bukkit.travel;

import com.google.gson.JsonObject;

import java.util.UUID;

public final class CoachRecord {
    public final long id;
    public String name;
    public final UUID world;
    /** Shown when that world isn't loaded. */
    public String worldName;
    public final int x, y, z;
    public final int turns;
    public final String wood;
    /** Null for a hand-edited record: only admins may rename or break it. */
    public final UUID owner;

    public CoachRecord(long id, String name, UUID world, String worldName, int x, int y, int z, int turns, String wood,
                       UUID owner) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.turns = Math.floorMod(turns, 4);
        this.wood = wood;
        this.owner = owner;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("world", world.toString());
        o.addProperty("worldName", worldName);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("turns", turns);
        o.addProperty("wood", wood);
        if (owner != null) o.addProperty("owner", owner.toString());
        return o;
    }

    /** Null if a field is missing or malformed. */
    static CoachRecord fromJson(JsonObject o) {
        try {
            UUID owner = o.has("owner") ? UUID.fromString(o.get("owner").getAsString()) : null;
            return new CoachRecord(o.get("id").getAsLong(), o.get("name").getAsString(),
                    UUID.fromString(o.get("world").getAsString()),
                    o.has("worldName") ? o.get("worldName").getAsString() : "?",
                    o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("turns").getAsInt(),
                    o.get("wood").getAsString(), owner);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
