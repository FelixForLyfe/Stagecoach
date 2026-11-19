package net.feelix.stagecoach.compat;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** FTB Ranks has no Forge build for this version: nothing to ask. */
public final class FtbRanksBridge {
    private FtbRanksBridge() {}

    public static @Nullable Boolean check(ServerPlayer player, String node) {
        return null;
    }

    public static @Nullable String option(ServerPlayer player, String key) {
        return null;
    }
}
