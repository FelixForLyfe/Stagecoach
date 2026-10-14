package net.feelix.stagecoach.travel;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.CreatedBy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StagecoachState extends SavedData {
    public static final String ID = "stagecoach_network";
    /** Files without the field are format 1; 2 added the stop flags, wood and provenance. */
    public static final int DATA_VERSION = 2;

    private final Long2ObjectLinkedOpenHashMap<CoachRecord> coaches = new Long2ObjectLinkedOpenHashMap<>();
    private final Collection<CoachRecord> coachView = Collections.unmodifiableCollection(coaches.values());
    private final Map<ResourceKey<Level>, List<CoachRecord>> byDimension = new HashMap<>();
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<CoachRecord>> byPos = new HashMap<>();
    private final Object2IntOpenHashMap<UUID> owned = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> perStructure = new Object2IntOpenHashMap<>();
    private final Map<UUID, LongSet> discovered = new HashMap<>();
    private final Map<UUID, Journey> journeys = new HashMap<>();
    private long nextId = 1;
    private int loadedVersion = DATA_VERSION;

    /** The same compound as ever, so files from older versions carry over. */
    private static final Codec<StagecoachState> CODEC =
            CompoundTag.CODEC.xmap(StagecoachState::fromNbt, state -> state.writeNbt(new CompoundTag()));
    /** The fixer type can't be null; command storage passes through every schema untouched. */
    private static final SavedDataType<StagecoachState> TYPE =
            new SavedDataType<>(Stagecoach.id(ID), StagecoachState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    public static StagecoachState get(MinecraftServer server) {
        StagecoachState state = server.getDataStorage().get(TYPE);
        if (state == null) {
            state = importLegacy(server);
            server.getDataStorage().set(TYPE, state);
        }
        return state;
    }

    /** Reads a 1.20/1.21 world's data/stagecoach_network.dat once and leaves it in place. */
    private static StagecoachState importLegacy(MinecraftServer server) {
        Path legacy = server.getWorldPath(LevelResource.DATA).resolve(ID + ".dat");
        if (!Files.isRegularFile(legacy)) return new StagecoachState();
        try {
            CompoundTag file = NbtIo.readCompressed(legacy, NbtAccounter.unlimitedHeap());
            StagecoachState state = fromNbt(file.getCompoundOrEmpty("data"));
            state.setDirty();
            Stagecoach.LOGGER.info("Imported the stagecoach network from the pre-26 file {} ({} coaches)", legacy, state.coaches.size());
            return state;
        } catch (IOException | RuntimeException e) {
            Stagecoach.LOGGER.error("Could not read the pre-26 stagecoach network {} - starting empty", legacy, e);
            return new StagecoachState();
        }
    }

    /** Writes now, so a crash a tick later still finds journey changes on disk. */
    public void flush(MinecraftServer server) {
        setDirty();
        server.getDataStorage().saveAndJoin();
    }

    public int loadedVersion() {
        return loadedVersion;
    }

    public CoachRecord register(BlockPos pos, ResourceKey<Level> dimension, Direction facing, StagecoachWood wood,
                                @Nullable UUID owner, CreatedBy createdBy, @Nullable String name) {
        long id = nextId++;
        CoachRecord record = new CoachRecord(id, name == null ? CoachNames.defaultName(id) : name, pos, dimension, facing,
                owner, wood, createdBy);
        coaches.put(id, record);
        index(record);
        setDirty();
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

    public @Nullable CoachRecord at(ResourceKey<Level> dimension, BlockPos pos) {
        Long2ObjectOpenHashMap<CoachRecord> at = byPos.get(dimension);
        return at == null ? null : at.get(pos.asLong());
    }

    public Collection<CoachRecord> coaches() {
        return coachView;
    }

    public List<CoachRecord> coaches(ResourceKey<Level> dimension) {
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
        setDirty();
    }

    public void rename(CoachRecord record, String name) {
        record.name = name;
        setDirty();
    }

    public void setOwner(CoachRecord record, @Nullable UUID owner) {
        unindex(record);
        record.owner = owner;
        index(record);
        setDirty();
    }

    public void setStructureKey(CoachRecord record, String key) {
        unindex(record);
        record.structureKey = key;
        index(record);
        setDirty();
    }

    public void changed() {
        setDirty();
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
        setDirty();
        return true;
    }

    /** True if it was known. */
    public boolean forget(UUID player, long id) {
        LongSet set = discovered.get(player);
        if (set == null || !set.remove(id)) return false;
        setDirty();
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
        setDirty();
    }

    public void removeJourney(UUID player) {
        if (journeys.remove(player) != null) setDirty();
    }

    public CompoundTag writeNbt(CompoundTag nbt) {
        nbt.putInt("dataVersion", DATA_VERSION);
        nbt.putLong("nextId", nextId);
        ListTag list = new ListTag();
        for (CoachRecord c : coaches.values()) list.add(c.toNbt());
        nbt.put("coaches", list);
        CompoundTag disc = new CompoundTag();
        for (Map.Entry<UUID, LongSet> e : discovered.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            ListTag ids = new ListTag();
            for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) ids.add(LongTag.valueOf(it.nextLong()));
            disc.put(e.getKey().toString(), ids);
        }
        nbt.put("discovered", disc);
        ListTag js = new ListTag();
        for (Journey j : journeys.values()) js.add(j.toNbt());
        nbt.put("journeys", js);
        return nbt;
    }

    public static StagecoachState fromNbt(CompoundTag nbt) {
        StagecoachState state = new StagecoachState();
        state.loadedVersion = nbt.getIntOr("dataVersion", 1);
        if (state.loadedVersion > DATA_VERSION) {
            Stagecoach.LOGGER.warn("{}.dat was written by a newer Stagecoach (data version {}); unknown fields are dropped on save",
                    ID, state.loadedVersion);
        }
        state.nextId = Math.max(1, nbt.getLongOr("nextId", 1));
        for (CompoundTag e : nbt.getListOrEmpty("coaches").compoundStream().toList()) {
            CoachRecord c = CoachRecord.fromNbt(e);
            if (c == null) {
                Stagecoach.LOGGER.warn("Skipping an unreadable stagecoach record: {}", e);
                continue;
            }
            state.coaches.put(c.id, c);
            state.index(c);
            state.nextId = Math.max(state.nextId, c.id + 1);
        }
        CompoundTag disc = nbt.getCompoundOrEmpty("discovered");
        for (String key : disc.keySet()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                Stagecoach.LOGGER.warn("Skipping a discovered list with a bad player id: {}", key);
                continue;
            }
            LongSet set = new LongOpenHashSet();
            for (Tag e : disc.getListOrEmpty(key)) if (e instanceof LongTag l) set.add(l.value());
            state.discovered.put(player, set);
        }
        for (CompoundTag e : nbt.getListOrEmpty("journeys").compoundStream().toList()) {
            Journey j = Journey.fromNbt(e);
            if (j != null) state.journeys.put(j.player, j);
            else Stagecoach.LOGGER.warn("Skipping an unreadable journey record: {}", e);
        }
        if (state.loadedVersion < DATA_VERSION) {
            Stagecoach.LOGGER.info("Upgrading {}.dat from data version {} to {} ({} stops)", ID, state.loadedVersion,
                    DATA_VERSION, state.coaches.size());
            state.setDirty();
        }
        return state;
    }
}
