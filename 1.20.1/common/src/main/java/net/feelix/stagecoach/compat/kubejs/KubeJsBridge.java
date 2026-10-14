package net.feelix.stagecoach.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.Map;

/** {@code StagecoachEvents.preJourney(e => ...)} and the rest; {@code e.event} is the Stagecoach event itself. */
public final class KubeJsBridge {
    private KubeJsBridge() {}

    static final EventGroup GROUP = EventGroup.of("StagecoachEvents");
    private static final Map<String, EventHandler> HANDLERS = Map.of(
            "preJourney", GROUP.server("preJourney", () -> StagecoachEventJS.class).hasResult(),
            "journeyStarted", GROUP.server("journeyStarted", () -> StagecoachEventJS.class),
            "journeyCompleted", GROUP.server("journeyCompleted", () -> StagecoachEventJS.class),
            "journeyEnded", GROUP.server("journeyEnded", () -> StagecoachEventJS.class),
            "stopDiscover", GROUP.server("stopDiscover", () -> StagecoachEventJS.class).hasResult(),
            "coachPlace", GROUP.server("coachPlace", () -> StagecoachEventJS.class).hasResult(),
            "coachBreak", GROUP.server("coachBreak", () -> StagecoachEventJS.class).hasResult(),
            "destinationList", GROUP.server("destinationList", () -> StagecoachEventJS.class));

    public static boolean post(String name, Object event) {
        EventHandler handler = HANDLERS.get(name);
        if (handler == null || !handler.hasListeners()) return false;
        return handler.post(ScriptType.SERVER, new StagecoachEventJS(event)).interruptFalse();
    }
}
