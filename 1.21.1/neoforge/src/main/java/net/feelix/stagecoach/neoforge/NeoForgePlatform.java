package net.feelix.stagecoach.neoforge;

import com.mojang.authlib.GameProfile;
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

    /** The 3D renderer comes from RegisterClientExtensionsEvent, so the plain item will do. */
    @Override
    public StagecoachItem createItem(StagecoachBlock block, Item.Settings settings) {
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

    /** NeoForge throws on a connection without the channel, such as a GameTest mock player. */
    @Override
    public void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        if (player instanceof FakePlayer) return; // no client behind it (Fabric's fake players drop packets too)
        RawPayload payload = RawPayload.of(channel, buf);
        if (player.networkHandler.hasChannel(payload.getId())) PacketDistributor.sendToPlayer(player, payload);
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
                if (receiver != null && context.player() instanceof ServerPlayerEntity player) {
                    receiver.receive(player.getServer(), player, payload.buf());
                }
            });
        }
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node, boolean fallback) {
        return NeoForgePermissions.check(player, node, fallback);
    }
}
