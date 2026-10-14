package net.feelix.stagecoach.bukkit.command;

import net.feelix.stagecoach.bukkit.Messages;
import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.Wood;
import net.feelix.stagecoach.bukkit.travel.CoachRecord;
import net.feelix.stagecoach.bukkit.travel.JourneyManager;
import net.feelix.stagecoach.bukkit.travel.StagecoachService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class StagecoachCommand implements TabExecutor {
    private static final int LIST_PAGE = 10;
    private static final String USAGE_GIVE = "/stagecoach give <player> <wood> [amount]";

    private final StagecoachPlugin plugin;

    public StagecoachCommand(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cancel" -> cancel(sender, args);
            case "give" -> {
                if (admin(sender)) give(sender, args);
            }
            case "list" -> {
                if (admin(sender)) list(sender, args);
            }
            case "remove" -> {
                if (admin(sender)) remove(sender, args);
            }
            case "reload" -> {
                if (admin(sender)) {
                    boolean clean = plugin.reloadSettings();
                    m.send(sender, "cmd-reloaded");
                    if (!clean) m.send(sender, "cmd-reload-warnings");
                }
            }
            default -> {
                m.sendLines(sender, "cmd-help");
                if (sender.hasPermission(StagecoachService.PERM_ADMIN)) m.sendLines(sender, "cmd-help-admin");
            }
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(StagecoachService.PERM_ADMIN)) return true;
        plugin.messages().send(sender, "cmd-no-permission");
        return false;
    }

    /** Asks first; "/stagecoach cancel confirm" does it. */
    private void cancel(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "cmd-player-only");
            return;
        }
        JourneyManager journeys = plugin.journeys();
        if (!journeys.isTravelling(p.getUniqueId())) {
            m.send(p, "not-travelling");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) journeys.cancel(p);
        else plugin.dialogs().abandon(p);
    }

    private void give(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (args.length < 3) {
            sender.sendMessage(USAGE_GIVE);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            m.send(sender, "cmd-unknown-player", args[1]);
            return;
        }
        Wood wood = Wood.byId(args[2]);
        if (wood == null) {
            m.send(sender, "cmd-unknown-wood", args[2], woodIds());
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                sender.sendMessage(USAGE_GIVE);
                return;
            }
        }
        ItemStack stack = plugin.items().create(wood, amount, m);
        for (ItemStack left : target.getInventory().addItem(stack).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        m.send(sender, "cmd-given", amount, m.c("item-name", m.c("wood-" + wood.id())), target.getName());
    }

    private void list(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        List<CoachRecord> all = new ArrayList<>(plugin.state().coaches());
        if (all.isEmpty()) {
            m.send(sender, "cmd-list-empty");
            return;
        }
        int pages = (all.size() + LIST_PAGE - 1) / LIST_PAGE;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // stays 1
            }
        }
        page = Math.max(1, Math.min(page, pages));
        m.send(sender, "cmd-list-header", all.size(), page, pages);
        for (int i = (page - 1) * LIST_PAGE; i < Math.min(all.size(), page * LIST_PAGE); i++) {
            CoachRecord r = all.get(i);
            String owner = "-";
            if (r.owner != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(r.owner);
                owner = op.getName() != null ? op.getName() : r.owner.toString();
            }
            m.send(sender, "cmd-list-line", r.id, r.name, r.worldName, r.x, r.y, r.z, owner);
        }
    }

    private void remove(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        long id;
        try {
            id = Long.parseLong(args.length >= 2 ? args[1] : "");
        } catch (NumberFormatException e) {
            sender.sendMessage("/stagecoach remove <id>");
            return;
        }
        CoachRecord r = plugin.state().coach(id);
        if (r == null) {
            m.send(sender, "cmd-unknown-coach", args[1]);
            return;
        }
        World world = Bukkit.getWorld(r.world);
        if (world != null) {
            plugin.service().remove(world, r); // loads the chunk; displays not loaded yet are cleaned up as they load
        } else {
            plugin.state().remove(r.id);
            plugin.state().flush();
        }
        m.send(sender, "cmd-removed", r.id, r.name);
        plugin.getLogger().info(sender.getName() + " removed stagecoach #" + r.id + " '" + r.name + "'");
    }

    private static String woodIds() {
        return Arrays.stream(Wood.values()).map(Wood::id).collect(Collectors.joining(", "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(StagecoachService.PERM_ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("help", "cancel"));
            if (admin) options.addAll(List.of("give", "list", "remove", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && admin) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && admin) {
            for (Wood w : Wood.values()) options.add(w.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove") && admin) {
            for (CoachRecord r : plugin.state().coaches()) options.add(Long.toString(r.id));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}
