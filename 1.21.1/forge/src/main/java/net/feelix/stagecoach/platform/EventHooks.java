package net.feelix.stagecoach.platform;

import net.feelix.stagecoach.api.event.CoachBreakEvent;
import net.feelix.stagecoach.api.event.CoachPlaceEvent;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyCompletedEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.JourneyStartedEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;
import net.minecraftforge.common.MinecraftForge;

/** True when a listener cancelled the event. */
public final class EventHooks {
    private EventHooks() {}

    public static boolean post(JourneyPreStartEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(JourneyStartedEvent event) {
        MinecraftForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(JourneyCompletedEvent event) {
        MinecraftForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(JourneyEndedEvent event) {
        MinecraftForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(StopDiscoverEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(CoachPlaceEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(CoachBreakEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(DestinationListEvent event) {
        MinecraftForge.EVENT_BUS.post(event);
        return false;
    }
}
