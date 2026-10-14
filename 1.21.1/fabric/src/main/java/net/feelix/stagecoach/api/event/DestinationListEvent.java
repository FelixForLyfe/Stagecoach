package net.feelix.stagecoach.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.feelix.stagecoach.api.Destination;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Before a player sees their destinations: add, remove, reorder or replace entries. Only OK entries can be travelled to. */
public class DestinationListEvent {
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onDestinationList(event);
    });

    @FunctionalInterface
    public interface Listener {
        void onDestinationList(DestinationListEvent event);
    }

    private final ServerPlayerEntity player;
    private final @Nullable StopView here;
    private final List<Destination> destinations;

    public DestinationListEvent(ServerPlayerEntity player, @Nullable StopView here, List<Destination> destinations) {
        this.player = player;
        this.here = here;
        this.destinations = destinations;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public @Nullable StopView getHere() {
        return here;
    }

    public List<Destination> getDestinations() {
        return destinations;
    }
}
