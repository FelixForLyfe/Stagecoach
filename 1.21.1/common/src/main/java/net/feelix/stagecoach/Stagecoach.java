package net.feelix.stagecoach;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.net.ServerNet;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
        return Identifier.of(MOD_ID, path);
    }

    /** {@code text("arrived", name)} reads {@code stagecoach.arrived}. */
    public static MutableText text(String key, Object... args) {
        return Text.translatable(MOD_ID + "." + key, args);
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
    public static boolean onBreakAttempt(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StagecoachBlock) && !(state.getBlock() instanceof StagecoachPartBlock)) return true;
        Text refusal = StagecoachService.breakRefusal(world, pos, state, player);
        if (refusal == null) return true;
        player.sendMessage(refusal.copy().formatted(Formatting.RED), true);
        return false;
    }
}
