package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.feelix.stagecoach.Stagecoach;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** requireRoad: which stops a road of {@link #ROAD_BLOCKS} joins, surveyed from each origin and kept for a minute. */
public final class RoadNetwork {
    private RoadNetwork() {}

    public static final TagKey<Block> ROAD_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Stagecoach.id("road_blocks"));

    public enum Link { CONNECTED, NOT_CONNECTED, SURVEYING }

    private static final int REACH = 8;
    private static final int STEP_BUDGET = 20_000;
    private static final int CHUNK_LOADS_PER_STEP = 4;
    private static final int FRESH_TICKS = 1200;

    private static final class Survey {
        final RoadSearch search = new RoadSearch(REACH);
        final RegistryKey<World> dimension;
        int finishedAt = -1;
        int steppedAt = -1;

        Survey(RegistryKey<World> dimension) {
            this.dimension = dimension;
        }
    }

    private static final class WorldGround implements RoadSearch.Ground {
        private final BlockPos.Mutable at = new BlockPos.Mutable();
        private ServerWorld world;
        private int loads;

        WorldGround reset(ServerWorld world) {
            this.world = world;
            loads = 0;
            return this;
        }

        @Override
        public int at(int x, int y, int z) {
            if (world.isOutOfHeightLimit(y)) return RoadSearch.NOT_ROAD;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                if (loads >= CHUNK_LOADS_PER_STEP) return RoadSearch.NOT_LOADED;
                loads++;
                world.getChunk(x >> 4, z >> 4);
            }
            return world.getBlockState(at.set(x, y, z)).isIn(ROAD_BLOCKS) ? RoadSearch.ROAD : RoadSearch.NOT_ROAD;
        }
    }

    private static final Long2ObjectOpenHashMap<Survey> SURVEYS = new Long2ObjectOpenHashMap<>();
    private static final WorldGround GROUND = new WorldGround();

    /** Other dimensions are reached through portals, never by road, so they're left to allowCrossDimension. */
    public static Link link(MinecraftServer server, CoachRecord from, CoachRecord to) {
        if (from.dimension != to.dimension) return Link.CONNECTED;
        ServerWorld world = server.getWorld(from.dimension);
        if (world == null) return Link.NOT_CONNECTED;
        Survey survey = SURVEYS.get(from.id);
        int now = server.getTicks();
        if (survey == null || survey.finishedAt >= 0 && now - survey.finishedAt > FRESH_TICKS) {
            survey = start(server, world, from);
            SURVEYS.put(from.id, survey);
        }
        if (survey.finishedAt < 0 && survey.steppedAt != now) {
            survey.steppedAt = now;
            if (survey.search.step(GROUND.reset(world), STEP_BUDGET)) survey.finishedAt = now;
        }
        if (survey.finishedAt < 0) return Link.SURVEYING;
        return survey.search.reached(to.id) ? Link.CONNECTED : Link.NOT_CONNECTED;
    }

    private static Survey start(MinecraftServer server, ServerWorld world, CoachRecord from) {
        Survey survey = new Survey(from.dimension);
        for (CoachRecord c : StagecoachState.get(server).coaches(from.dimension)) {
            if (c.id != from.id) survey.search.addTarget(c.id, c.pos.getX(), c.pos.getY(), c.pos.getZ());
        }
        survey.search.seed(GROUND.reset(world), from.pos.getX(), from.pos.getY(), from.pos.getZ());
        return survey;
    }

    /** Unfinished surveys go on in the background, one budget a tick between them. */
    public static void onServerTick(MinecraftServer server) {
        if (SURVEYS.isEmpty()) return;
        int now = server.getTicks();
        for (Survey survey : SURVEYS.values()) {
            if (survey.finishedAt >= 0 || survey.steppedAt == now) continue;
            survey.steppedAt = now;
            ServerWorld world = server.getWorld(survey.dimension);
            if (world == null || survey.search.step(GROUND.reset(world), STEP_BUDGET)) survey.finishedAt = now;
            return;
        }
    }

    /** A stop came or went: every survey's list of targets is out of date. */
    public static void forget() {
        SURVEYS.clear();
    }
}
