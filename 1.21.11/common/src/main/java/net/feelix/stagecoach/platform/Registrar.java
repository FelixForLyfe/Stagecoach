package net.feelix.stagecoach.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/** Fabric calls the factory at once; Forge and NeoForge hand it to a DeferredRegister. */
@FunctionalInterface
public interface Registrar {
    <T> void register(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<? extends T> factory);
}
