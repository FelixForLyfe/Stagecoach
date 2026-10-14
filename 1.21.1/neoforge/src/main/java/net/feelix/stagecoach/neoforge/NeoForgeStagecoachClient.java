package net.feelix.stagecoach.neoforge;

import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ClientPlatform;
import net.feelix.stagecoach.client.LanternEmberParticle;
import net.feelix.stagecoach.client.PlacementPreview;
import net.feelix.stagecoach.client.RoadDustParticle;
import net.feelix.stagecoach.client.StagecoachClient;
import net.feelix.stagecoach.client.StagecoachRenderer;
import net.feelix.stagecoach.client.scene.SceneManager;
import net.feelix.stagecoach.client.screen.ClientConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Only reached on the physical client. */
final class NeoForgeStagecoachClient {
    private NeoForgeStagecoachClient() {}

    static void init(IEventBus modBus, ModContainer container) {
        ClientPlatform.install(new NeoForgeClientNetwork());
        StagecoachClient.init();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ClientConfigScreen(parent));
        modBus.addListener(NeoForgeStagecoachClient::onRegisterRenderers);
        modBus.addListener(NeoForgeStagecoachClient::onRegisterClientExtensions);
        modBus.addListener(NeoForgeStagecoachClient::onReloadListeners);
        modBus.addListener(NeoForgeStagecoachClient::onParticles);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onRenderLevel);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(StagecoachRegistry.BLOCK_ENTITY, ctx -> new StagecoachRenderer());
    }

    /** One 3D coach renderer per wood, made on first use. */
    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (StagecoachWood wood : StagecoachWood.values()) {
            event.registerItem(new IClientItemExtensions() {
                private BuiltinModelItemRenderer renderer;

                @Override
                public BuiltinModelItemRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new NeoForgeCoachItemRenderer(StagecoachRegistry.BLOCKS.get(wood));
                    return renderer;
                }
            }, StagecoachRegistry.ITEMS.get(wood));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientNet.onClientTick(client);
        PlacementPreview.update(client);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNet.onDisconnect();
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNet.onJoin();
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SceneManager.RELOADER);
    }

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(StagecoachRegistry.ROAD_DUST, RoadDustParticle.Factory::new);
        event.registerSpriteSet(StagecoachRegistry.LANTERN_EMBER, LanternEmberParticle.Factory::new);
    }

    /** After the world's block entities, like Fabric's BEFORE_DEBUG_RENDER. */
    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        VertexConsumerProvider.Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        PlacementPreview.render(event.getPoseStack(), event.getCamera().getPos(), event.getPartialTick().getTickDelta(true), buffers);
        buffers.draw();
    }
}
