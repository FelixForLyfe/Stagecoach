package net.feelix.stagecoach.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.advancement.criterion.Criterion;
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
    public void registerServerReceiver(Identifier channel, ServerReceiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(channel, (server, player, handler, buf, sender) ->
                receiver.receive(server, player, buf));
    }

    @Override
    public void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, channel, buf);
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback) {
        return isModLoaded(PERMISSIONS_API) ? FabricPermissions.check(player, node, fallback) : fallback;
    }

    @Override
    public void registerCriterion(Criterion<?> criterion) {
        CriterionRegistry.register(criterion);
    }
}
