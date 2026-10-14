package net.feelix.stagecoach.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ClientPlatform;
import net.feelix.stagecoach.client.LanternEmberParticle;
import net.feelix.stagecoach.client.PlacementPreview;
import net.feelix.stagecoach.client.RoadDustParticle;
import net.feelix.stagecoach.client.StagecoachClient;
import net.feelix.stagecoach.client.StagecoachRenderer;
import net.feelix.stagecoach.client.scene.SceneManager;
import net.feelix.stagecoach.net.RawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class FabricStagecoachClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(new ClientPlatform() {
            @Override
            public void registerClientReceiver(Identifier channel, ClientReceiver receiver) {
                ClientPlayNetworking.registerGlobalReceiver(RawPayload.id(channel), (payload, context) ->
                        receiver.receive(context.client(), payload.buf()));
            }

            @Override
            public void sendToServer(Identifier channel, FriendlyByteBuf buf) {
                ClientPlayNetworking.send(RawPayload.of(channel, buf));
            }
        });
        StagecoachClient.init();

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SceneManager.ID, SceneManager.RELOADER);
        ParticleProviderRegistry.getInstance().register(StagecoachRegistry.ROAD_DUST, RoadDustParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(StagecoachRegistry.LANTERN_EMBER, LanternEmberParticle.Factory::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNet.onJoin());

        BlockEntityRenderers.register(StagecoachRegistry.BLOCK_ENTITY, ctx -> {
            PlacementPreview.setRendererContext(ctx); // the ghost is built from the same context
            return new StagecoachRenderer(ctx);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientNet.onClientTick(client);
            PlacementPreview.update(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientNet.onDisconnect());
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> PlacementPreview.submit(ctx.poseStack(), ctx.submitNodeCollector(),
                ctx.levelState().cameraRenderState, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }
}
