package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/** Before a player takes a coach down; cancel to refuse. */
public class CoachBreakEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final StopView stop;
    private @Nullable Component failMessage;

    public CoachBreakEvent(ServerPlayer player, StopView stop, @Nullable Component failMessage) {
        this.player = player;
        this.stop = stop;
        this.failMessage = failMessage;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public StopView getStop() {
        return stop;
    }

    public @Nullable Component getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(@Nullable Component failMessage) {
        this.failMessage = failMessage;
    }
}
