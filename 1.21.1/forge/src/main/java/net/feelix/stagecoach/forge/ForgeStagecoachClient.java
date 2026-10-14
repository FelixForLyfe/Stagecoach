package net.feelix.stagecoach.forge;

import net.feelix.stagecoach.StagecoachRegistry;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Only reached on the physical client. */
final class ForgeStagecoachClient {
    private ForgeStagecoachClient() {}

    // the model-view matrix already holds the camera's turn here, so start from identity
    private static final MatrixStack WORLD_POSE = new MatrixStack();

    static void init(FMLJavaModLoadingContext context) {
        ClientPlatform.install(new ForgeClientNetwork());
        StagecoachClient.init();
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ClientConfigScreen::new));
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(ForgeStagecoachClient::onRegisterRenderers);
        modBus.addListener(ForgeStagecoachClient::onReloadListeners);
        modBus.addListener(ForgeStagecoachClient::onParticles);
        MinecraftForge.EVENT_BUS.addListener(ForgeStagecoachClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeStagecoachClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeStagecoachClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ForgeStagecoachClient::onRenderLevel);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(StagecoachRegistry.BLOCK_ENTITY, ctx -> new StagecoachRenderer());
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
        PlacementPreview.render(WORLD_POSE, event.getCamera().getPos(), event.getPartialTick(), buffers);
        buffers.draw();
    }
}
