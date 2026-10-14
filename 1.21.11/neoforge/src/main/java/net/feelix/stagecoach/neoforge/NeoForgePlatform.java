package net.feelix.stagecoach.neoforge;

import com.mojang.authlib.GameProfile;
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
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class NeoForgePlatform implements Platform {
    private static final String PROTOCOL = "3";
    private static List<Identifier> toClient = List.of();
    private static List<Identifier> toServer = List.of();
    private static final Map<Identifier, ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayerFactory.get(level, profile);
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
    public StagecoachItem createItem(StagecoachBlock block, Item.Properties settings) {
        return new StagecoachItem(block, settings);
    }

    @Override
    public void registerChannels(List<Identifier> toClient, List<Identifier> toServer) {
        NeoForgePlatform.toClient = List.copyOf(toClient);
        NeoForgePlatform.toServer = List.copyOf(toServer);
    }

    @Override
    public void registerServerReceiver(Identifier channel, ServerReceiver receiver) {
        SERVER_RECEIVERS.put(channel, receiver);
    }

    /** NeoForge throws on a connection without the channel, such as a fake or GameTest mock player. */
    @Override
    public void sendToPlayer(ServerPlayer player, Identifier channel, FriendlyByteBuf buf) {
        if (player instanceof FakePlayer) return;
        RawPayload payload = RawPayload.of(channel, buf);
        if (player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    /** Handlers run on the game thread, so the receivers' execute() runs inline. */
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        for (Identifier channel : toClient) {
            registrar.playToClient(RawPayload.id(channel), RawPayload.codec(channel),
                    (payload, context) -> NeoForgeClientNetwork.receive(payload));
        }
        for (Identifier channel : toServer) {
            registrar.playToServer(RawPayload.id(channel), RawPayload.codec(channel), (payload, context) -> {
                ServerReceiver receiver = SERVER_RECEIVERS.get(payload.channel());
                if (receiver != null && context.player() instanceof ServerPlayer player) {
                    receiver.receive(player.level().getServer(), player, payload.buf());
                }
            });
        }
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String node, boolean fallback) {
        return NeoForgePermissions.check(player, node, fallback);
    }
}
