package net.feelix.stagecoach.permission;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/** Nodes go through the loader's permission system when one is installed, else these defaults apply. */
public final class Permissions {
    private Permissions() {}

    public enum Node {
        PLACE("place"),
        TRAVEL("travel"),
        DISCOVER("discover"),
        BREAK_OWN("break.own"),
        BREAK_ANY("break.any"),
        RENAME_OWN("rename.own"),
        ADMIN_MANAGE("admin.manage"),
        ADMIN_COMMANDS("admin.commands"),
        BYPASS_LIMIT("bypass.limit"),
        BYPASS_COST("bypass.cost");

        public final String path;
        public final String node;

        Node(String path) {
            this.path = path;
            this.node = Stagecoach.MOD_ID + "." + path;
        }

        public boolean byDefault(ServerPlayer player) {
            StagecoachConfig cfg = StagecoachConfig.get();
            return switch (this) {
                case PLACE, TRAVEL, DISCOVER, BREAK_OWN, RENAME_OWN -> true;
                case BREAK_ANY, BYPASS_LIMIT -> hasLevel(player.permissions(), 2);
                case ADMIN_MANAGE -> cfg.opsCanManageAnyCoach && hasLevel(player.permissions(), 2);
                case ADMIN_COMMANDS -> hasLevel(player.permissions(), cfg.adminCommandLevel);
                case BYPASS_COST -> false;
            };
        }
    }

    public static boolean hasLevel(PermissionSet permissions, int level) {
        return permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static boolean check(ServerPlayer player, Node node) {
        return Platform.get().hasPermission(player, node.node, node.byDefault(player));
    }

    /** Any node, as travel rules name them; false unless a permissions mod grants it. */
    public static boolean check(ServerPlayer player, String node) {
        for (Node n : Node.values()) if (n.node.equals(node)) return check(player, n);
        return Platform.get().hasPermission(player, node, false);
    }
}
