package net.feelix.stagecoach.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.feelix.stagecoach.api.StagecoachAPI;

/** Listed in kubejs.plugins.txt, so KubeJS only loads it when KubeJS is installed. */
public class StagecoachKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(KubeJsBridge.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Stagecoach", StagecoachAPI.get());
    }
}
