package net.feelix.stagecoach.fabric;

import me.lucko.fabric.api.permissions.v0.Options;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.level.ServerPlayer;

/** Only loaded while fabric-permissions-api is installed. */
final class FabricPermissions {
    private FabricPermissions() {}

    static boolean check(ServerPlayer player, String node, boolean fallback) {
        return Permissions.check(player, node, fallback);
    }

    static String option(ServerPlayer player, String key) {
        return Options.get(player, key).orElse(null);
    }
}
