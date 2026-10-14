package net.feelix.stagecoach.forge;

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
import net.minecraftforge.common.util.Result;
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
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** ServerStoppingEvent fires before the server saves and kicks players, like Fabric's SERVER_STOPPING. */
@Mod(Stagecoach.MOD_ID)
public final class ForgeStagecoach {
    public ForgeStagecoach(FMLJavaModLoadingContext context) {
        Platform.install(new ForgePlatform());
        BusGroup modBus = context.getModBusGroup();
        Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
        StagecoachRegistry.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void register(ResourceKey<? extends Registry<T>> key, Identifier id, Supplier<? extends T> factory) {
                DeferredRegister<T> register = (DeferredRegister<T>) registers.computeIfAbsent(key, k -> DeferredRegister.create(key, Stagecoach.MOD_ID));
                register.register(id.getPath(), factory);
            }
        });
        for (DeferredRegister<?> register : registers.values()) register.register(modBus);
        ForgeConfigCondition.register(modBus);
        Stagecoach.init();
        ForgeNetwork.init();

        // Forge reads the event's result, not cancellation
        BlockEvent.BreakEvent.BUS.addListener(Priority.NORMAL, event -> {
            if (event.getLevel() instanceof Level level && !Stagecoach.onBreakAttempt(level, event.getPlayer(), event.getPos(), event.getState())) {
                event.setResult(Result.DENY);
            }
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> JourneyManager.onServerTick(event.server()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) JourneyManager.onPlayerJoin(player);
        });
        ServerStoppingEvent.BUS.addListener(event -> JourneyManager.onServerStopping(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> {
            JourneyManager.onServerStopped();
            StructureInjections.onServerStopped();
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) JourneyManager.onRespawn(player, event.isEndConquered());
        });
        LivingAttackEvent.BUS.addListener(Priority.NORMAL, event -> { JourneyManager.onDamaged(event.getEntity(), event.getSource()); });
        RegisterCommandsEvent.BUS.addListener(event -> StagecoachCommands.register(event.getDispatcher()));
        AddReloadListenerEvent.BUS.addListener(event -> event.addListener(StagecoachData.RELOADER));
        // before the worlds are created, so even the spawn chunks generate with the coach stops
        ServerAboutToStartEvent.BUS.addListener(event -> StructureInjections.apply(event.getServer()));
        // a null player is the sync after /reload, when the data files have just been read again
        OnDatapackSyncEvent.BUS.addListener(event -> {
            if (event.getPlayer() == null) StructureInjections.apply(event.getPlayerList().getServer());
        });
        PermissionGatherEvent.Nodes.BUS.addListener(ForgePermissions::onGather);

        if (FMLEnvironment.dist.isClient()) ForgeStagecoachClient.init(context);
    }
}
