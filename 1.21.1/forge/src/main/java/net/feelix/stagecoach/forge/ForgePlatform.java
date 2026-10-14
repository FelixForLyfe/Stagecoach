package net.feelix.stagecoach.forge;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;

final class ForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** Forge 52 has no fake-player class: a player missing from the server's list is a machine or test. */
    @Override
    public boolean isFakePlayer(ServerPlayerEntity player) {
        return player.getServer() == null || player.getServer().getPlayerManager().getPlayer(player.getUuid()) != player;
    }

    /** A player over an in-memory channel, never added to the player list. */
    @Override
    public ServerPlayerEntity fakePlayer(ServerWorld world, GameProfile profile) {
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, SyncedClientOptions.createDefault());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.networkHandler = new ServerPlayNetworkHandler(world.getServer(), connection, player, ConnectedClientData.createDefault(profile, false));
        return player;
    }

    @Override
    public ItemGroup.Builder itemGroupBuilder() {
        return ItemGroup.builder();
    }

    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Settings settings) {
        return new ForgeStagecoachItem(block, settings);
    }

    /** One envelope type carries every message, so there's nothing to declare per channel. */
    @Override
    public void registerChannels(List<Identifier> toClient, List<Identifier> toServer) {
    }

    @Override
    public void registerServerReceiver(Identifier channel, ServerReceiver receiver) {
        ForgeNetwork.SERVER_RECEIVERS.put(channel, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        if (isFakePlayer(player)) return; // no client behind it
        ForgeNetwork.sendToPlayer(player, channel, buf);
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback) {
        return ForgePermissions.check(player, node, fallback);
    }
}
