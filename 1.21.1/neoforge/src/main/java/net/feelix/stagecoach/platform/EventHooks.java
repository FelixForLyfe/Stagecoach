package net.feelix.stagecoach.platform;

import net.feelix.stagecoach.api.event.CoachBreakEvent;
import net.feelix.stagecoach.api.event.CoachPlaceEvent;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyCompletedEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.JourneyStartedEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;
import net.feelix.stagecoach.compat.KubeJs;
import net.neoforged.neoforge.common.NeoForge;

/** True when a listener cancelled the event. */
public final class EventHooks {
    private EventHooks() {}

    public static boolean post(JourneyPreStartEvent event) {
        NeoForge.EVENT_BUS.post(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("preJourney", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(JourneyStartedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        KubeJs.post("journeyStarted", event);
        return false;
    }

    public static boolean post(JourneyCompletedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        KubeJs.post("journeyCompleted", event);
        return false;
    }

    public static boolean post(JourneyEndedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        KubeJs.post("journeyEnded", event);
        return false;
    }

    public static boolean post(StopDiscoverEvent event) {
        NeoForge.EVENT_BUS.post(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("stopDiscover", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(CoachPlaceEvent event) {
        NeoForge.EVENT_BUS.post(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("coachPlace", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(CoachBreakEvent event) {
        NeoForge.EVENT_BUS.post(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("coachBreak", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(DestinationListEvent event) {
        NeoForge.EVENT_BUS.post(event);
        KubeJs.post("destinationList", event);
        return false;
    }
}
