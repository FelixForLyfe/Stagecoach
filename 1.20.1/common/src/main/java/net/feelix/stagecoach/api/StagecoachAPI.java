package net.feelix.stagecoach.api;

import net.feelix.stagecoach.travel.StagecoachApiImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Collection;
import java.util.Optional;

/** Server thread only. */
public interface StagecoachAPI {
    int API_VERSION = 1;

    static StagecoachAPI get() {
        return StagecoachApiImpl.INSTANCE;
    }

    Collection<StopView> getStops(MinecraftServer server);

    Collection<StopView> getStops(ServerWorld world);

    Optional<StopView> getStop(MinecraftServer server, long id);

    /** Case-insensitive; empty when no stop or more than one stop has that name. */
    Optional<StopView> findStopByName(MinecraftServer server, String name);

    boolean isDiscovered(ServerPlayerEntity player, StopView stop);

    void discover(ServerPlayerEntity player, StopView stop);

    void forget(ServerPlayerEntity player, StopView stop);

    boolean isTravelling(ServerPlayerEntity player);

    JourneyResult startJourney(ServerPlayerEntity player, StopView destination, JourneyOptions options);

    void cancelJourney(ServerPlayerEntity player);
}
