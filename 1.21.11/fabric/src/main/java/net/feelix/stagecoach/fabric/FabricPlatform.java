package net.feelix.stagecoach.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.net.RawPayload;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.nio.file.Path;
import java.util.Set;

final class FabricPlatform implements Platform {
    private static final String PERMISSIONS_API = "fabric-permissions-api-v0";

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayer.get(level, profile);
    }

    @Override
    public CreativeModeTab.Builder itemGroupBuilder() {
        return FabricItemGroup.builder();
    }

    @Override
    public BlockEntityType<StagecoachBlockEntity> blockEntityType(Set<Block> blocks) {
        return FabricBlockEntityTypeBuilder.create(StagecoachBlockEntity::new, blocks.toArray(Block[]::new)).build();
    }

    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Properties settings) {
        return new StagecoachItem(block, settings);
    }

    @Override
    public void registerChannels(java.util.List<Identifier> toClient, java.util.List<Identifier> toServer) {
        toClient.forEach(c -> PayloadTypeRegistry.playS2C().register(RawPayload.id(c), RawPayload.codec(c)));
        toServer.forEach(c -> PayloadTypeRegistry.playC2S().register(RawPayload.id(c), RawPayload.codec(c)));
    }

    /** Fabric runs payload handlers on the server thread, so the receivers' execute runs inline. */
    @Override
    public void registerServerReceiver(Identifier channel, ServerReceiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(RawPayload.id(channel), (payload, context) ->
                receiver.receive(context.server(), context.player(), payload.buf()));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Identifier channel, FriendlyByteBuf buf) {
        ServerPlayNetworking.send(player, RawPayload.of(channel, buf));
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String node, boolean fallback) {
        return isModLoaded(PERMISSIONS_API) ? FabricPermissions.check(player, node, fallback) : fallback;
    }

}
