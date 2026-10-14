package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class ClaimCompat {
    private ClaimCompat() {}

    private static final boolean OPEN_PAC = Platform.get().isModLoaded("openpartiesandclaims");

    /** True if a claim mod would stop this player editing the block at {@code pos}. */
    public static boolean prevents(ServerPlayer player, BlockPos pos) {
        return OPEN_PAC && OpenPacBridge.prevents(player, pos);
    }
}
