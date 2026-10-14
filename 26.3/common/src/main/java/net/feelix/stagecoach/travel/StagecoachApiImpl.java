package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.api.JourneyOptions;
import net.feelix.stagecoach.api.JourneyResult;
import net.feelix.stagecoach.api.StagecoachAPI;
import net.feelix.stagecoach.api.StopView;
import net.feelix.stagecoach.net.ServerNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class StagecoachApiImpl implements StagecoachAPI {
    public static final StagecoachApiImpl INSTANCE = new StagecoachApiImpl();

    private StagecoachApiImpl() {}

    @Override
    public Collection<StopView> getStops(MinecraftServer server) {
        return views(StagecoachState.get(server).coaches());
    }

    @Override
    public Collection<StopView> getStops(ServerLevel level) {
        return views(StagecoachState.get(level.getServer()).coaches(level.dimension()));
    }

    private static List<StopView> views(Collection<CoachRecord> records) {
        List<StopView> out = new ArrayList<>(records.size());
        for (CoachRecord r : records) out.add(r.view());
        return out;
    }

    @Override
    public Optional<StopView> getStop(MinecraftServer server, long id) {
        CoachRecord r = StagecoachState.get(server).coach(id);
        return r == null ? Optional.empty() : Optional.of(r.view());
    }

    @Override
    public Optional<StopView> findStopByName(MinecraftServer server, String name) {
        List<CoachRecord> found = StagecoachState.get(server).named(name);
        return found.size() == 1 ? Optional.of(found.get(0).view()) : Optional.empty();
    }

    @Override
    public boolean isDiscovered(ServerPlayer player, StopView stop) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
        CoachRecord r = network.coach(stop.id());
        return r != null && StagecoachService.knows(network, player, r);
    }

    @Override
    public void discover(ServerPlayer player, StopView stop) {
        StagecoachState network = StagecoachState.get(player.level().getServer());
        if (network.coach(stop.id()) != null && network.discover(player.getUUID(), stop.id())) ServerNet.knownStops(player);
    }

    @Override
    public void forget(ServerPlayer player, StopView stop) {
        if (StagecoachState.get(player.level().getServer()).forget(player.getUUID(), stop.id())) ServerNet.knownStops(player);
    }

    @Override
    public boolean isTravelling(ServerPlayer player) {
        return JourneyManager.isTravelling(player.getUUID());
    }

    @Override
    public JourneyResult startJourney(ServerPlayer player, StopView destination, JourneyOptions options) {
        CoachRecord dest = StagecoachState.get(player.level().getServer()).coach(destination.id());
        if (dest == null) return JourneyResult.UNAVAILABLE;
        return StagecoachService.startJourney(player, dest, options).result();
    }

    @Override
    public void cancelJourney(ServerPlayer player) {
        JourneyManager.cancelByAdmin(player);
    }
}
