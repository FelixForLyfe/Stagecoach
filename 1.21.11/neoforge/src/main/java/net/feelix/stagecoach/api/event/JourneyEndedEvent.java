package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.EndReason;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Exactly once per journey, however it ended. */
public class JourneyEndedEvent extends Event {
    private final ServerPlayer player;
    private final @Nullable StopView from;
    private final StopView to;
    private final EndReason reason;

    public JourneyEndedEvent(ServerPlayer player, @Nullable StopView from, StopView to, EndReason reason) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.reason = reason;
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

    public EndReason getReason() {
        return reason;
    }
}
