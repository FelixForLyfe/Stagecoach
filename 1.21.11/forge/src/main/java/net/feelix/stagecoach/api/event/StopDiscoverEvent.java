package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;

/** Before a player discovers a stop; cancel to keep it unknown to them. */
public class StopDiscoverEvent extends MutableEvent implements Cancellable {
    public static final CancellableEventBus<StopDiscoverEvent> BUS = CancellableEventBus.create(StopDiscoverEvent.class);

    private final ServerPlayer player;
    private final StopView stop;
    private boolean canceled;

    public StopDiscoverEvent(ServerPlayer player, StopView stop) {
        this.player = player;
        this.stop = stop;
    }

    public ServerPlayer getPlayer() {
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
