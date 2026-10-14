package net.feelix.stagecoach.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class StagecoachKubeEvent implements KubeEvent {
    private final Object event;

    public StagecoachKubeEvent(Object event) {
        this.event = event;
    }

    public Object getEvent() {
        return event;
    }
}
