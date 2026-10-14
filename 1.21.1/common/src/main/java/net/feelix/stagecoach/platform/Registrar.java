package net.feelix.stagecoach.platform;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/** Fabric calls the factory at once; Forge and NeoForge hand it to a DeferredRegister. */
@FunctionalInterface
public interface Registrar {
    <T> void register(RegistryKey<? extends Registry<T>> registry, Identifier id, Supplier<? extends T> factory);
}
