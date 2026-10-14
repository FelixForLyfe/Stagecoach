package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.CreatedBy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StagecoachState extends PersistentState {
    public static final String ID = "stagecoach_network";
    /** Files without the field are format 1; 2 added the stop flags, wood and provenance. */
    public static final int DATA_VERSION = 2;

    private final Long2ObjectLinkedOpenHashMap<CoachRecord> coaches = new Long2ObjectLinkedOpenHashMap<>();
    private final Collection<CoachRecord> coachView = Collections.unmodifiableCollection(coaches.values());
    private final Map<RegistryKey<World>, List<CoachRecord>> byDimension = new HashMap<>();
    private final Map<RegistryKey<World>, Long2ObjectOpenHashMap<CoachRecord>> byPos = new HashMap<>();
    private final Object2IntOpenHashMap<UUID> owned = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> perStructure = new Object2IntOpenHashMap<>();
    private final Map<UUID, LongSet> discovered = new HashMap<>();
    private final Map<UUID, Journey> journeys = new HashMap<>();
    private long nextId = 1;
    private int loadedVersion = DATA_VERSION;

    /** No data fixer: the format is the mod's own. */
    private static final PersistentState.Type<StagecoachState> TYPE = new PersistentState.Type<>(StagecoachState::new, StagecoachState::fromNbt, null);

    public static StagecoachState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** Writes now, so a crash a tick later still finds journey changes on disk. */
    public void flush(MinecraftServer server) {
        markDirty();
        File file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(ID + ".dat").toFile();
        save(file, server.getRegistryManager());
    }

    public int loadedVersion() {
        return loadedVersion;
    }

    public CoachRecord register(BlockPos pos, RegistryKey<World> dimension, Direction facing, StagecoachWood wood,
                                @Nullable UUID owner, CreatedBy createdBy, @Nullable String name) {
        long id = nextId++;
        CoachRecord record = new CoachRecord(id, name == null ? CoachNames.defaultName(id) : name, pos, dimension, facing,
                owner, wood, createdBy);
        coaches.put(id, record);
        index(record);
        markDirty();
        return record;
    }

    private void index(CoachRecord c) {
        byDimension.computeIfAbsent(c.dimension, k -> new ArrayList<>()).add(c);
        byPos.computeIfAbsent(c.dimension, k -> new Long2ObjectOpenHashMap<>()).put(c.pos.asLong(), c);
        if (c.owner != null) owned.addTo(c.owner, 1);
        if (c.structureKey != null) perStructure.addTo(c.structureKey, 1);
    }

    private void unindex(CoachRecord c) {
        List<CoachRecord> list = byDimension.get(c.dimension);
        if (list != null) list.remove(c);
        Long2ObjectOpenHashMap<CoachRecord> at = byPos.get(c.dimension);
        if (at != null && at.get(c.pos.asLong()) == c) at.remove(c.pos.asLong());
        if (c.owner != null && owned.addTo(c.owner, -1) <= 1) owned.removeInt(c.owner);
        if (c.structureKey != null && perStructure.addTo(c.structureKey, -1) <= 1) perStructure.removeInt(c.structureKey);
    }

    public @Nullable CoachRecord coach(long id) {
        return coaches.get(id);
    }

    public @Nullable CoachRecord at(RegistryKey<World> dimension, BlockPos pos) {
        Long2ObjectOpenHashMap<CoachRecord> at = byPos.get(dimension);
        return at == null ? null : at.get(pos.asLong());
    }

    public Collection<CoachRecord> coaches() {
        return coachView;
    }

    public List<CoachRecord> coaches(RegistryKey<World> dimension) {
        List<CoachRecord> list = byDimension.get(dimension);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public int countOwnedBy(UUID player) {
        return owned.getInt(player);
    }

    public int countInStructure(String key) {
        return perStructure.getInt(key);
    }

    public List<CoachRecord> named(String name) {
        List<CoachRecord> out = new ArrayList<>();
        for (CoachRecord c : coaches.values()) if (c.name.equalsIgnoreCase(name)) out.add(c);
        return out;
    }

    /** "Name", then "Name (2)", "Name (3)"... cut so the suffix always fits. */
    public String uniqueName(String base, int maxLength) {
        String name = cut(base, maxLength);
        for (int n = 2; !named(name).isEmpty(); n++) {
            String suffix = " (" + n + ")";
            name = cut(base, maxLength - suffix.length()).stripTrailing() + suffix;
        }
        return name;
    }

    private static String cut(String s, int maxCodePoints) {
        if (maxCodePoints <= 0) return "";
        if (s.codePointCount(0, s.length()) <= maxCodePoints) return s;
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }

    public void remove(long id) {
        CoachRecord c = coaches.remove(id);
        if (c == null) return;
        unindex(c);
        for (LongSet set : discovered.values()) set.remove(id);
        markDirty();
    }

    public void rename(CoachRecord record, String name) {
        record.name = name;
        markDirty();
    }

    public void setOwner(CoachRecord record, @Nullable UUID owner) {
        unindex(record);
        record.owner = owner;
        index(record);
        markDirty();
    }

    public void setStructureKey(CoachRecord record, String key) {
        unindex(record);
        record.structureKey = key;
        index(record);
        markDirty();
    }

    public void changed() {
        markDirty();
    }

    public boolean hasDiscovered(UUID player, long id) {
        LongSet set = discovered.get(player);
        return set != null && set.contains(id);
    }

    public int discoveredCount(UUID player) {
        LongSet set = discovered.get(player);
        return set == null ? 0 : set.size();
    }

    /** True if this was new. */
    public boolean discover(UUID player, long id) {
        LongSet set = discovered.get(player);
        if (set == null) {
            set = new LongOpenHashSet();
            discovered.put(player, set);
        }
        if (!set.add(id)) return false;
        markDirty();
        return true;
    }

    /** True if it was known. */
    public boolean forget(UUID player, long id) {
        LongSet set = discovered.get(player);
        if (set == null || !set.remove(id)) return false;
        markDirty();
        return true;
    }

    public @Nullable Journey journey(UUID player) {
        return journeys.get(player);
    }

    public Collection<Journey> journeys() {
        return Collections.unmodifiableCollection(journeys.values());
    }

    public void putJourney(Journey journey) {
        journeys.put(journey.player, journey);
        markDirty();
    }

    public void removeJourney(UUID player) {
        if (journeys.remove(player) != null) markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putInt("dataVersion", DATA_VERSION);
        nbt.putLong("nextId", nextId);
        NbtList list = new NbtList();
        for (CoachRecord c : coaches.values()) list.add(c.toNbt());
        nbt.put("coaches", list);
        NbtCompound disc = new NbtCompound();
        for (Map.Entry<UUID, LongSet> e : discovered.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            NbtList ids = new NbtList();
            for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) ids.add(NbtLong.of(it.nextLong()));
            disc.put(e.getKey().toString(), ids);
        }
        nbt.put("discovered", disc);
        NbtList js = new NbtList();
        for (Journey j : journeys.values()) js.add(j.toNbt(registries));
        nbt.put("journeys", js);
        return nbt;
    }

    public static StagecoachState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        StagecoachState state = new StagecoachState();
        state.loadedVersion = nbt.contains("dataVersion") ? nbt.getInt("dataVersion") : 1;
        if (state.loadedVersion > DATA_VERSION) {
            Stagecoach.LOGGER.warn("{}.dat was written by a newer Stagecoach (data version {}); unknown fields are dropped on save",
                    ID, state.loadedVersion);
        }
        state.nextId = Math.max(1, nbt.getLong("nextId"));
        for (NbtElement e : nbt.getList("coaches", NbtElement.COMPOUND_TYPE)) {
            CoachRecord c = CoachRecord.fromNbt((NbtCompound) e);
            if (c == null) {
                Stagecoach.LOGGER.warn("Skipping an unreadable stagecoach record: {}", e);
                continue;
            }
            state.coaches.put(c.id, c);
            state.index(c);
            state.nextId = Math.max(state.nextId, c.id + 1);
        }
        NbtCompound disc = nbt.getCompound("discovered");
        for (String key : disc.getKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                Stagecoach.LOGGER.warn("Skipping a discovered list with a bad player id: {}", key);
                continue;
            }
            LongSet set = new LongOpenHashSet();
            for (NbtElement e : disc.getList(key, NbtElement.LONG_TYPE)) set.add(((NbtLong) e).longValue());
            state.discovered.put(player, set);
        }
        for (NbtElement e : nbt.getList("journeys", NbtElement.COMPOUND_TYPE)) {
            Journey j = Journey.fromNbt((NbtCompound) e, registries);
            if (j != null) state.journeys.put(j.player, j);
            else Stagecoach.LOGGER.warn("Skipping an unreadable journey record: {}", e);
        }
        if (state.loadedVersion < DATA_VERSION) {
            Stagecoach.LOGGER.info("Upgrading {}.dat from data version {} to {} ({} stops)", ID, state.loadedVersion,
                    DATA_VERSION, state.coaches.size());
            state.markDirty();
        }
        return state;
    }
}
