package net.feelix.stagecoach.compat;

import dev.ftb.mods.ftbranks.api.FTBRanksAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Only loaded while FTB Ranks is installed; its ranks answer before any other permission system. */
public final class FtbRanksBridge {
    private FtbRanksBridge() {}

    /** Null when no rank sets the node; a parent counts (stagecoach.admin: true covers stagecoach.admin.manage). */
    public static @Nullable Boolean check(ServerPlayer player, String node) {
        return FTBRanksAPI.getPermissionValue(player, node).asBoolean().orElse(null);
    }

    /** Text and number values only, so a parent set to true never becomes an option's value. */
    public static @Nullable String option(ServerPlayer player, String key) {
        return FTBRanksAPI.getPermissionValue(player, key).asString().orElse(null);
    }
}
