package net.feelix.stagecoach.bukkit.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Journey {
    public enum Phase { TRAVELLING, ARRIVING }

    /** Random, so recovery can tell whether the player file saw this journey. */
    public final long id;
    public final UUID player;
    /** GameMode name before setting off ("SURVIVAL", ...). */
    public final String savedMode;
    public final boolean wasFlying;
    /** Creative, or another plugin's flight permission. */
    public final boolean allowFlight;
    public final int xpLevel;
    public final float xpProgress;
    public final int xpTotal;
    public final int xpPaid;
    /** Each in {@link Fare#encode} form; empty for an XP or free journey. */
    public final List<String> itemsPaid;

    public final long originId;
    public final String originName;
    public final UUID originWorld;
    public final int originX, originY, originZ, originTurns;

    public final long destId;
    public final String destName;
    public final UUID destWorld;
    public final int destX, destY, destZ, destTurns;

    /** Main-world game time when the journey began. */
    public final long startTime;
    public final int ticks;
    public Phase phase;

    public Journey(long id, UUID player, String savedMode, boolean wasFlying, boolean allowFlight, int xpLevel,
                   float xpProgress, int xpTotal, int xpPaid, List<String> itemsPaid, long originId, String originName, UUID originWorld, int originX,
                   int originY, int originZ, int originTurns, long destId, String destName, UUID destWorld, int destX,
                   int destY, int destZ, int destTurns, long startTime, int ticks, Phase phase) {
        this.id = id;
        this.player = player;
        this.savedMode = savedMode;
        this.wasFlying = wasFlying;
        this.allowFlight = allowFlight;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
        this.xpTotal = xpTotal;
        this.xpPaid = xpPaid;
        this.itemsPaid = List.copyOf(itemsPaid);
        this.originId = originId;
        this.originName = originName;
        this.originWorld = originWorld;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originTurns = originTurns;
        this.destId = destId;
        this.destName = destName;
        this.destWorld = destWorld;
        this.destX = destX;
        this.destY = destY;
        this.destZ = destZ;
        this.destTurns = destTurns;
        this.startTime = startTime;
        this.ticks = ticks;
        this.phase = phase;
    }

    public long endTime() {
        return startTime + ticks;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("player", player.toString());
        o.addProperty("savedMode", savedMode);
        o.addProperty("wasFlying", wasFlying);
        o.addProperty("allowFlight", allowFlight);
        o.addProperty("xpLevel", xpLevel);
        o.addProperty("xpProgress", xpProgress);
        o.addProperty("xpTotal", xpTotal);
        o.addProperty("xpPaid", xpPaid);
        JsonArray items = new JsonArray();
        for (String s : itemsPaid) items.add(s);
        o.add("items", items);
        o.addProperty("originId", originId);
        o.addProperty("originName", originName);
        o.addProperty("originWorld", originWorld.toString());
        o.addProperty("originX", originX);
        o.addProperty("originY", originY);
        o.addProperty("originZ", originZ);
        o.addProperty("originTurns", originTurns);
        o.addProperty("destId", destId);
        o.addProperty("destName", destName);
        o.addProperty("destWorld", destWorld.toString());
        o.addProperty("destX", destX);
        o.addProperty("destY", destY);
        o.addProperty("destZ", destZ);
        o.addProperty("destTurns", destTurns);
        o.addProperty("startTime", startTime);
        o.addProperty("ticks", ticks);
        o.addProperty("phase", phase.name());
        return o;
    }

    /** Null if anything is missing: a broken record is never half-applied. */
    public static Journey fromJson(JsonObject o) {
        try {
            List<String> items = new ArrayList<>();
            if (o.has("items")) for (JsonElement e : o.getAsJsonArray("items")) items.add(e.getAsString());
            return new Journey(o.has("id") ? o.get("id").getAsLong() : 0, UUID.fromString(o.get("player").getAsString()),
                    o.get("savedMode").getAsString(), o.get("wasFlying").getAsBoolean(), o.get("allowFlight").getAsBoolean(),
                    o.get("xpLevel").getAsInt(), o.get("xpProgress").getAsFloat(), o.get("xpTotal").getAsInt(),
                    o.get("xpPaid").getAsInt(), items,
                    o.get("originId").getAsLong(), o.get("originName").getAsString(),
                    UUID.fromString(o.get("originWorld").getAsString()), o.get("originX").getAsInt(),
                    o.get("originY").getAsInt(), o.get("originZ").getAsInt(), o.get("originTurns").getAsInt(),
                    o.get("destId").getAsLong(), o.get("destName").getAsString(),
                    UUID.fromString(o.get("destWorld").getAsString()), o.get("destX").getAsInt(),
                    o.get("destY").getAsInt(), o.get("destZ").getAsInt(), o.get("destTurns").getAsInt(),
                    o.get("startTime").getAsLong(), o.get("ticks").getAsInt(), Phase.valueOf(o.get("phase").getAsString()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Journey fromJsonString(String s) {
        try {
            return fromJson(JsonParser.parseString(s).getAsJsonObject());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
