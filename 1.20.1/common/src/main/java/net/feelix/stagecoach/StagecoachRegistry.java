package net.feelix.stagecoach;

import net.feelix.stagecoach.advancement.StagecoachTriggers;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.platform.Registrar;
import net.feelix.stagecoach.worldgen.CoachStopProcessor;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public final class StagecoachRegistry {
    private StagecoachRegistry() {}

    public static final Map<StagecoachWood, StagecoachBlock> BLOCKS = new EnumMap<>(StagecoachWood.class);
    public static final Map<StagecoachWood, StagecoachItem> ITEMS = new EnumMap<>(StagecoachWood.class);
    public static StagecoachPartBlock PART;
    public static BlockEntityType<StagecoachBlockEntity> BLOCK_ENTITY;
    // DefaultParticleType's constructor is protected; an empty subclass is the vanilla-only way to make one
    public static final DefaultParticleType ROAD_DUST = new DefaultParticleType(false) {};
    public static final DefaultParticleType LANTERN_EMBER = new DefaultParticleType(false) {};

    /** Loaders call these in this order: blocks, items, block entity type, tab. */
    public static void register(Registrar r) {
        for (StagecoachWood wood : StagecoachWood.values()) {
            r.register(RegistryKeys.BLOCK, Stagecoach.id(wood.blockId()), () -> {
                StagecoachBlock block = new StagecoachBlock(wood, settings(wood));
                BLOCKS.put(wood, block);
                return block;
            });
        }
        // random ticks only drive the orphan clean-up in StagecoachPartBlock#randomTick
        r.register(RegistryKeys.BLOCK, Stagecoach.id("stagecoach_part"),
                () -> PART = new StagecoachPartBlock(settings(StagecoachWood.OAK).dropsNothing().ticksRandomly()));

        for (StagecoachWood wood : StagecoachWood.values()) {
            r.register(RegistryKeys.ITEM, Stagecoach.id(wood.blockId()), () -> {
                StagecoachItem item = Platform.get().createItem(BLOCKS.get(wood), new Item.Settings());
                ITEMS.put(wood, item);
                return item;
            });
        }
        r.register(RegistryKeys.BLOCK_ENTITY_TYPE, Stagecoach.id("stagecoach"), () -> BLOCK_ENTITY =
                BlockEntityType.Builder.create(StagecoachBlockEntity::new, BLOCKS.values().toArray(new Block[0])).build(null));
        r.register(RegistryKeys.ITEM_GROUP, Stagecoach.id("stagecoaches"), StagecoachRegistry::itemGroup);
        StagecoachSounds.register(r);
        for (Identifier stat : StagecoachTriggers.STATS) r.register(RegistryKeys.CUSTOM_STAT, stat, () -> stat);
        r.register(RegistryKeys.STRUCTURE_PROCESSOR, Stagecoach.id("coach_stop"), () -> CoachStopProcessor.TYPE);
        r.register(RegistryKeys.PARTICLE_TYPE, Stagecoach.id("road_dust"), () -> ROAD_DUST);
        r.register(RegistryKeys.PARTICLE_TYPE, Stagecoach.id("lantern_ember"), () -> LANTERN_EMBER);
    }

    private static ItemGroup itemGroup() {
        return Platform.get().itemGroupBuilder()
                .displayName(Stagecoach.text("item_group"))
                .icon(() -> new ItemStack(ITEMS.get(StagecoachWood.OAK)))
                .entries((context, entries) -> {
                    for (StagecoachItem item : ITEMS.values()) entries.add(item);
                })
                .build();
    }

    /** Forced solid: vanilla lets fluids wash away a block with a small shape. */
    private static AbstractBlock.Settings settings(StagecoachWood wood) {
        Block planks = wood.planks();
        return AbstractBlock.Settings.create()
                .mapColor(planks.getDefaultMapColor())
                .strength(2.5f)
                .sounds(planks.getDefaultState().getSoundGroup())
                .nonOpaque()
                .solid()
                .pistonBehavior(PistonBehavior.BLOCK)
                .allowsSpawning((state, world, pos, type) -> false)
                .solidBlock((state, world, pos) -> false)
                .suffocates((state, world, pos) -> false)
                .blockVision((state, world, pos) -> false);
    }
}
