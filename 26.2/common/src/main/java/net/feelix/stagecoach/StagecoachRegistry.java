package net.feelix.stagecoach;

import net.feelix.stagecoach.advancement.StagecoachCriterion;
import net.feelix.stagecoach.advancement.StagecoachTriggers;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.platform.Platform;
import net.feelix.stagecoach.platform.Registrar;
import net.feelix.stagecoach.worldgen.CoachStopProcessor;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class StagecoachRegistry {
    private StagecoachRegistry() {}

    public static final Map<StagecoachWood, StagecoachBlock> BLOCKS = new EnumMap<>(StagecoachWood.class);
    public static final Map<StagecoachWood, StagecoachItem> ITEMS = new EnumMap<>(StagecoachWood.class);
    public static StagecoachPartBlock PART;
    public static BlockEntityType<StagecoachBlockEntity> BLOCK_ENTITY;
    // SimpleParticleType's constructor is protected; an empty subclass is the vanilla-only way to make one
    public static final SimpleParticleType ROAD_DUST = new SimpleParticleType(false) {};
    public static final SimpleParticleType LANTERN_EMBER = new SimpleParticleType(false) {};

    /** Loaders call these in this order: blocks, items, block entity type, tab. */
    public static void register(Registrar r) {
        for (StagecoachWood wood : StagecoachWood.values()) {
            r.register(Registries.BLOCK, Stagecoach.id(wood.blockId()), () -> {
                StagecoachBlock block = new StagecoachBlock(wood, properties(wood).setId(blockKey(wood.blockId())));
                BLOCKS.put(wood, block);
                return block;
            });
        }
        // random ticks only drive the orphan clean-up in StagecoachPartBlock#randomTick
        r.register(Registries.BLOCK, Stagecoach.id("stagecoach_part"), () -> PART =
                new StagecoachPartBlock(properties(StagecoachWood.OAK).noLootTable().randomTicks().setId(blockKey("stagecoach_part"))));

        for (StagecoachWood wood : StagecoachWood.values()) {
            r.register(Registries.ITEM, Stagecoach.id(wood.blockId()), () -> {
                StagecoachItem item = Platform.get().createItem(BLOCKS.get(wood), new Item.Properties().useBlockDescriptionPrefix()
                        .setId(ResourceKey.create(Registries.ITEM, Stagecoach.id(wood.blockId()))));
                ITEMS.put(wood, item);
                return item;
            });
        }
        r.register(Registries.BLOCK_ENTITY_TYPE, Stagecoach.id("stagecoach"), () -> BLOCK_ENTITY =
                new BlockEntityType<>(StagecoachBlockEntity::new, Set.copyOf(BLOCKS.values())));
        r.register(Registries.CREATIVE_MODE_TAB, Stagecoach.id("stagecoaches"), StagecoachRegistry::creativeTab);
        StagecoachSounds.register(r);
        for (Identifier stat : StagecoachTriggers.STATS) r.register(Registries.CUSTOM_STAT, stat, () -> stat);
        for (StagecoachCriterion c : StagecoachTriggers.CRITERIA) r.register(Registries.TRIGGER_TYPE, Stagecoach.id(c.name()), () -> c);
        r.register(Registries.STRUCTURE_PROCESSOR, Stagecoach.id("coach_stop"), () -> CoachStopProcessor.CODEC);
        r.register(Registries.PARTICLE_TYPE, Stagecoach.id("road_dust"), () -> ROAD_DUST);
        r.register(Registries.PARTICLE_TYPE, Stagecoach.id("lantern_ember"), () -> LANTERN_EMBER);
    }

    private static CreativeModeTab creativeTab() {
        return Platform.get().itemGroupBuilder()
                .title(Stagecoach.text("item_group"))
                .icon(() -> new ItemStack(ITEMS.get(StagecoachWood.OAK)))
                .displayItems((context, entries) -> {
                    for (StagecoachItem item : ITEMS.values()) entries.accept(item);
                })
                .build();
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, Stagecoach.id(path));
    }

    /** Forced solid: vanilla lets fluids wash away a block with a small shape. */
    private static BlockBehaviour.Properties properties(StagecoachWood wood) {
        Block planks = wood.planks();
        return BlockBehaviour.Properties.of()
                .mapColor(planks.defaultMapColor())
                .strength(2.5f)
                .sound(planks.defaultBlockState().getSoundType())
                .noOcclusion()
                .forceSolidOn()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }
}
