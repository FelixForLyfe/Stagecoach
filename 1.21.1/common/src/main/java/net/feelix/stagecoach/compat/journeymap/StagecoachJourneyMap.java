package net.feelix.stagecoach.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.Waypoints;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.List;

/** Found by JourneyMap itself: the "journeymap" entrypoint on Fabric, this annotation on Forge. */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class StagecoachJourneyMap implements IClientPlugin {
    private static final int COLOUR = 0xD9A441;

    private static IClientAPI api;
    private static boolean mapping;

    @Override
    public String getModId() {
        return Stagecoach.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        ClientEventRegistry.MAPPING_EVENT.subscribe(Stagecoach.MOD_ID, event -> {
            mapping = event.getStage() == MappingEvent.Stage.MAPPING_STARTED;
            if (mapping) show(Waypoints.known());
        });
    }

    /** The group is not persistent: the server sends the list again on every join. */
    public static void show(List<Packets.KnownStop> stops) {
        if (api == null || !mapping) return;
        api.removeWaypointGroups(Stagecoach.MOD_ID, true);
        if (stops.isEmpty()) return;

        WaypointGroup group = WaypointFactory.createWaypointGroup(Stagecoach.MOD_ID, Waypoints.GROUP);
        group.setPersistent(false);
        group.setColor(COLOUR);
        api.addWaypointGroup(group);
        for (Packets.KnownStop stop : stops) {
            Waypoint waypoint = WaypointFactory.createWaypoint(Stagecoach.MOD_ID, stop.pos().up(), stop.name(),
                    RegistryKey.of(RegistryKeys.WORLD, stop.dimension()), false);
            waypoint.setColor(COLOUR);
            group.addWaypoint(waypoint);
            api.addWaypoint(Stagecoach.MOD_ID, waypoint);
        }
    }
}
