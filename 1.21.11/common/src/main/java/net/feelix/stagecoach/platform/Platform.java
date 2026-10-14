package net.feelix.stagecoach.platform;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Platform {
    /** Read {@code buf} on the network thread, then hop to the server thread for the work. */
    @FunctionalInterface
    interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf);
    }

    Path configDir();

    boolean isModLoaded(String modId);

    /** Machines and tests acting as players; they never travel. */
    boolean isFakePlayer(ServerPlayer player);

    ServerPlayer fakePlayer(ServerLevel level, GameProfile profile);

    CreativeModeTab.Builder itemGroupBuilder();

    /** 1.21.11 keeps BlockEntityType's constructor private; each loader has its own way to make one. */
    BlockEntityType<StagecoachBlockEntity> blockEntityType(Set<Block> blocks);

    /** Forge needs a subclass to attach the 3D item renderer. */
    StagecoachItem createItem(StagecoachBlock block, Item.Properties settings);

    /** Before any receiver is registered or packet sent (1.20.5+ payload types). */
    void registerChannels(List<Identifier> toClient, List<Identifier> toServer);

    void registerServerReceiver(Identifier channel, ServerReceiver receiver);

    void sendToPlayer(ServerPlayer player, Identifier channel, FriendlyByteBuf buf);

    /** Asks the loader's permission system; {@code fallback} is the answer without one. */
    boolean hasPermission(ServerPlayer player, String node, boolean fallback);

    /** Only called while GameStages is loaded. */
    default boolean hasGameStage(ServerPlayer player, String stage) {
        return false;
    }

    final class Holder {
        private Holder() {}

        private static volatile Platform instance;
    }

    static Platform get() {
        Platform p = Holder.instance;
        if (p == null) throw new IllegalStateException("Stagecoach: no platform installed (the loader entrypoint has not run)");
        return p;
    }

    static void install(Platform platform) {
        Holder.instance = platform;
    }
}
