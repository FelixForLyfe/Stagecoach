package net.feelix.stagecoach.gametest;

import net.fabricmc.api.ModInitializer;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;

public final class FabricEventHooks implements ModInitializer {
    @Override
    public void onInitialize() {
        JourneyPreStartEvent.EVENT.register(EventGameTests::onPreStart);
        DestinationListEvent.EVENT.register(EventGameTests::onList);
        JourneyEndedEvent.EVENT.register(EventGameTests::onEnded);
        StopDiscoverEvent.EVENT.register(EventGameTests::onDiscover);
    }
}
