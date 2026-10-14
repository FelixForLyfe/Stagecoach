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

/** True when a listener cancelled the event. */
public final class EventHooks {
    private EventHooks() {}

    public static boolean post(JourneyPreStartEvent event) {
        JourneyPreStartEvent.EVENT.invoker().onJourneyPreStart(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("preJourney", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(JourneyStartedEvent event) {
        JourneyStartedEvent.EVENT.invoker().onJourneyStarted(event);
        KubeJs.post("journeyStarted", event);
        return false;
    }

    public static boolean post(JourneyCompletedEvent event) {
        JourneyCompletedEvent.EVENT.invoker().onJourneyCompleted(event);
        KubeJs.post("journeyCompleted", event);
        return false;
    }

    public static boolean post(JourneyEndedEvent event) {
        JourneyEndedEvent.EVENT.invoker().onJourneyEnded(event);
        KubeJs.post("journeyEnded", event);
        return false;
    }

    public static boolean post(StopDiscoverEvent event) {
        StopDiscoverEvent.EVENT.invoker().onStopDiscover(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("stopDiscover", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(CoachPlaceEvent event) {
        CoachPlaceEvent.EVENT.invoker().onCoachPlace(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("coachPlace", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(CoachBreakEvent event) {
        CoachBreakEvent.EVENT.invoker().onCoachBreak(event);
        boolean cancelled = event.isCanceled();
        if (KubeJs.post("coachBreak", event)) {
            event.setCanceled(true);
            cancelled = true;
        }
        return cancelled;
    }

    public static boolean post(DestinationListEvent event) {
        DestinationListEvent.EVENT.invoker().onDestinationList(event);
        KubeJs.post("destinationList", event);
        return false;
    }
}
