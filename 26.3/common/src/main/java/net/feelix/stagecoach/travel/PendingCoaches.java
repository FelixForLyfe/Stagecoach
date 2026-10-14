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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Coaches that joined a world without being placed by a player become stops here, one server tick later. */
public final class PendingCoaches {
    private PendingCoaches() {}

    private record Pending(ResourceKey<Level> dimension, BlockPos pos) {}

    /** Filled from any thread a chunk may be finished on; drained on the server thread. */
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();

    public static final String TEMPLATE_NAME = StagecoachBlockEntity.TEMPLATE_PREFIX + "name";
    public static final String TEMPLATE_FLAGS = StagecoachBlockEntity.TEMPLATE_PREFIX + "flags";
    public static final String TEMPLATE_COST = StagecoachBlockEntity.TEMPLATE_PREFIX + "cost";
    public static final String TEMPLATE_SCENE = StagecoachBlockEntity.TEMPLATE_PREFIX + "scene";
    public static final String TEMPLATE_THEME = StagecoachBlockEntity.TEMPLATE_PREFIX + "theme";
    public static final String TEMPLATE_WOOD = StagecoachBlockEntity.TEMPLATE_PREFIX + "wood";

    public static void queue(Level level, BlockPos pos) {
        QUEUE.add(new Pending(level.dimension(), pos.immutable()));
    }

    public static void onServerTick(MinecraftServer server) {
        Pending p;
        while ((p = QUEUE.poll()) != null) {
            ServerLevel level = server.getLevel(p.dimension);
            if (level != null && level.hasChunkAt(p.pos)) check(level, p.pos);
        }
    }

    public static void onServerStopped() {
        QUEUE.clear();
    }

    /** Also used by right-click and the API, so a coach is never used before it's a stop. */
    public static @Nullable CoachRecord check(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StagecoachBlockEntity be) || be.isRemoved() || be.isDecoration()) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StagecoachBlock block)) return null;
        StagecoachState network = StagecoachState.get(level.getServer());
        CoachRecord record = network.coach(be.getCoachId());
        if (record == null || !record.pos.equals(pos) || record.dimension != level.dimension()) {
            CoachRecord here = network.at(level.dimension(), pos);
            if (here == null) return register(level, pos, state, be, network);
            be.setCoachId(here.id);
            record = here;
        }
        if (record.wood == null) {
            record.wood = block.wood();
            network.changed();
        }
        return record;
    }

    private static @Nullable CoachRecord register(ServerLevel level, BlockPos pos, BlockState state, StagecoachBlockEntity be,
                                                  StagecoachState network) {
        StagecoachConfig cfg = StagecoachConfig.get();
        CompoundTag template = be.template() == null ? new CompoundTag() : be.template();
        String themeId = template.contains(TEMPLATE_THEME) ? template.getStringOr(TEMPLATE_THEME, "") : null;
        boolean generated = themeId != null;
        if (cfg.blockedDimensions.contains(level.dimension().identifier().toString())) {
            if (generated) StagecoachStructure.removeCoach(level, pos, state);
            else be.makeDecoration();
            return null;
        }
        String structureKey = generated ? structureKey(level, pos) : null;
        if (structureKey != null && network.countInStructure(structureKey) >= cfg.maxGeneratedStopsPerStructure) {
            StagecoachStructure.removeCoach(level, pos, state);
            return null;
        }

        long seed = Landscape.hash(level.getSeed(), 21, pos.asLong());
        Theme theme = generated ? StagecoachData.theme(themeId) : null;
        StagecoachWood wood = ((StagecoachBlock) state.getBlock()).wood();
        StagecoachWood picked = template.contains(TEMPLATE_WOOD) ? StagecoachWood.byId(template.getStringOr(TEMPLATE_WOOD, ""))
                : theme != null ? StagecoachWood.byId(theme.pickWood(seed)) : null;
        if (picked != null) wood = picked;
        String name = template.contains(TEMPLATE_NAME) ? CoachNames.clean(template.getStringOr(TEMPLATE_NAME, ""), cfg.maxNameLength) : null;
        if (name == null && theme != null && theme.namePool() != null) name = poolName(theme.namePool(), seed, cfg.maxNameLength);
        if (name == null) name = autoName(level, pos, cfg);
        name = network.uniqueName(name, cfg.maxNameLength);

        CoachRecord record = network.register(pos, level.dimension(), state.getValue(StagecoachBlock.FACING), wood, null,
                CreatedBy.STRUCTURE, name);
        for (Tag flag : template.getListOrEmpty(TEMPLATE_FLAGS)) {
            String value = flag.asString().orElse("");
            switch (value) {
                case "public" -> record.isPublic = true;
                case "hidden" -> record.hidden = true;
                case "locked" -> record.locked = true;
                case "free" -> record.free = true;
                default -> Stagecoach.LOGGER.warn("Coach at {} has an unknown template flag '{}' (use {})", pos, value, TravelRule.FLAGS);
            }
        }
        if (template.contains(TEMPLATE_COST)) record.costMultiplier = Math.max(0, template.getDoubleOr(TEMPLATE_COST, 0));
        if (template.contains(TEMPLATE_SCENE)) record.scene = Identifier.tryParse(template.getStringOr(TEMPLATE_SCENE, ""));
        if (generated && !cfg.generatedCoachesBreakable) record.locked = true;
        if (structureKey != null) network.setStructureKey(record, structureKey);

        StagecoachBlockEntity target = StagecoachStructure.rebuild(level, pos, state, wood);
        target.setCoachId(record.id);
        target.setDropsNothing(true);
        target.clearTemplate();
        network.changed();
        ServerNet.knownStopsChanged();
        Stagecoach.LOGGER.debug("Registered stop #{} '{}' at {} in {}", record.id, record.name, pos, level.dimension().identifier());
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

    private static String autoName(ServerLevel level, BlockPos pos, StagecoachConfig cfg) {
        String name = cfg.autoNameFormat.replace("{biome}", biomeName(level.getBiome(pos)));
        String clean = CoachNames.clean(name, cfg.maxNameLength);
        return clean != null ? clean : CoachNames.defaultName(StagecoachState.get(level.getServer()).coaches().size() + 1);
    }

    /** The server's English name for it when it has one, else the id's path in title case. */
    static String biomeName(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isEmpty()) return "Wild";
        Identifier id = key.get().identifier();
        String fallback = titleCase(id.getPath());
        return Language.getInstance().getOrDefault("biome." + id.getNamespace() + "." + id.getPath(), fallback);
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
    static @Nullable String structureKey(ServerLevel level, BlockPos pos) {
        StructureManager structures = level.structureManager();
        for (Map.Entry<Structure, LongSet> e : structures.getAllStructuresAt(pos).entrySet()) {
            StructureStart start = structures.getStructureAt(pos, e.getKey());
            if (!start.isValid()) continue;
            Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey());
            return level.dimension().identifier() + "|" + id + "|" + start.getChunkPos().x() + "," + start.getChunkPos().z();
        }
        return null;
    }
}
