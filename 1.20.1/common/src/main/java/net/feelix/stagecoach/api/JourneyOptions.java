package net.feelix.stagecoach.api;

/** Without ignoreRange the journey leaves from the nearest known stop in reach; with it, from where the player stands. */
public record JourneyOptions(boolean free, boolean instant, boolean ignoreRange, boolean ignoreDiscovery, boolean ignoreCombatLock) {
    public static final JourneyOptions NORMAL = new JourneyOptions(false, false, false, false, false);
}
