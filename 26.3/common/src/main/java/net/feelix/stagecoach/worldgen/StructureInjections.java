package net.feelix.stagecoach.worldgen;

import com.mojang.datafixers.util.Pair;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.data.Injection;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.Theme;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hands the coach-stop pieces to each jigsaw assembler while it fills a slot (pools never change), first until the structure has a stop. */
public final class StructureInjections {
    private StructureInjections() {}

    private record Offer(List<StructurePoolElement> guaranteed, List<StructurePoolElement> weighted) {}

    // Built whole, never changed after publishing: worldgen threads read it without a lock.
    private record Snapshot(Map<StructureTemplatePool, Offer> byPool, Map<Identifier, Offer> byId, Map<StructurePoolElement, Offer> byElement,
                            Set<StructurePoolElement> pieces) {}

    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of());
    private static volatile Snapshot now = EMPTY;
    private static final ThreadLocal<List<?>> ASSEMBLING = new ThreadLocal<>();

    public static synchronized void apply(MinecraftServer server) {
        now = EMPTY;
        StagecoachConfig cfg = StagecoachConfig.get();
        if (!cfg.naturalGeneration) return;
        Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        Map<StructureTemplatePool, Offer> byPool = new IdentityHashMap<>();
        Map<Identifier, Offer> byId = new HashMap<>();
        Set<StructurePoolElement> pieces = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, Injection> e : StagecoachData.get().injections().entrySet()) {
            Injection injection = e.getValue();
            if (cfg.disabledInjections.contains(e.getKey()) || !allLoaded(injection.requiresMods())) continue;
            Theme theme = StagecoachData.theme(injection.theme());
            if (theme == null) {
                Stagecoach.LOGGER.warn("Stagecoach injection {}: there is no theme {}", e.getKey(), injection.theme());
                continue;
            }
            String wood = injection.wood();
            if (wood != null && StagecoachWood.byId(wood) == null) {
                Stagecoach.LOGGER.warn("Stagecoach injection {}: '{}' is not a stagecoach wood, the theme's is used", e.getKey(), wood);
                wood = null;
            }
            int copies = injection.guaranteed() ? 1 : (int) Math.round(injection.weight() * cfg.generationWeightMultiplier);
            if (copies <= 0) continue;
            List<StructurePoolElement> elements = new ArrayList<>();
            for (String piece : injection.pieces()) elements.add(element(piece, injection, theme, wood));
            pieces.addAll(elements);
            for (String poolId : injection.targetPools()) {
                Identifier id = Identifier.tryParse(poolId);
                StructureTemplatePool pool = id == null ? null : pools.getValue(id);
                if (pool == null) {
                    Stagecoach.LOGGER.debug("Stagecoach injection {}: there is no template pool {}", e.getKey(), poolId);
                    continue;
                }
                Offer offer = byPool.computeIfAbsent(pool, p -> new Offer(new ArrayList<>(), new ArrayList<>()));
                byId.put(id, offer);
                if (injection.guaranteed()) offer.guaranteed().addAll(elements);
                else for (int i = 0; i < copies; i++) offer.weighted().addAll(elements);
            }
        }
        // Weighted assemblers (Repurposed Structures, Integrated API) only hand over the pool's pieces, not the pool
        Map<StructurePoolElement, Offer> byElement = new IdentityHashMap<>();
        RandomSource random = RandomSource.create(0L);
        for (Map.Entry<StructureTemplatePool, Offer> e : byPool.entrySet()) {
            for (StructurePoolElement element : e.getKey().getShuffledTemplates(random)) {
                if (element != EmptyPoolElement.INSTANCE) byElement.putIfAbsent(element, e.getValue());
            }
        }
        now = new Snapshot(byPool, byId, byElement, pieces);
        if (!byPool.isEmpty()) Stagecoach.LOGGER.info("Stagecoach offers its coach stop to {} structure template pools", byPool.size());
    }

    /** Set by vanilla's jigsaw assembler while it fills one piece's slots: the structure's pieces so far. */
    public static void assembling(@Nullable List<?> placed) {
        if (placed == null) ASSEMBLING.remove();
        else ASSEMBLING.set(placed);
    }

    /** Vanilla: a pool's shuffled pieces for the slot being filled, with the coach stops added; unchanged outside an assembly. */
    public static List<StructurePoolElement> offer(StructureTemplatePool pool, List<StructurePoolElement> shuffled, RandomSource random) {
        Offer offer = now.byPool().get(pool);
        List<?> placed = offer == null ? null : ASSEMBLING.get();
        return placed == null ? shuffled : order(offer, shuffled, placed, random);
    }

    /** Lithostitched: the same, for its own assembler, which names the pool by id. */
    public static List<StructurePoolElement> offer(Identifier poolId, List<StructurePoolElement> shuffled, List<?> placed, RandomSource random) {
        Offer offer = now.byId().get(poolId);
        return offer == null ? shuffled : order(offer, shuffled, placed, random);
    }

    /** Repurposed Structures and Integrated API pick by weight: the guaranteed pieces get almost all of it. */
    public static List<Pair<StructurePoolElement, Integer>> offerWeighted(List<Pair<StructurePoolElement, Integer>> candidates,
                                                                          List<?> placed) {
        Snapshot snapshot = now;
        Offer offer = null;
        for (Pair<StructurePoolElement, Integer> candidate : candidates) {
            offer = snapshot.byElement().get(candidate.getFirst());
            if (offer != null) break;
        }
        if (offer == null) return candidates;
        int stops = stops(placed, snapshot.pieces());
        if (stops >= StagecoachConfig.get().maxGeneratedStopsPerStructure) return candidates;
        int total = 0;
        for (Pair<StructurePoolElement, Integer> candidate : candidates) total += candidate.getSecond();
        List<Pair<StructurePoolElement, Integer>> out = new ArrayList<>(candidates.size() + offer.guaranteed().size() + offer.weighted().size());
        if (stops == 0) {
            for (int i = 0; i < offer.guaranteed().size(); i++) {
                out.add(Pair.of(offer.guaranteed().get(i), StopOffers.guaranteedWeight(total, i, offer.guaranteed().size())));
            }
        }
        out.addAll(candidates);
        for (StructurePoolElement element : offer.weighted()) out.add(Pair.of(element, 1));
        return out;
    }

    public static boolean isStopPiece(StructurePoolElement element) {
        return now.pieces().contains(element);
    }

    private static List<StructurePoolElement> order(Offer offer, List<StructurePoolElement> shuffled, List<?> placed, RandomSource random) {
        return StopOffers.order(shuffled, offer.guaranteed(), offer.weighted(), stops(placed, now.pieces()),
                StagecoachConfig.get().maxGeneratedStopsPerStructure, random::nextInt);
    }

    private static int stops(List<?> placed, Set<StructurePoolElement> pieces) {
        int stops = 0;
        for (Object piece : placed) {
            if (piece instanceof PoolElementStructurePiece p && pieces.contains(p.getElement())) stops++;
        }
        return stops;
    }

    private static StructurePoolElement element(String piece, Injection injection, Theme theme, @Nullable String wood) {
        String palette = wood != null ? wood : theme.decorPalette() == null ? "" : theme.decorPalette();
        StructureProcessorList processors = new StructureProcessorList(List.of(new CoachStopProcessor(injection.theme(), palette,
                wood == null ? "" : wood)));
        StructureTemplatePool.Projection projection = injection.projection().equals("terrain_matching")
                ? StructureTemplatePool.Projection.TERRAIN_MATCHING : StructureTemplatePool.Projection.RIGID;
        return StructurePoolElement.single(piece, Holder.direct(processors)).apply(projection);
    }

    private static boolean allLoaded(List<String> mods) {
        for (String mod : mods) if (!Platform.get().isModLoaded(mod)) return false;
        return true;
    }

    public static synchronized void onServerStopped() {
        now = EMPTY;
    }
}
