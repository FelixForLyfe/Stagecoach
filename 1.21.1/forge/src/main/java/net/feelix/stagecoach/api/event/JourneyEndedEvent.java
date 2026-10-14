package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.EndReason;
import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Exactly once per journey, however it ended. */
public class JourneyEndedEvent extends Event {
    private final ServerPlayerEntity player;
    private final @Nullable StopView from;
    private final StopView to;
    private final EndReason reason;

    public JourneyEndedEvent(ServerPlayerEntity player, @Nullable StopView from, StopView to, EndReason reason) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.reason = reason;
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

    public EndReason getReason() {
        return reason;
    }
}
