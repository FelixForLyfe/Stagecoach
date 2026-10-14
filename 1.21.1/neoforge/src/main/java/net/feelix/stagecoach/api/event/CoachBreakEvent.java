package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/** Before a player takes a coach down; cancel to refuse. */
public class CoachBreakEvent extends Event implements ICancellableEvent {
    private final ServerPlayerEntity player;
    private final StopView stop;
    private @Nullable Text failMessage;

    public CoachBreakEvent(ServerPlayerEntity player, StopView stop, @Nullable Text failMessage) {
        this.player = player;
        this.stop = stop;
        this.failMessage = failMessage;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public StopView getStop() {
        return stop;
    }

    public @Nullable Text getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(@Nullable Text failMessage) {
        this.failMessage = failMessage;
    }
}
