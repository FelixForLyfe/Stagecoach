package net.feelix.stagecoach.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.net.RawPayload;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

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
    public boolean isFakePlayer(ServerPlayerEntity player) {
        return player instanceof FakePlayer;
    }

    @Override
    public ServerPlayerEntity fakePlayer(ServerWorld world, GameProfile profile) {
        return FakePlayer.get(world, profile);
    }

    @Override
    public ItemGroup.Builder itemGroupBuilder() {
        return FabricItemGroup.builder();
    }

    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Settings settings) {
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
    public void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, RawPayload.of(channel, buf));
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback) {
        return isModLoaded(PERMISSIONS_API) ? FabricPermissions.check(player, node, fallback) : fallback;
    }

}
