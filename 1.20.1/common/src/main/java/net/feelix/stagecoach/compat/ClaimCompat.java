package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.platform.Platform;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class ClaimCompat {
    private ClaimCompat() {}

    private static final boolean FTB_CHUNKS = Platform.get().isModLoaded("ftbchunks");
    private static final boolean OPEN_PAC = Platform.get().isModLoaded("openpartiesandclaims");

    /** True if a claim mod would stop this player editing the block at {@code pos}. */
    public static boolean prevents(ServerPlayerEntity player, BlockPos pos) {
        return FTB_CHUNKS && FtbChunksBridge.prevents(player, pos) || OPEN_PAC && OpenPacBridge.prevents(player, pos);
    }
}
