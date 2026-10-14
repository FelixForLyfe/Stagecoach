package net.feelix.stagecoach.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

final class OpenPacBridge {
    private OpenPacBridge() {}

    /** The claim's own access rule; an unclaimed chunk follows the server's wilderness setting. */
    static boolean prevents(ServerPlayer player, BlockPos pos) {
        return !OpenPACServerAPI.get(player.level().getServer()).getChunkProtection()
                .hasChunkAccess(player, player.level().dimension().identifier(), pos.getX() >> 4, pos.getZ() >> 4);
    }
}
