package net.feelix.stagecoach.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** {@code cost} is levels, or items while the server charges items; {@code lockMessage} is shown for LOCKED. */
public record Destination(StopView stop, int distance, int cost, int seconds, Status status, @Nullable Component lockMessage) {
    public enum Status {
        OK, CANT_AFFORD, OTHER_DIMENSION, TOO_FAR, LOCKED
    }

    public Destination withCost(int newCost) {
        return new Destination(stop, distance, newCost, seconds, status, lockMessage);
    }

    public Destination withStatus(Status newStatus, @Nullable Component message) {
        return new Destination(stop, distance, cost, seconds, newStatus, message);
    }
}
