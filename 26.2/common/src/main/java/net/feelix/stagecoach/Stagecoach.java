package net.feelix.stagecoach;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.net.ServerNet;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class Stagecoach {
    private Stagecoach() {}

    public static final String MOD_ID = "stagecoach";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String CONFIG_FILE = MOD_ID + ".json";
    public static final String CLIENT_CONFIG_FILE = MOD_ID + "-client.json";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** {@code text("arrived", name)} reads {@code stagecoach.arrived}. */
    public static MutableComponent text(String key, Object... args) {
        return Component.translatable(MOD_ID + "." + key, args);
    }

    public static void init() {
        reloadConfig();
        Platform.get().registerChannels(Packets.TO_CLIENT, Packets.TO_SERVER);
        ServerNet.init();
    }

    public static List<String> reloadConfig() {
        List<String> warnings = new ArrayList<>();
        StagecoachConfig.set(StagecoachConfig.load(Platform.get().configDir().resolve(CONFIG_FILE), warnings));
        for (String w : warnings) LOGGER.warn("config/{}: {}", CONFIG_FILE, w);
        return warnings;
    }

    /** Called before anything breaks, for any cell; false refuses after telling the player why. */
    public static boolean onBreakAttempt(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StagecoachBlock) && !(state.getBlock() instanceof StagecoachPartBlock)) return true;
        Component refusal = StagecoachService.breakRefusal(level, pos, state, player);
        if (refusal == null) return true;
        player.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
        return false;
    }
}
