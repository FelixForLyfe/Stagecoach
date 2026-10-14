package net.feelix.stagecoach.platform;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.List;

public interface Platform {
    /** Read {@code buf} on the network thread, then hop to the server thread for the work. */
    @FunctionalInterface
    interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf);
    }

    Path configDir();

    boolean isModLoaded(String modId);

    /** Machines and tests acting as players; they never travel. */
    boolean isFakePlayer(ServerPlayerEntity player);

    ServerPlayerEntity fakePlayer(ServerWorld world, GameProfile profile);

    ItemGroup.Builder itemGroupBuilder();

    /** Forge needs a subclass to attach the 3D item renderer. */
    StagecoachItem createItem(StagecoachBlock block, Item.Settings settings);

    /** Before any receiver is registered or packet sent (1.20.5+ payload types). */
    void registerChannels(List<Identifier> toClient, List<Identifier> toServer);

    void registerServerReceiver(Identifier channel, ServerReceiver receiver);

    void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf);

    /** Asks the loader's permission system; {@code fallback} is the answer without one. */
    boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback);

    /** Only called while GameStages is loaded. */
    default boolean hasGameStage(ServerPlayerEntity player, String stage) {
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
