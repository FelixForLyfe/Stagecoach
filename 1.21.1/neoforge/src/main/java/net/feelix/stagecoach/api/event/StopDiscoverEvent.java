package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Before a player discovers a stop; cancel to keep it unknown to them. */
public class StopDiscoverEvent extends Event implements ICancellableEvent {
    private final ServerPlayerEntity player;
    private final StopView stop;

    public StopDiscoverEvent(ServerPlayerEntity player, StopView stop) {
        this.player = player;
        this.stop = stop;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public StopView getStop() {
        return stop;
    }
}
