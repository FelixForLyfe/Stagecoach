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
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Only reached on the physical client; LevelRendererMixin draws the ghost. */
final class ForgeStagecoachClient {
    private ForgeStagecoachClient() {}

    static void init(FMLJavaModLoadingContext context) {
        ClientPlatform.install(new ForgeClientNetwork());
        StagecoachClient.init();
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ClientConfigScreen::new));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> event.registerBlockEntityRenderer(StagecoachRegistry.BLOCK_ENTITY, ctx -> {
            PlacementPreview.setRendererContext(ctx); // the ghost is built from the same context
            return new StagecoachRenderer(ctx);
        }));
        RegisterClientReloadListenersEvent.BUS.addListener(event -> event.registerReloadListener(SceneManager.RELOADER));
        RegisterParticleProvidersEvent.BUS.addListener(event -> {
            event.registerSpriteSet(StagecoachRegistry.ROAD_DUST, RoadDustParticle.Factory::new);
            event.registerSpriteSet(StagecoachRegistry.LANTERN_EMBER, LanternEmberParticle.Factory::new);
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            Minecraft client = Minecraft.getInstance();
            ClientNet.onClientTick(client);
            PlacementPreview.update(client);
        });
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> ClientNet.onJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientNet.onDisconnect());
    }
}
