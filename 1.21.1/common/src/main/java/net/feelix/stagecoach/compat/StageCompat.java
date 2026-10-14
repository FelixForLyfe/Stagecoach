package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.server.network.ServerPlayerEntity;

public final class StageCompat {
    private StageCompat() {}

    private static boolean warned;

    /** Without GameStages a rule that needs a stage can never be met. */
    public static boolean has(ServerPlayerEntity player, String stage) {
        if (Platform.get().isModLoaded("gamestages")) return Platform.get().hasGameStage(player, stage);
        if (!warned) {
            warned = true;
            Stagecoach.LOGGER.warn("A travel rule needs the GameStages stage '{}', but GameStages isn't installed: it always locks", stage);
        }
        return false;
    }
}
