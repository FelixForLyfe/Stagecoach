package net.feelix.stagecoach.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** After every built-in check passed and before anything is paid; cancel to refuse with the fail message. */
public class JourneyPreStartEvent {
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onJourneyPreStart(event);
    });

    @FunctionalInterface
    public interface Listener {
        void onJourneyPreStart(JourneyPreStartEvent event);
    }

    private final ServerPlayer player;
    private final @Nullable StopView from;
    private final StopView to;
    private int cost;
    private final boolean itemFare;
    private int travelSeconds;
    private final boolean instant;
    private @Nullable Component failMessage;
    private boolean canceled;

    public JourneyPreStartEvent(ServerPlayer player, @Nullable StopView from, StopView to, int cost, boolean itemFare,
            int travelSeconds, boolean instant, @Nullable Component failMessage) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.cost = cost;
        this.itemFare = itemFare;
        this.travelSeconds = travelSeconds;
        this.instant = instant;
        this.failMessage = failMessage;
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

    public @Nullable Component getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(@Nullable Component failMessage) {
        this.failMessage = failMessage;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
