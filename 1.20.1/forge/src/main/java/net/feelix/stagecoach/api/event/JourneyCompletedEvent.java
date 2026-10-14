package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** After the player was set down at the destination. */
public class JourneyCompletedEvent extends Event {
    private final ServerPlayerEntity player;
    private final @Nullable StopView from;
    private final StopView to;
    private final int distance;

    public JourneyCompletedEvent(ServerPlayerEntity player, @Nullable StopView from, StopView to, int distance) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.distance = distance;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public @Nullable StopView getFrom() {
        return from;
    }

    public StopView getTo() {
        return to;
    }

    public int getDistance() {
        return distance;
    }
}
