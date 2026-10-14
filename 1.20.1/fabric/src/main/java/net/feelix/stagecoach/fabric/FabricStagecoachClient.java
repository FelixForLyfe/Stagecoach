package net.feelix.stagecoach.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ClientPlatform;
import net.feelix.stagecoach.client.LanternEmberParticle;
import net.feelix.stagecoach.client.PlacementPreview;
import net.feelix.stagecoach.client.RoadDustParticle;
import net.feelix.stagecoach.client.StagecoachClient;
import net.feelix.stagecoach.client.StagecoachRenderer;
import net.feelix.stagecoach.client.scene.SceneManager;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FabricStagecoachClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(new ClientPlatform() {
            @Override
            public void registerClientReceiver(Identifier channel, ClientReceiver receiver) {
                ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, sender) -> receiver.receive(client, buf));
            }

            @Override
            public void sendToServer(Identifier channel, PacketByteBuf buf) {
                ClientPlayNetworking.send(channel, buf);
            }
        });
        StagecoachClient.init();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return SceneManager.ID;
            }

            @Override
            public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager, Profiler prepareProfiler,
                                                  Profiler applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
                return SceneManager.RELOADER.reload(synchronizer, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
            }
        });
        ParticleFactoryRegistry.getInstance().register(StagecoachRegistry.ROAD_DUST, RoadDustParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(StagecoachRegistry.LANTERN_EMBER, LanternEmberParticle.Factory::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNet.onJoin());

        BlockEntityRendererFactories.register(StagecoachRegistry.BLOCK_ENTITY, ctx -> new StagecoachRenderer());
        for (StagecoachBlock block : StagecoachRegistry.BLOCKS.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(block, new StagecoachItemRenderer(block));
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientNet.onClientTick(client);
            PlacementPreview.update(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientNet.onDisconnect());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ctx ->
                PlacementPreview.render(ctx.matrixStack(), ctx.camera().getPos(), ctx.tickDelta(), ctx.consumers()));
    }
}
