package net.feelix.stagecoach.platform;

import net.feelix.stagecoach.api.event.CoachBreakEvent;
import net.feelix.stagecoach.api.event.CoachPlaceEvent;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyCompletedEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.JourneyStartedEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;

/** True when a listener cancelled the event. */
public final class EventHooks {
    private EventHooks() {}

    public static boolean post(JourneyPreStartEvent event) {
        return JourneyPreStartEvent.BUS.post(event) || event.isCanceled();
    }

    public static boolean post(JourneyStartedEvent event) {
        JourneyStartedEvent.BUS.post(event);
        return false;
    }

    public static boolean post(JourneyCompletedEvent event) {
        JourneyCompletedEvent.BUS.post(event);
        return false;
    }

    public static boolean post(JourneyEndedEvent event) {
        JourneyEndedEvent.BUS.post(event);
        return false;
    }

    public static boolean post(StopDiscoverEvent event) {
        return StopDiscoverEvent.BUS.post(event) || event.isCanceled();
    }

    public static boolean post(CoachPlaceEvent event) {
        return CoachPlaceEvent.BUS.post(event) || event.isCanceled();
    }

    public static boolean post(CoachBreakEvent event) {
        return CoachBreakEvent.BUS.post(event) || event.isCanceled();
    }

    public static boolean post(DestinationListEvent event) {
        DestinationListEvent.BUS.post(event);
        return false;
    }
}
