package net.feelix.stagecoach.advancement;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.data.TriggerConditions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public final class StagecoachTriggers {
    private StagecoachTriggers() {}

    public static final StagecoachCriterion COACH_PLACED = new StagecoachCriterion("coach_placed");
    public static final StagecoachCriterion STOP_DISCOVERED = new StagecoachCriterion("stop_discovered");
    public static final StagecoachCriterion JOURNEY_STARTED = new StagecoachCriterion("journey_started");
    public static final StagecoachCriterion JOURNEY_COMPLETED = new StagecoachCriterion("journey_completed");
    public static final StagecoachCriterion JOURNEY_CANCELLED = new StagecoachCriterion("journey_cancelled");

    public static final Identifier JOURNEYS_COMPLETED = Stagecoach.id("journeys_completed");
    public static final Identifier BLOCKS_TRAVELLED = Stagecoach.id("blocks_travelled");
    public static final Identifier STOPS_DISCOVERED = Stagecoach.id("stops_discovered");
    public static final Identifier COACHES_PLACED = Stagecoach.id("coaches_placed");
    public static final Identifier[] STATS = {JOURNEYS_COMPLETED, BLOCKS_TRAVELLED, STOPS_DISCOVERED, COACHES_PLACED};

    public static final StagecoachCriterion[] CRITERIA = {COACH_PLACED, STOP_DISCOVERED, JOURNEY_STARTED, JOURNEY_COMPLETED,
            JOURNEY_CANCELLED};

    public static void coachPlaced(ServerPlayerEntity player, String wood, String dimension) {
        player.increaseStat(COACHES_PLACED, 1);
        COACH_PLACED.trigger(player, new TriggerConditions.Fired(wood, dimension, "", -1, 0, "", "", 0, 0, false, 0));
    }

    public static void stopDiscovered(ServerPlayerEntity player, String name, long id, int total, String dimension) {
        player.increaseStat(STOPS_DISCOVERED, 1);
        STOP_DISCOVERED.trigger(player, new TriggerConditions.Fired("", dimension, name, id, total, "", "", 0, 0, false, 0));
    }

    public static void journeyStarted(ServerPlayerEntity player, String from, String to, int distance, int cost, boolean crossDimension) {
        JOURNEY_STARTED.trigger(player, new TriggerConditions.Fired("", "", "", -1, 0, from, to, distance, cost, crossDimension, 0));
    }

    public static void journeyCompleted(ServerPlayerEntity player, String from, String to, int distance, boolean crossDimension) {
        player.increaseStat(JOURNEYS_COMPLETED, 1);
        player.increaseStat(BLOCKS_TRAVELLED, distance);
        int total = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(JOURNEYS_COMPLETED));
        JOURNEY_COMPLETED.trigger(player, new TriggerConditions.Fired("", "", "", -1, 0, from, to, distance, 0, crossDimension, total));
    }

    public static void journeyCancelled(ServerPlayerEntity player, String to) {
        JOURNEY_CANCELLED.trigger(player, new TriggerConditions.Fired("", "", "", -1, 0, "", to, 0, 0, false, 0));
    }
}
