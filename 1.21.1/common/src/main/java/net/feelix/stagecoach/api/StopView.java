package net.feelix.stagecoach.api;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public record StopView(long id, String name, RegistryKey<World> dimension, BlockPos pos, Direction facing, String wood,
                       Optional<UUID> owner, boolean isPublic, boolean hidden, boolean locked, boolean free,
                       double costMultiplier, Optional<Identifier> scene, CreatedBy createdBy) {
}
