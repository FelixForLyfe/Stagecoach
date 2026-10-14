package net.feelix.stagecoach.bukkit;

import net.feelix.stagecoach.bukkit.command.StagecoachCommand;
import net.feelix.stagecoach.bukkit.config.StagecoachConfig;
import net.feelix.stagecoach.bukkit.item.CoachItems;
import net.feelix.stagecoach.bukkit.listen.CoachListener;
import net.feelix.stagecoach.bukkit.listen.ItemGuardListener;
import net.feelix.stagecoach.bukkit.listen.PlayerListener;
import net.feelix.stagecoach.bukkit.model.CoachDisplays;
import net.feelix.stagecoach.bukkit.pack.ResourcePack;
import net.feelix.stagecoach.bukkit.travel.Fare;
import net.feelix.stagecoach.bukkit.travel.JourneyManager;
import net.feelix.stagecoach.bukkit.travel.NetworkState;
import net.feelix.stagecoach.bukkit.travel.StagecoachService;
import net.feelix.stagecoach.bukkit.ui.Dialogs;
import net.feelix.stagecoach.bukkit.ui.GhostPreview;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class StagecoachPlugin extends JavaPlugin {
    private StagecoachConfig settings = StagecoachConfig.DEFAULTS;
    private Fare fare;
    private Messages messages;
    private NetworkState state;
    private CoachItems items;
    private CoachDisplays displays;
    private JourneyManager journeys;
    private StagecoachService service;
    private Dialogs dialogs;
    private GhostPreview ghosts;
    private ResourcePack pack;
    private CoachListener coachListener;
    private long ticks;

    @Override
    public void onEnable() {
        if (!paper()) {
            getLogger().severe("Stagecoach needs Paper (or a fork of it, like Purpur) 1.21.11: it uses Paper's dialog "
                    + "screens and resource-pack API, which Spigot doesn't have. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        items = new CoachItems(this);
        reloadSettings();
        state = NetworkState.load(getDataFolder().toPath().resolve("network.json"), getLogger());
        displays = new CoachDisplays(this);
        journeys = new JourneyManager(this);
        service = new StagecoachService(this);
        dialogs = new Dialogs(this);
        ghosts = new GhostPreview(this);
        coachListener = new CoachListener(this);
        pack = new ResourcePack(this);
        try {
            pack.start();
        } catch (Exception e) {
            getLogger().severe("Could not start the resource pack (" + e.getMessage() + "): players won't see the "
                    + "coaches. Is port " + settings.packPort() + " free? Change resourcePack.port or set resourcePack.url.");
        }

        getServer().getPluginManager().registerEvents(coachListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemGuardListener(items), this);
        getServer().getPluginManager().registerEvents(pack, this);
        PluginCommand command = getCommand("stagecoach");
        if (command != null) {
            StagecoachCommand executor = new StagecoachCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        // after /reload players are already online: pick up any journey a crash left behind
        for (Player p : getServer().getOnlinePlayers()) journeys.onJoin(p);
        getLogger().info("Stagecoach ready: " + state.coaches().size() + " stagecoaches on the network.");
    }

    private static boolean paper() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        // before the server saves and kicks players, so travellers are saved in their own mode
        if (journeys != null) journeys.endAll();
        if (ghosts != null) ghosts.removeAll();
        if (pack != null) pack.stop();
        if (items != null) items.unregisterRecipes();
        if (state != null) state.flush();
    }

    private void tick() {
        journeys.tick();
        if (ticks++ % 2 == 0) ghosts.tick();
    }

    /** Also re-registers the recipes; false if config.yml had problems. */
    public boolean reloadSettings() {
        reloadConfig();
        List<String> warnings = new ArrayList<>();
        settings = StagecoachConfig.from(getConfig(), warnings);
        fare = Fare.resolve(settings, warnings);
        for (String w : warnings) getLogger().warning("config.yml: " + w);
        messages = Messages.load(this);
        if (settings.craftable()) items.registerRecipes(messages);
        else items.unregisterRecipes();
        return warnings.isEmpty();
    }

    /** Main-world game time: it keeps counting across restarts and /time set doesn't touch it. */
    public long gameTime() {
        return getServer().getWorlds().get(0).getGameTime();
    }

    public StagecoachConfig settings() {
        return settings;
    }

    /** Null when journeys cost XP levels. */
    public Fare fare() {
        return fare;
    }

    public Messages messages() {
        return messages;
    }

    public NetworkState state() {
        return state;
    }

    public CoachItems items() {
        return items;
    }

    public CoachDisplays displays() {
        return displays;
    }

    public JourneyManager journeys() {
        return journeys;
    }

    public StagecoachService service() {
        return service;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public GhostPreview ghosts() {
        return ghosts;
    }

    public CoachListener coachListener() {
        return coachListener;
    }
}
