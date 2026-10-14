package net.feelix.stagecoach.compat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import xaero.pac.common.server.api.OpenPACServerAPI;

final class OpenPacBridge {
    private OpenPacBridge() {}

    /** The claim's own access rule; an unclaimed chunk follows the server's wilderness setting. */
    static boolean prevents(ServerPlayerEntity player, BlockPos pos) {
        return !OpenPACServerAPI.get(player.getServer()).getChunkProtection()
                .hasChunkAccess(player, player.getWorld().getRegistryKey().getValue(), pos.getX() >> 4, pos.getZ() >> 4);
    }
}
