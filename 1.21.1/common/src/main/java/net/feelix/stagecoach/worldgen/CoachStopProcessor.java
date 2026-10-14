package net.feelix.stagecoach.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.travel.PendingCoaches;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** Writes the theme and wood into the coach's template data and turns the piece's oak decor into the palette's wood. */
public final class CoachStopProcessor extends StructureProcessor {
    public static final MapCodec<CoachStopProcessor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("theme").forGetter(p -> p.theme),
            Codec.STRING.optionalFieldOf("palette", "").forGetter(p -> p.palette),
            Codec.STRING.optionalFieldOf("wood", "").forGetter(p -> p.wood)
    ).apply(i, CoachStopProcessor::new));
    public static final StructureProcessorType<CoachStopProcessor> TYPE = () -> CODEC;

    private static final String OAK = "oak_";

    private final String theme;
    private final String palette;
    private final String wood;

    public CoachStopProcessor(String theme, String palette, String wood) {
        this.theme = theme;
        this.palette = palette;
        this.wood = wood;
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot,
                                                                StructureTemplate.StructureBlockInfo original,
                                                                StructureTemplate.StructureBlockInfo current, StructurePlacementData data) {
        BlockState state = current.state();
        if (state.getBlock() instanceof StagecoachBlock) {
            NbtCompound nbt = current.nbt() == null ? new NbtCompound() : current.nbt().copy();
            nbt.putString(PendingCoaches.TEMPLATE_THEME, theme);
            if (!wood.isEmpty()) nbt.putString(PendingCoaches.TEMPLATE_WOOD, wood);
            nbt.remove("CoachId");
            return new StructureTemplate.StructureBlockInfo(current.pos(), state, nbt);
        }
        if (palette.isEmpty() || palette.equals("oak")) return current;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !id.getPath().startsWith(OAK)) return current;
        Identifier swappedId = Identifier.of(Identifier.DEFAULT_NAMESPACE, palette + "_" + id.getPath().substring(OAK.length()));
        Block target = Registries.BLOCK.getOrEmpty(swappedId).orElse(null);
        if (target == null) return current;
        BlockState swapped = target.getDefaultState();
        for (Property<?> p : state.getProperties()) swapped = copy(swapped, p, state);
        return new StructureTemplate.StructureBlockInfo(current.pos(), swapped, current.nbt());
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState to, Property<T> property, BlockState from) {
        return to.contains(property) ? to.with(property, from.get(property)) : to;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE;
    }
}
