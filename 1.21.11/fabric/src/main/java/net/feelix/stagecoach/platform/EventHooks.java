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
        JourneyPreStartEvent.EVENT.invoker().onJourneyPreStart(event);
        return event.isCanceled();
    }

    public static boolean post(JourneyStartedEvent event) {
        JourneyStartedEvent.EVENT.invoker().onJourneyStarted(event);
        return false;
    }

    public static boolean post(JourneyCompletedEvent event) {
        JourneyCompletedEvent.EVENT.invoker().onJourneyCompleted(event);
        return false;
    }

    public static boolean post(JourneyEndedEvent event) {
        JourneyEndedEvent.EVENT.invoker().onJourneyEnded(event);
        return false;
    }

    public static boolean post(StopDiscoverEvent event) {
        StopDiscoverEvent.EVENT.invoker().onStopDiscover(event);
        return event.isCanceled();
    }

    public static boolean post(CoachPlaceEvent event) {
        CoachPlaceEvent.EVENT.invoker().onCoachPlace(event);
        return event.isCanceled();
    }

    public static boolean post(CoachBreakEvent event) {
        CoachBreakEvent.EVENT.invoker().onCoachBreak(event);
        return event.isCanceled();
    }

    public static boolean post(DestinationListEvent event) {
        DestinationListEvent.EVENT.invoker().onDestinationList(event);
        return false;
    }
}
