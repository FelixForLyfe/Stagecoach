package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** After the fare was taken and the journey saved; {@code from} is null for a journey sent from anywhere. */
public class JourneyStartedEvent extends Event {
    private final ServerPlayerEntity player;
    private final @Nullable StopView from;
    private final StopView to;
    private final int cost;
    private final boolean itemFare;
    private final int travelSeconds;
    private final boolean instant;

    public JourneyStartedEvent(ServerPlayerEntity player, @Nullable StopView from, StopView to, int cost,
            boolean itemFare, int travelSeconds, boolean instant) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.cost = cost;
        this.itemFare = itemFare;
        this.travelSeconds = travelSeconds;
        this.instant = instant;
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

    public int getCost() {
        return cost;
    }

    public boolean isItemFare() {
        return itemFare;
    }

    public int getTravelSeconds() {
        return travelSeconds;
    }

    public boolean isInstant() {
        return instant;
    }
}
