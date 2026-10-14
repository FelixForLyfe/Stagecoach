package net.feelix.stagecoach.api.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/** Before a coach is placed by a player or by a command (then the player is null); cancel to refuse. */
public class CoachPlaceEvent extends Event implements ICancellableEvent {
    private final @Nullable ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos pos;
    private final Direction facing;
    private final String wood;
    private @Nullable Text failMessage;

    public CoachPlaceEvent(@Nullable ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction facing,
            String wood, @Nullable Text failMessage) {
        this.player = player;
        this.world = world;
        this.pos = pos;
        this.facing = facing;
        this.wood = wood;
        this.failMessage = failMessage;
    }

    public @Nullable ServerPlayerEntity getPlayer() {
        return player;
    }

    public ServerWorld getWorld() {
        return world;
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

    public @Nullable Text getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(@Nullable Text failMessage) {
        this.failMessage = failMessage;
    }
}
