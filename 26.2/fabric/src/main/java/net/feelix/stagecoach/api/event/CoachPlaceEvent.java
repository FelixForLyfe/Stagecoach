package net.feelix.stagecoach.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Before a coach is placed by a player or by a command (then the player is null); cancel to refuse. */
public class CoachPlaceEvent {
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onCoachPlace(event);
    });

    @FunctionalInterface
    public interface Listener {
        void onCoachPlace(CoachPlaceEvent event);
    }

    private final @Nullable ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction facing;
    private final String wood;
    private @Nullable Component failMessage;
    private boolean canceled;

    public CoachPlaceEvent(@Nullable ServerPlayer player, ServerLevel level, BlockPos pos, Direction facing,
            String wood, @Nullable Component failMessage) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.facing = facing;
        this.wood = wood;
        this.failMessage = failMessage;
    }

    public @Nullable ServerPlayer getPlayer() {
        return player;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFacing() {
        return facing;
    }

    public String getWood() {
        return wood;
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
