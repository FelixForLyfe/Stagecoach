package net.feelix.stagecoach.forge;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachCommands;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.platform.Registrar;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** ServerStoppingEvent fires before the server saves and kicks players, like Fabric's SERVER_STOPPING. */
@Mod(Stagecoach.MOD_ID)
public final class ForgeStagecoach {
    public ForgeStagecoach(FMLJavaModLoadingContext context) {
        Platform.install(new ForgePlatform());
        IEventBus modBus = context.getModEventBus();
        Map<RegistryKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
        StagecoachRegistry.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void register(RegistryKey<? extends Registry<T>> key, Identifier id, Supplier<? extends T> factory) {
                DeferredRegister<T> register = (DeferredRegister<T>) registers.computeIfAbsent(key, k -> DeferredRegister.create(key, Stagecoach.MOD_ID));
                register.register(id.getPath(), factory);
            }
        });
        for (DeferredRegister<?> register : registers.values()) register.register(modBus);
        ForgeConfigCondition.register(modBus);
        Stagecoach.init();
        ForgeNetwork.init();

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(ForgeStagecoach::onBreak);
        bus.addListener(ForgeStagecoach::onServerTick);
        bus.addListener(ForgeStagecoach::onLogin);
        bus.addListener(ForgeStagecoach::onStopping);
        bus.addListener(ForgeStagecoach::onStopped);
        bus.addListener(ForgeStagecoach::onRespawn);
        bus.addListener(ForgeStagecoach::onAttacked);
        bus.addListener(ForgeStagecoach::onCommands);
        bus.addListener(ForgeStagecoach::onReloadListeners);
        bus.addListener(ForgeStagecoach::onAboutToStart);
        bus.addListener(ForgeStagecoach::onDatapackSync);
        bus.addListener(ForgePermissions::onGather);

        if (FMLEnvironment.dist == Dist.CLIENT) ForgeStagecoachClient.init(context);
    }

    /** Fired in tryBreakBlock before anything breaks, like Fabric's BEFORE. */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof World world && !Stagecoach.onBreakAttempt(world, event.getPlayer(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        JourneyManager.onServerTick(event.getServer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) JourneyManager.onPlayerJoin(player);
    }

    private static void onStopping(ServerStoppingEvent event) {
        JourneyManager.onServerStopping(event.getServer());
    }

    private static void onStopped(ServerStoppedEvent event) {
        JourneyManager.onServerStopped();
        StructureInjections.onServerStopped();
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(StagecoachData.RELOADER);
    }

    /** Before the worlds are created, so even the spawn chunks generate with the injected pieces. */
    private static void onAboutToStart(ServerAboutToStartEvent event) {
        StructureInjections.apply(event.getServer());
    }

    /** A null player is the sync after /reload, when the data files have just been read again. */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) StructureInjections.apply(event.getPlayerList().getServer());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) JourneyManager.onRespawn(player, event.isEndConquered());
    }

    private static void onAttacked(LivingAttackEvent event) {
        JourneyManager.onDamaged(event.getEntity(), event.getSource());
    }

    private static void onCommands(RegisterCommandsEvent event) {
        StagecoachCommands.register(event.getDispatcher());
    }
}
