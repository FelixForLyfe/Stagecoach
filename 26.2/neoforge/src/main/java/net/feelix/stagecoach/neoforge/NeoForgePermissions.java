package net.feelix.stagecoach.neoforge;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.TravelRule;
import net.feelix.stagecoach.permission.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** NeoForge only answers for nodes registered when the server starts: Stagecoach's own, plus any a travel rule names. */
final class NeoForgePermissions {
    private NeoForgePermissions() {}

    private static final Map<String, PermissionNode<Boolean>> NODES = new ConcurrentHashMap<>();

    static void onGather(PermissionGatherEvent.Nodes event) {
        NODES.clear();
        for (Permissions.Node node : Permissions.Node.values()) {
            add(event, new PermissionNode<>(Stagecoach.MOD_ID, node.path, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> player != null && node.byDefault(player)));
        }
        for (TravelRule rule : StagecoachData.get().rules().values()) {
            String name = rule.permission();
            int dot = name == null ? -1 : name.indexOf('.');
            if (dot <= 0 || dot == name.length() - 1 || NODES.containsKey(name)) continue;
            try {
                add(event, new PermissionNode<>(name.substring(0, dot), name.substring(dot + 1), PermissionTypes.BOOLEAN,
                        (player, uuid, context) -> false));
            } catch (RuntimeException e) {
                Stagecoach.LOGGER.warn("A travel rule names the permission '{}', which NeoForge won't register: {}", name, e.getMessage());
            }
        }
    }

    private static void add(PermissionGatherEvent.Nodes event, PermissionNode<Boolean> node) {
        event.addNodes(node);
        NODES.put(node.getNodeName(), node);
    }

    static boolean check(ServerPlayer player, String node, boolean fallback) {
        PermissionNode<Boolean> registered = NODES.get(node);
        return registered == null ? fallback : PermissionAPI.getPermission(player, registered);
    }
}
