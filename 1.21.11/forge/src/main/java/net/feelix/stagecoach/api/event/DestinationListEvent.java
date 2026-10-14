package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.Destination;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Before a player sees their destinations: add, remove, reorder or replace entries. Only OK entries can be travelled to. */
public class DestinationListEvent extends MutableEvent {
    public static final EventBus<DestinationListEvent> BUS = EventBus.create(DestinationListEvent.class);

    private final ServerPlayer player;
    private final @Nullable StopView here;
    private final List<Destination> destinations;

    public DestinationListEvent(ServerPlayer player, @Nullable StopView here, List<Destination> destinations) {
        this.player = player;
        this.here = here;
        this.destinations = destinations;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public @Nullable StopView getHere() {
        return here;
    }

    public List<Destination> getDestinations() {
        return destinations;
    }
}
