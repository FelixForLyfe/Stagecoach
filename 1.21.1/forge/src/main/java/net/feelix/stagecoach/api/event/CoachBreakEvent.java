package net.feelix.stagecoach.api.event;

import net.feelix.stagecoach.api.StopView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Before a player takes a coach down; cancel to refuse. */
@Cancelable
public class CoachBreakEvent extends Event {
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
