package net.feelix.stagecoach.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;

/** Before a player discovers a stop; cancel to keep it unknown to them. */
public class StopDiscoverEvent {
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onStopDiscover(event);
    });

    @FunctionalInterface
    public interface Listener {
        void onStopDiscover(StopDiscoverEvent event);
    }

    private final ServerPlayerEntity player;
    private final StopView stop;
    private boolean canceled;

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

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
