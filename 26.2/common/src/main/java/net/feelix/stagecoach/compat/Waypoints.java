package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.journeymap.StagecoachJourneyMap;
import net.feelix.stagecoach.compat.xaero.XaeroWaypoints;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** The client's known stops as a "Stagecoach" waypoint set on whichever map mods are installed. */
public final class Waypoints {
    private Waypoints() {}

    public static final String GROUP = "Stagecoach";

    private static final boolean JOURNEYMAP = Platform.get().isModLoaded("journeymap");
    private static final boolean XAERO = Platform.get().isModLoaded("xaerominimap") || Platform.get().isModLoaded("xaerominimapfair");
    private static final int XAERO_CHECK_TICKS = 40;

    /** A loader's own map integration (Antique Atlas on Fabric), set by its client entrypoint. */
    public static @Nullable Consumer<List<Packets.KnownStop>> extraMap;

    private static List<Packets.KnownStop> known = List.of();
    private static boolean extraMapFailed;
    private static boolean journeyMapFailed;
    private static boolean xaeroFailed;
    private static int ticks;

    public static boolean available() {
        return JOURNEYMAP || XAERO || extraMap != null;
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
        if (extraMap != null && !extraMapFailed) {
            try {
                extraMap.accept(stops);
            } catch (LinkageError | RuntimeException e) {
                extraMapFailed = true;
                Stagecoach.LOGGER.warn("Map markers switched off: {}", e.toString());
            }
        }
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
