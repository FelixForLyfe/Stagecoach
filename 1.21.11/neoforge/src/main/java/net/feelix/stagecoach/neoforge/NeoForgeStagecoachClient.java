package net.feelix.stagecoach.neoforge;

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
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
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
        modBus.addListener(NeoForgeStagecoachClient::onReloadListeners);
        modBus.addListener(NeoForgeStagecoachClient::onParticles);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeStagecoachClient::onLoggingOut);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(StagecoachRegistry.BLOCK_ENTITY, ctx -> {
            PlacementPreview.setRendererContext(ctx); // the ghost is built from the same context
            return new StagecoachRenderer(ctx);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientNet.onClientTick(client);
        PlacementPreview.update(client);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNet.onDisconnect();
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNet.onJoin();
    }

    private static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(SceneManager.ID, SceneManager.RELOADER);
    }

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(StagecoachRegistry.ROAD_DUST, RoadDustParticle.Factory::new);
        event.registerSpriteSet(StagecoachRegistry.LANTERN_EMBER, LanternEmberParticle.Factory::new);
    }
}
