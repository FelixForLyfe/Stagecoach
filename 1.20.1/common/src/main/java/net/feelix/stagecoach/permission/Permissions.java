package net.feelix.stagecoach.permission;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.compat.FtbRanksBridge;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Nodes go to FTB Ranks, then the loader's permission system (LuckPerms), and only then to these defaults. */
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
        BYPASS_COST("bypass.cost"),
        GLOBAL("global");

        public final String path;
        public final String node;

        Node(String path) {
            this.path = path;
            this.node = Stagecoach.MOD_ID + "." + path;
        }

        public boolean byDefault(ServerPlayerEntity player) {
            StagecoachConfig cfg = config(player);
            return switch (this) {
                case PLACE, TRAVEL, DISCOVER, BREAK_OWN, RENAME_OWN -> true;
                case BREAK_ANY, BYPASS_LIMIT -> player.hasPermissionLevel(2);
                case ADMIN_MANAGE -> cfg.opsCanManageAnyCoach && player.hasPermissionLevel(2);
                case ADMIN_COMMANDS -> player.hasPermissionLevel(cfg.adminCommandLevel);
                case BYPASS_COST -> false;
                case GLOBAL -> cfg.anyoneCanMakeGlobal;
            };
        }
    }

    private record Cached(StagecoachConfig base, int tick, StagecoachConfig config) {}

    private static final boolean FTB_RANKS = Platform.get().isModLoaded("ftbranks");
    private static final Map<UUID, Cached> CONFIGS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** The server config with this player's permission options (LuckPerms meta) laid over it, worked out at most once a tick. */
    public static StagecoachConfig config(ServerPlayerEntity player) {
        StagecoachConfig base = StagecoachConfig.get();
        int tick = player.getServer().getTicks();
        Cached cached = CONFIGS.get(player.getUuid());
        if (cached != null && cached.base() == base && cached.tick() == tick) return cached.config();
        List<String> warnings = new ArrayList<>();
        Platform platform = Platform.get();
        StagecoachConfig cfg = base.withOverrides(key -> option(player, platform, StagecoachConfig.optionKey(key)), warnings);
        for (String w : warnings) {
            if (WARNED.add(w)) Stagecoach.LOGGER.warn("A Stagecoach permission option for {} is ignored: {}", player.getGameProfile().getName(), w);
        }
        CONFIGS.put(player.getUuid(), new Cached(base, tick, cfg));
        return cfg;
    }

    public static void forget(UUID player) {
        CONFIGS.remove(player);
    }

    private static @Nullable String option(ServerPlayerEntity player, Platform platform, String key) {
        String ranked = FTB_RANKS ? FtbRanksBridge.option(player, key) : null;
        return ranked != null ? ranked : platform.option(player, key);
    }

    public static boolean check(ServerPlayerEntity player, Node node) {
        Boolean ranked = FTB_RANKS ? FtbRanksBridge.check(player, node.node) : null;
        return ranked != null ? ranked : Platform.get().hasPermission(player, node.node, node.byDefault(player));
    }

    /** Any node, as travel rules name them; false unless a permissions mod grants it. */
    public static boolean check(ServerPlayerEntity player, String node) {
        for (Node n : Node.values()) if (n.node.equals(node)) return check(player, n);
        Boolean ranked = FTB_RANKS ? FtbRanksBridge.check(player, node) : null;
        return ranked != null ? ranked : Platform.get().hasPermission(player, node, false);
    }
}
