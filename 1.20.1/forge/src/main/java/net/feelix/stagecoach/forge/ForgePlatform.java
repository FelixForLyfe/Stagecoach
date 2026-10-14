package net.feelix.stagecoach.forge;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

final class ForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isFakePlayer(ServerPlayerEntity player) {
        return player instanceof FakePlayer;
    }

    @Override
    public ServerPlayerEntity fakePlayer(ServerWorld world, GameProfile profile) {
        return FakePlayerFactory.get(world, profile);
    }

    @Override
    public ItemGroup.Builder itemGroupBuilder() {
        return ItemGroup.builder();
    }

    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Settings settings) {
        return new ForgeStagecoachItem(block, settings);
    }

    @Override
    public void registerServerReceiver(Identifier channel, ServerReceiver receiver) {
        ForgeNetwork.SERVER_RECEIVERS.put(channel, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ForgeNetwork.sendToPlayer(player, channel, buf);
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback) {
        return ForgePermissions.check(player, node, fallback);
    }

    @Override
    public void registerCriterion(Criterion<?> criterion) {
        Criteria.register(criterion);
    }

    @Override
    public boolean hasGameStage(ServerPlayerEntity player, String stage) {
        return ForgeGameStages.has(player, stage);
    }
}
