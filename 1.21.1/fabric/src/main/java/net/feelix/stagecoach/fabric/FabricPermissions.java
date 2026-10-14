package net.feelix.stagecoach.fabric;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.network.ServerPlayerEntity;

/** Only loaded while fabric-permissions-api is installed. */
final class FabricPermissions {
    private FabricPermissions() {}

    static boolean check(ServerPlayerEntity player, String node, boolean fallback) {
        return Permissions.check(player, node, fallback);
    }
}
