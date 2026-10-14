package net.feelix.stagecoach.neoforge;

import com.mojang.serialization.MapCodec;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachCommands;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.platform.Registrar;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** ServerStoppingEvent fires before the server saves and kicks players, like Fabric's SERVER_STOPPING. */
@Mod(Stagecoach.MOD_ID)
public final class NeoForgeStagecoach {
    public NeoForgeStagecoach(IEventBus modBus, ModContainer container, Dist dist) {
        Platform.install(new NeoForgePlatform());
        Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
        StagecoachRegistry.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void register(ResourceKey<? extends Registry<T>> key, Identifier id, Supplier<? extends T> factory) {
                DeferredRegister<T> register = (DeferredRegister<T>) registers.computeIfAbsent(key, k -> DeferredRegister.create(key, Stagecoach.MOD_ID));
                register.register(id.getPath(), factory);
            }
        });
        DeferredRegister<MapCodec<? extends ICondition>> conditions = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Stagecoach.MOD_ID);
        conditions.register("builtin_advancements", () -> NeoForgeConfigCondition.CODEC);
        for (DeferredRegister<?> register : registers.values()) register.register(modBus);
        conditions.register(modBus);
        modBus.addListener(NeoForgePlatform::onRegisterPayloads);
        Stagecoach.init();

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(NeoForgeStagecoach::onBreak);
        bus.addListener(NeoForgeStagecoach::onServerTick);
        bus.addListener(NeoForgeStagecoach::onLogin);
        bus.addListener(NeoForgeStagecoach::onStopping);
        bus.addListener(NeoForgeStagecoach::onStopped);
        bus.addListener(NeoForgeStagecoach::onRespawn);
        bus.addListener(NeoForgeStagecoach::onDamage);
        bus.addListener(NeoForgeStagecoach::onCommands);
        bus.addListener(NeoForgeStagecoach::onReloadListeners);
        bus.addListener(NeoForgeStagecoach::onAboutToStart);
        bus.addListener(NeoForgeStagecoach::onDatapackSync);
        bus.addListener(NeoForgePermissions::onGather);

        if (dist.isClient()) NeoForgeStagecoachClient.init(modBus, container);
    }

    /** Before anything breaks, like Fabric's PlayerBlockBreakEvents.BEFORE. */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !Stagecoach.onBreakAttempt(level, event.getPlayer(), event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        JourneyManager.onServerTick(event.getServer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) JourneyManager.onPlayerJoin(player);
    }

    private static void onStopping(ServerStoppingEvent event) {
        JourneyManager.onServerStopping(event.getServer());
    }

    private static void onStopped(ServerStoppedEvent event) {
        JourneyManager.onServerStopped();
        StructureInjections.onServerStopped();
    }

    private static void onReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(StagecoachData.ID, StagecoachData.RELOADER);
    }

    /** Before the worlds are created, so even the spawn chunks generate with the coach stops. */
    private static void onAboutToStart(ServerAboutToStartEvent event) {
        StructureInjections.apply(event.getServer());
    }

    /** A null player is the sync after /reload, when the data files have just been read again. */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) StructureInjections.apply(event.getPlayerList().getServer());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) JourneyManager.onRespawn(player, event.isEndConquered());
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        JourneyManager.onDamaged(event.getEntity(), event.getSource());
    }

    private static void onCommands(RegisterCommandsEvent event) {
        StagecoachCommands.register(event.getDispatcher());
    }
}
