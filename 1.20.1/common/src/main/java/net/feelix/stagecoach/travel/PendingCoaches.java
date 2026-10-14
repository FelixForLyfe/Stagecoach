package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.data.NamePool;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.Theme;
import net.feelix.stagecoach.data.TravelRule;
import net.feelix.stagecoach.net.ServerNet;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Coaches that joined a world without being placed by a player become stops here, one server tick later. */
public final class PendingCoaches {
    private PendingCoaches() {}

    private record Pending(RegistryKey<World> dimension, BlockPos pos) {}

    /** Filled from any thread a chunk may be finished on; drained on the server thread. */
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();

    public static final String TEMPLATE_NAME = StagecoachBlockEntity.TEMPLATE_PREFIX + "name";
    public static final String TEMPLATE_FLAGS = StagecoachBlockEntity.TEMPLATE_PREFIX + "flags";
    public static final String TEMPLATE_COST = StagecoachBlockEntity.TEMPLATE_PREFIX + "cost";
    public static final String TEMPLATE_SCENE = StagecoachBlockEntity.TEMPLATE_PREFIX + "scene";
    public static final String TEMPLATE_THEME = StagecoachBlockEntity.TEMPLATE_PREFIX + "theme";
    public static final String TEMPLATE_WOOD = StagecoachBlockEntity.TEMPLATE_PREFIX + "wood";

    public static void queue(World world, BlockPos pos) {
        QUEUE.add(new Pending(world.getRegistryKey(), pos.toImmutable()));
    }

    public static void onServerTick(MinecraftServer server) {
        Pending p;
        while ((p = QUEUE.poll()) != null) {
            ServerWorld world = server.getWorld(p.dimension);
            if (world != null && world.isChunkLoaded(p.pos)) check(world, p.pos);
        }
    }

    public static void onServerStopped() {
        QUEUE.clear();
    }

