package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.journeymap.StagecoachJourneyMap;
import net.feelix.stagecoach.compat.xaero.XaeroWaypoints;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.platform.Platform;

import java.util.List;

/** The client's known stops as a "Stagecoach" waypoint set on whichever map mods are installed. */
public final class Waypoints {
    private Waypoints() {}

    public static final String GROUP = "Stagecoach";

    private static final boolean JOURNEYMAP = Platform.get().isModLoaded("journeymap");
    private static final boolean XAERO = Platform.get().isModLoaded("xaerominimap") || Platform.get().isModLoaded("xaerominimapfair");
    private static final int XAERO_CHECK_TICKS = 40;

    private static List<Packets.KnownStop> known = List.of();
    private static boolean journeyMapFailed;
    private static boolean xaeroFailed;
    private static int ticks;

    public static boolean available() {
        return JOURNEYMAP || XAERO;
    }

    public static void update(List<Packets.KnownStop> stops) {
        known = stops;
        if (JOURNEYMAP && !journeyMapFailed) {
            try {
                StagecoachJourneyMap.show(stops);
            } catch (LinkageError | RuntimeException e) {
                journeyMapFailed = true;
                Stagecoach.LOGGER.warn("JourneyMap waypoints switched off: {}", e.toString());
            }
        }
        showOnXaero(true);
    }

    /** Xaero keeps one waypoint world per dimension and server, so the set is laid again when that world changes. */
    public static void tick() {
        if (!XAERO || xaeroFailed || ++ticks < XAERO_CHECK_TICKS) return;
        ticks = 0;
        showOnXaero(false);
    }

    public static void clear() {
        update(List.of());
    }

    public static List<Packets.KnownStop> known() {
        return known;
    }

    private static void showOnXaero(boolean changed) {
        if (!XAERO || xaeroFailed) return;
        try {
            XaeroWaypoints.show(known, changed);
        } catch (LinkageError | RuntimeException e) {
            xaeroFailed = true;
            Stagecoach.LOGGER.warn("Xaero's Minimap waypoints switched off: {}", e.toString());
        }
    }
}
