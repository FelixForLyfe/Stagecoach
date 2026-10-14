package net.feelix.stagecoach.client;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.platform.Platform;

import java.util.ArrayList;
import java.util.List;

public final class StagecoachClient {
    private StagecoachClient() {}

    public static void init() {
        List<String> warnings = new ArrayList<>();
        ClientConfig.load(Platform.get().configDir().resolve(Stagecoach.CLIENT_CONFIG_FILE), warnings);
        for (String w : warnings) Stagecoach.LOGGER.warn("config/{}: {}", Stagecoach.CLIENT_CONFIG_FILE, w);
        ClientNet.init();
    }
}
