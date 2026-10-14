package net.feelix.stagecoach.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventJS;

public class StagecoachEventJS extends EventJS {
    private final Object event;

    public StagecoachEventJS(Object event) {
        this.event = event;
    }

    public Object getEvent() {
        return event;
    }
}
