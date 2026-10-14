package net.feelix.stagecoach.api;

import net.feelix.stagecoach.travel.StagecoachApiImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;

/** Server thread only. */
public interface StagecoachAPI {
    int API_VERSION = 1;

    static StagecoachAPI get() {
        return StagecoachApiImpl.INSTANCE;
    }

    Collection<StopView> getStops(MinecraftServer server);

    Collection<StopView> getStops(ServerLevel level);

    Optional<StopView> getStop(MinecraftServer server, long id);

    /** Case-insensitive; empty when no stop or more than one stop has that name. */
    Optional<StopView> findStopByName(MinecraftServer server, String name);

    boolean isDiscovered(ServerPlayer player, StopView stop);

    void discover(ServerPlayer player, StopView stop);

    void forget(ServerPlayer player, StopView stop);

    boolean isTravelling(ServerPlayer player);

    JourneyResult startJourney(ServerPlayer player, StopView destination, JourneyOptions options);

    void cancelJourney(ServerPlayer player);
}
