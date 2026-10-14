package net.feelix.stagecoach.compat;

import net.feelix.stagecoach.platform.Platform;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** FTB Chunks has no Forge build for this version; Open Parties and Claims does. */
public final class ClaimCompat {
    private ClaimCompat() {}

    private static final boolean OPEN_PAC = Platform.get().isModLoaded("openpartiesandclaims");

    public static boolean prevents(ServerPlayerEntity player, BlockPos pos) {
        return OPEN_PAC && OpenPacBridge.prevents(player, pos);
    }
}
