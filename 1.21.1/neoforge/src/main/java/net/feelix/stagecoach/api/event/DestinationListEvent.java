package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.Destination;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Before a player sees their destinations: add, remove, reorder or replace entries. Only OK entries can be travelled to. */
public class DestinationListEvent extends Event {
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
