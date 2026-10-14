package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.compat.kubejs.KubeJsBridge;
import net.feelix.stagecoach.platform.Platform;

/** Hands each Stagecoach event to KubeJS scripts too; the bridge is only loaded while KubeJS is installed. */
public final class KubeJs {
    private KubeJs() {}

    private static final boolean LOADED = Platform.get().isModLoaded("kubejs");

    /** True when a script cancelled it. */
    public static boolean post(String handler, Object event) {
        return LOADED && KubeJsBridge.post(handler, event);
    }
}
