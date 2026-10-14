package net.feelix.stagecoach.forge;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class ForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** No fake-player class here: a player missing from the server's list is a machine or test. */
    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player;
    }

    /** A player over an in-memory channel, never added to the player list. */
    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player, CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(GameType.SURVIVAL); // a new player starts in the world's default mode
        return player;
    }

    @Override
    public CreativeModeTab.Builder itemGroupBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public BlockEntityType<StagecoachBlockEntity> blockEntityType(Set<Block> blocks) {
        return new BlockEntityType<>(StagecoachBlockEntity::new, blocks);
    }

    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Properties properties) {
        return new StagecoachItem(block, properties);
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
    public void sendToPlayer(ServerPlayer player, Identifier channel, FriendlyByteBuf buf) {
        if (isFakePlayer(player)) return; // no client behind it
        ForgeNetwork.sendToPlayer(player, channel, buf);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String node, boolean fallback) {
        return ForgePermissions.check(player, node, fallback);
    }
}
