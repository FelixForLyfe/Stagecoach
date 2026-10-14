package net.feelix.stagecoach.compat.xaero;

import net.feelix.stagecoach.compat.Waypoints;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.resources.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.List;
import java.util.Locale;

/** Xaero has no public API, so these are its own classes; the waypoints are temporary and only for the current dimension. */
public final class XaeroWaypoints {
    private XaeroWaypoints() {}

    private static MinimapWorld shownIn;

    public static void show(List<Packets.KnownStop> stops, boolean changed) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
        if (world == null || !changed && world == shownIn) return;

        WaypointSet set = world.getWaypointSet(Waypoints.GROUP);
        if (set == null) {
            if (stops.isEmpty()) return;
            world.addWaypointSet(Waypoints.GROUP);
            set = world.getWaypointSet(Waypoints.GROUP);
        }
        set.clear();
        shownIn = world;
        Identifier dimension = world.getDimId() == null ? null : world.getDimId().identifier();
        for (Packets.KnownStop stop : stops) {
            if (!stop.dimension().equals(dimension)) continue;
            set.add(new Waypoint(stop.pos().getX(), stop.pos().getY() + 1, stop.pos().getZ(), stop.name(), initials(stop.name()),
                    WaypointColor.GOLD, WaypointPurpose.NORMAL, true, true));
        }
    }

    private static String initials(String name) {
        return name.isEmpty() ? "S" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }
}
