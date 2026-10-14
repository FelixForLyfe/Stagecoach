package net.feelix.stagecoach.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachCommands;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.platform.Registrar;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;

import java.util.function.Supplier;

public class FabricStagecoach implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatform());
        StagecoachRegistry.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void register(ResourceKey<? extends Registry<T>> key, Identifier id, Supplier<? extends T> factory) {
                Registry.register((Registry<T>) BuiltInRegistries.REGISTRY.getValue(key.identifier()), id, factory.get());
            }
        });
        Stagecoach.init();

        ResourceConditions.register(FabricConfigCondition.TYPE);
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(StagecoachData.ID, StagecoachData.RELOADER);

        // runs before anything breaks, for any cell of the coach
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> Stagecoach.onBreakAttempt(level, player, pos, state));
        ServerTickEvents.END_SERVER_TICK.register(JourneyManager::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> JourneyManager.onPlayerJoin(handler.player));
        // before the worlds are created, so even the spawn chunks generate with the injected pieces
        ServerLifecycleEvents.SERVER_STARTING.register(StructureInjections::apply);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> StructureInjections.apply(server));
        // before players are saved and kicked
        ServerLifecycleEvents.SERVER_STOPPING.register(JourneyManager::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JourneyManager.onServerStopped();
            StructureInjections.onServerStopped();
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> JourneyManager.onRespawn(newPlayer, alive));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            JourneyManager.onDamaged(entity, source);
            return true;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> StagecoachCommands.register(dispatcher));
    }
}
