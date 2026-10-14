package net.feelix.stagecoach.api;

public enum JourneyResult {
    STARTED,
    NOT_ENOUGH_LEVELS,
    NOT_ENOUGH_ITEMS,
    TOO_FAR,
    CROSS_DIMENSION_DISABLED,
    COMBAT_LOCKED,
    COOLDOWN,
    NOT_DISCOVERED,
    OUT_OF_RANGE,
    CANCELLED_BY_EVENT,
    ALREADY_TRAVELLING,
    LOCKED_BY_RULE,
    NO_PERMISSION,
    UNAVAILABLE;

    public boolean started() {
        return this == STARTED;
    }
}
