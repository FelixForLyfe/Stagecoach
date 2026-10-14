package net.feelix.stagecoach.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.feelix.stagecoach.api.StagecoachAPI;

/** Listed in kubejs.plugins.txt, so KubeJS only loads it when KubeJS is installed. */
public class StagecoachKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        KubeJsBridge.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("Stagecoach", StagecoachAPI.get());
    }
}
