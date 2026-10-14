package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Before a player discovers a stop; cancel to keep it unknown to them. */
@Cancelable
public class StopDiscoverEvent extends Event {
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
