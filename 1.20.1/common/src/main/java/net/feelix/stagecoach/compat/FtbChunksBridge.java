package net.feelix.stagecoach.compat;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

final class FtbChunksBridge {
    private FtbChunksBridge() {}

    /** FTB's own placement check: privacy, teams, allies, fake players and the admin bypass. */
    static boolean prevents(ServerPlayerEntity player, BlockPos pos) {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        return api.isManagerLoaded() && api.getManager().shouldPreventInteraction(player, Hand.MAIN_HAND, pos, Protection.EDIT_BLOCK, null);
    }
}