    /** Also used by right-click and the API, so a coach is never used before it's a stop. */
    public static @Nullable CoachRecord check(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof StagecoachBlockEntity be) || be.isRemoved() || be.isDecoration()) return null;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof StagecoachBlock block)) return null;
        StagecoachState network = StagecoachState.get(world.getServer());
        CoachRecord record = network.coach(be.getCoachId());
        if (record == null || !record.pos.equals(pos) || record.dimension != world.getRegistryKey()) {
            CoachRecord here = network.at(world.getRegistryKey(), pos);
            if (here == null) return register(world, pos, state, be, network);
            be.setCoachId(here.id);
            record = here;
        }
        if (record.wood == null) {
            record.wood = block.wood();
            network.changed();
        }
        return record;
    }

    private static @Nullable CoachRecord register(ServerWorld world, BlockPos pos, BlockState state, StagecoachBlockEntity be,
                                                  StagecoachState network) {
        StagecoachConfig cfg = StagecoachConfig.get();
        NbtCompound template = be.template() == null ? new NbtCompound() : be.template();
        String themeId = template.contains(TEMPLATE_THEME) ? template.getString(TEMPLATE_THEME) : null;
        boolean generated = themeId != null;
        if (cfg.blockedDimensions.contains(world.getRegistryKey().getValue().toString())) {
            if (generated) StagecoachStructure.removeCoach(world, pos, state);
            else be.makeDecoration();
            return null;
        }
        String structureKey = generated ? structureKey(world, pos) : null;
        if (structureKey != null && network.countInStructure(structureKey) >= cfg.maxGeneratedStopsPerStructure) {
            StagecoachStructure.removeCoach(world, pos, state);
            return null;
        }

        long seed = Landscape.hash(world.getSeed(), 21, pos.asLong());
        Theme theme = generated ? StagecoachData.theme(themeId) : null;
        StagecoachWood wood = ((StagecoachBlock) state.getBlock()).wood();
        StagecoachWood picked = template.contains(TEMPLATE_WOOD) ? StagecoachWood.byId(template.getString(TEMPLATE_WOOD))
                : theme != null ? StagecoachWood.byId(theme.pickWood(seed)) : null;
        if (picked != null) wood = picked;
        String name = template.contains(TEMPLATE_NAME) ? CoachNames.clean(template.getString(TEMPLATE_NAME), cfg.maxNameLength) : null;
        if (name == null && theme != null && theme.namePool() != null) name = poolName(theme.namePool(), seed, cfg.maxNameLength);
        if (name == null) name = autoName(world, pos, cfg);
        name = network.uniqueName(name, cfg.maxNameLength);

        CoachRecord record = network.register(pos, world.getRegistryKey(), state.get(StagecoachBlock.FACING), wood, null,
                CreatedBy.STRUCTURE, name);
        for (NbtElement flag : template.getList(TEMPLATE_FLAGS, NbtElement.STRING_TYPE)) {
            switch (flag.asString()) {
                case "public" -> record.isPublic = true;
                case "hidden" -> record.hidden = true;
                case "locked" -> record.locked = true;
                case "free" -> record.free = true;
                default -> Stagecoach.LOGGER.warn("Coach at {} has an unknown template flag '{}' (use {})", pos, flag.asString(), TravelRule.FLAGS);
            }
        }
        if (template.contains(TEMPLATE_COST)) record.costMultiplier = Math.max(0, template.getDouble(TEMPLATE_COST));
        if (template.contains(TEMPLATE_SCENE)) record.scene = Identifier.tryParse(template.getString(TEMPLATE_SCENE));
        if (generated && !cfg.generatedCoachesBreakable) record.locked = true;
        if (structureKey != null) network.setStructureKey(record, structureKey);

        StagecoachBlockEntity target = StagecoachStructure.rebuild(world, pos, state, wood);
        target.setCoachId(record.id);
        target.setDropsNothing(true);
        target.clearTemplate();
        network.changed();
        ServerNet.knownStopsChanged();
        Stagecoach.LOGGER.debug("Registered stop #{} '{}' at {} in {}", record.id, record.name, pos, world.getRegistryKey().getValue());
        return record;
    }

    private static @Nullable String poolName(String poolId, long seed, int maxLength) {
        NamePool pool = StagecoachData.namePool(poolId);
        if (pool == null) return null;
        for (int attempt = 0; attempt < 8; attempt++) {
            String name = CoachNames.clean(pool.generate(seed, attempt), maxLength);
            if (name != null) return name;
        }
        return null;
    }

    private static String autoName(ServerWorld world, BlockPos pos, StagecoachConfig cfg) {
        String name = cfg.autoNameFormat.replace("{biome}", biomeName(world.getBiome(pos)));
        String clean = CoachNames.clean(name, cfg.maxNameLength);
        return clean != null ? clean : CoachNames.defaultName(StagecoachState.get(world.getServer()).coaches().size() + 1);
    }

    /** The server's English name for it when it has one, else the id's path in title case. */
    static String biomeName(RegistryEntry<Biome> biome) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (key.isEmpty()) return "Wild";
        Identifier id = key.get().getValue();
        String fallback = titleCase(id.getPath());
        return Language.getInstance().get("biome." + id.getNamespace() + "." + id.getPath(), fallback);
    }

    static String titleCase(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }

    /** The settlement a generated coach stands in: dimension, structure id and its start chunk. */
    static @Nullable String structureKey(ServerWorld world, BlockPos pos) {
        StructureAccessor structures = world.getStructureAccessor();
        for (Map.Entry<Structure, LongSet> e : structures.getStructureReferences(pos).entrySet()) {
            StructureStart start = structures.getStructureAt(pos, e.getKey());
            if (!start.hasChildren()) continue;
            Identifier id = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(e.getKey());
            return world.getRegistryKey().getValue() + "|" + id + "|" + start.getPos().x + "," + start.getPos().z;
        }
        return null;
    }
}
