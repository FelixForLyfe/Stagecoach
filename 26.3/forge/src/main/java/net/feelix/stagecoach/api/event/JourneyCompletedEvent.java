package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import org.jetbrains.annotations.Nullable;

/** After the player was set down at the destination. */
public class JourneyCompletedEvent extends MutableEvent {
    public static final EventBus<JourneyCompletedEvent> BUS = EventBus.create(JourneyCompletedEvent.class);

    private final ServerPlayer player;
    private final @Nullable StopView from;
    private final StopView to;
    private final int distance;

    public JourneyCompletedEvent(ServerPlayer player, @Nullable StopView from, StopView to, int distance) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.distance = distance;
    }

    public ServerPlayer getPlayer() {
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
