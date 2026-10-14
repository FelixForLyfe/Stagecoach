package net.feelix.stagecoach.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public record StopView(long id, String name, ResourceKey<Level> dimension, BlockPos pos, Direction facing, String wood,
                       Optional<UUID> owner, boolean isPublic, boolean hidden, boolean locked, boolean free,
                       double costMultiplier, Optional<Identifier> scene, CreatedBy createdBy) {
}
