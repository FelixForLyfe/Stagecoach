package net.feelix.stagecoach;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

public enum StagecoachWood implements StringIdentifiable {
    OAK("oak", Blocks.OAK_PLANKS),
    SPRUCE("spruce", Blocks.SPRUCE_PLANKS),
    BIRCH("birch", Blocks.BIRCH_PLANKS),
    JUNGLE("jungle", Blocks.JUNGLE_PLANKS),
    ACACIA("acacia", Blocks.ACACIA_PLANKS),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_PLANKS),
    MANGROVE("mangrove", Blocks.MANGROVE_PLANKS),
    CHERRY("cherry", Blocks.CHERRY_PLANKS),
    BAMBOO("bamboo", Blocks.BAMBOO_PLANKS),
    CRIMSON("crimson", Blocks.CRIMSON_PLANKS),
    WARPED("warped", Blocks.WARPED_PLANKS);

    private final String id;
    private final Block planks;

    StagecoachWood(String id, Block planks) {
        this.id = id;
        this.planks = planks;
    }

    public static @Nullable StagecoachWood byId(String id) {
        for (StagecoachWood wood : values()) if (wood.id.equals(id)) return wood;
        return null;
    }

    public String blockId() {
        return id + "_stagecoach";
    }

    /** The vanilla planks this variant borrows its map colour and sounds from. */
    public Block planks() {
        return planks;
    }

    @Override
    public String asString() {
        return id;
    }
}
