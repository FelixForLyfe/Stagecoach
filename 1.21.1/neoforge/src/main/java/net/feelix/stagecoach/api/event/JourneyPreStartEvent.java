package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/** After every built-in check passed and before anything is paid; cancel to refuse with the fail message. */
public class JourneyPreStartEvent extends Event implements ICancellableEvent {
    private final ServerPlayerEntity player;
    private final @Nullable StopView from;
    private final StopView to;
    private int cost;
    private final boolean itemFare;
    private int travelSeconds;
    private final boolean instant;
    private @Nullable Text failMessage;

    public JourneyPreStartEvent(ServerPlayerEntity player, @Nullable StopView from, StopView to, int cost, boolean itemFare,
            int travelSeconds, boolean instant, @Nullable Text failMessage) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.cost = cost;
        this.itemFare = itemFare;
        this.travelSeconds = travelSeconds;
        this.instant = instant;
        this.failMessage = failMessage;
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

    public void setCost(int cost) {
        this.cost = cost;
    }

    public boolean isItemFare() {
        return itemFare;
    }

    public int getTravelSeconds() {
        return travelSeconds;
    }

    public void setTravelSeconds(int travelSeconds) {
        this.travelSeconds = travelSeconds;
    }

    public boolean isInstant() {
        return instant;
    }

    public @Nullable Text getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(@Nullable Text failMessage) {
        this.failMessage = failMessage;
    }
}
