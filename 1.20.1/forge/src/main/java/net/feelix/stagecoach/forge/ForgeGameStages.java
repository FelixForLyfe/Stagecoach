package net.feelix.stagecoach.forge;

import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/** Only loaded while GameStages is installed. */
final class ForgeGameStages {
    private ForgeGameStages() {}

    static boolean has(ServerPlayerEntity player, String stage) {
        return GameStageHelper.hasStage(player, stage);
    }
}
