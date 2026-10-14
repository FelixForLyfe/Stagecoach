package net.feelix.stagecoach.travel;

import net.feelix.stagecoach.api.JourneyOptions;
import net.feelix.stagecoach.api.JourneyResult;
import net.feelix.stagecoach.api.StagecoachAPI;
import net.feelix.stagecoach.api.StopView;
import net.feelix.stagecoach.net.ServerNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
    public Collection<StopView> getStops(ServerWorld world) {
        return views(StagecoachState.get(world.getServer()).coaches(world.getRegistryKey()));
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
    public boolean isDiscovered(ServerPlayerEntity player, StopView stop) {
        StagecoachState network = StagecoachState.get(player.getServer());
        CoachRecord r = network.coach(stop.id());
        return r != null && StagecoachService.knows(network, player, r);
    }

    @Override
    public void discover(ServerPlayerEntity player, StopView stop) {
        StagecoachState network = StagecoachState.get(player.getServer());
        if (network.coach(stop.id()) != null && network.discover(player.getUuid(), stop.id())) ServerNet.knownStops(player);
    }

    @Override
    public void forget(ServerPlayerEntity player, StopView stop) {
        if (StagecoachState.get(player.getServer()).forget(player.getUuid(), stop.id())) ServerNet.knownStops(player);
    }

    @Override
    public boolean isTravelling(ServerPlayerEntity player) {
        return JourneyManager.isTravelling(player.getUuid());
    }

    @Override
    public JourneyResult startJourney(ServerPlayerEntity player, StopView destination, JourneyOptions options) {
        CoachRecord dest = StagecoachState.get(player.getServer()).coach(destination.id());
        if (dest == null) return JourneyResult.UNAVAILABLE;
        return StagecoachService.startJourney(player, dest, options).result();
    }

    @Override
    public void cancelJourney(ServerPlayerEntity player) {
        JourneyManager.cancelByAdmin(player);
    }
}
