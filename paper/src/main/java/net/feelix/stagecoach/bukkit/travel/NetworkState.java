package net.feelix.stagecoach.bukkit.travel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.bukkit.model.CoachModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NetworkState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record CellKey(UUID world, int x, int y, int z) {}

    /** Barrier cells hold a barrier; the rest are kept empty. */
    public record Cell(long coachId, int index, boolean barrier) {}

    private final Path file;
    private final Logger log;
    private long nextId = 1;
    private final Map<Long, CoachRecord> coaches = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> discovered = new HashMap<>();
    private final Map<UUID, Journey> journeys = new HashMap<>();
    private final Map<CellKey, Cell> cells = new HashMap<>();
    /** False after reading a file failed, so nothing is written over it. */
    private boolean writable = true;

    private NetworkState(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public CoachRecord register(LongFunction<CoachRecord> factory) {
        CoachRecord r = factory.apply(nextId++);
        coaches.put(r.id, r);
        index(r);
        return r;
    }

    public CoachRecord coach(long id) {
        return coaches.get(id);
    }

    public Collection<CoachRecord> coaches() {
        return coaches.values();
    }

    public int countOwnedBy(UUID player) {
        int n = 0;
        for (CoachRecord c : coaches.values()) if (c.isOwner(player)) n++;
        return n;
    }

    /** The registry, the cell index and every discovered list. */
    public void remove(long id) {
        CoachRecord r = coaches.remove(id);
        if (r == null) return;
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) cells.remove(cellKey(r, i));
        for (Set<Long> set : discovered.values()) set.remove(id);
    }

    public void rename(CoachRecord r, String name) {
        r.name = name;
    }

    public Cell cellAt(UUID world, int x, int y, int z) {
        return cells.get(new CellKey(world, x, y, z));
    }

    public static CellKey cellKey(CoachRecord r, int index) {
        int[] o = CoachModel.offset(index, r.turns);
        return new CellKey(r.world, r.x + o[0], r.y + o[1], r.z + o[2]);
    }

    private void index(CoachRecord r) {
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            cells.put(cellKey(r, i), new Cell(r.id, i, CoachModel.barrier(i)));
        }
    }

    public boolean hasDiscovered(UUID player, long coachId) {
        Set<Long> set = discovered.get(player);
        return set != null && set.contains(coachId);
    }

    public void discover(UUID player, long coachId) {
        discovered.computeIfAbsent(player, k -> new HashSet<>()).add(coachId);
    }

    public Journey journey(UUID player) {
        return journeys.get(player);
    }

    public void putJourney(Journey j) {
        journeys.put(j.player, j);
    }

    public void removeJourney(UUID player) {
        journeys.remove(player);
    }

    /** Temp file, then .bak, then swap: a crash always leaves one complete file. Synchronous on purpose. */
    public void flush() {
        if (!writable) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(toJson()), StandardCharsets.UTF_8);
            if (Files.exists(file)) {
                Files.move(file, bak(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    private Path bak() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("nextId", nextId);
        JsonArray list = new JsonArray();
        for (CoachRecord r : coaches.values()) list.add(r.toJson());
        root.add("coaches", list);
        JsonObject disc = new JsonObject();
        for (Map.Entry<UUID, Set<Long>> e : discovered.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            JsonArray ids = new JsonArray();
            e.getValue().stream().sorted().forEach(ids::add);
            disc.add(e.getKey().toString(), ids);
        }
        root.add("discovered", disc);
        JsonArray js = new JsonArray();
        for (Journey j : journeys.values()) js.add(j.toJson());
        root.add("journeys", js);
        return root;
    }

    /** An unreadable file is set aside and never overwritten; then nothing is saved until it's fixed. */
    public static NetworkState load(Path file, Logger log) {
        NetworkState s = new NetworkState(file, log);
        Path bak = s.bak();
        if (!Files.exists(file) && !Files.exists(bak)) return s;
        List<String> errors = new ArrayList<>();
        for (Path p : new Path[] {file, bak}) {
            if (!Files.exists(p)) continue;
            try {
                s.read(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject(), log);
                if (p == bak) {
                    log.warning("Loaded the travel network from " + bak.getFileName() + " (" + String.join("; ", errors) + ")");
                    if (Files.exists(file)) {
                        Files.copy(file, file.resolveSibling(file.getFileName() + ".unreadable-" + System.currentTimeMillis()));
                    }
                }
                return s;
            } catch (Exception e) {
                errors.add(p.getFileName() + ": " + e.getMessage());
                s = new NetworkState(file, log);
            }
        }
        log.severe("Could not read the travel network (" + String.join("; ", errors) + "). Stagecoach runs with an "
                + "empty network and will NOT overwrite " + file.getFileName() + " until it is fixed or removed.");
        s.writable = false;
        return s;
    }

    private void read(JsonObject root, Logger log) {
        nextId = root.get("nextId").getAsLong();
        for (JsonElement e : root.getAsJsonArray("coaches")) {
            CoachRecord r = CoachRecord.fromJson(e.getAsJsonObject());
            if (r == null) {
                log.warning("Skipping a malformed stagecoach record: " + e);
                continue;
            }
            coaches.put(r.id, r);
            index(r);
            nextId = Math.max(nextId, r.id + 1);
        }
        JsonObject disc = root.getAsJsonObject("discovered");
        for (Map.Entry<String, JsonElement> e : disc.entrySet()) {
            Set<Long> ids = new HashSet<>();
            for (JsonElement id : e.getValue().getAsJsonArray()) ids.add(id.getAsLong());
            discovered.put(UUID.fromString(e.getKey()), ids);
        }
        for (JsonElement e : root.getAsJsonArray("journeys")) {
            Journey j = Journey.fromJson(e.getAsJsonObject());
            if (j == null) log.warning("Skipping a malformed journey record: " + e);
            else journeys.put(j.player, j);
        }
    }

    /** For tests: memory only until flushed. */
    static NetworkState empty(Path file, Logger log) {
        return new NetworkState(file, log);
    }
}
