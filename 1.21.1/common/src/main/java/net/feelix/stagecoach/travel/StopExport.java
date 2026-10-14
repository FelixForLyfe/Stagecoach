package net.feelix.stagecoach.travel;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachCommands;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A stop network as a file: positions, woods, names and flags, never players or discoveries. */
public final class StopExport {
    private StopExport() {}

    private static final int FORMAT = 1;

    private static Path file(String name) {
        return Platform.get().configDir().resolve(Stagecoach.MOD_ID).resolve("exports").resolve(name + ".json");
    }

    public static int export(ServerCommandSource source, String name) {
        StagecoachState network = StagecoachState.get(source.getServer());
        JsonArray stops = new JsonArray();
        for (CoachRecord c : network.coaches()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            o.addProperty("dimension", c.dimension.getValue().toString());
            JsonArray pos = new JsonArray();
            pos.add(c.pos.getX());
            pos.add(c.pos.getY());
            pos.add(c.pos.getZ());
            o.add("pos", pos);
            o.addProperty("facing", c.facing.asString());
            o.addProperty("wood", c.woodOrOak().asString());
            JsonArray flags = new JsonArray();
            if (c.isPublic) flags.add("public");
            if (c.hidden) flags.add("hidden");
            if (c.locked) flags.add("locked");
            if (c.free) flags.add("free");
            o.add("flags", flags);
            o.addProperty("cost", c.costMultiplier);
            if (c.scene != null) o.addProperty("scene", c.scene.toString());
            stops.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("stops", stops);
        Path out = file(name);
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendError(Stagecoach.text("command.export_failed", out.toString(), e.getMessage()));
            return 0;
        }
        source.sendFeedback(() -> Stagecoach.text("command.exported", stops.size(), out.toString()), true);
        return stops.size();
    }

    /** Places every stop with force, loading its chunk; {@code replace} takes down a stop with the same name or spot first. */
    public static int importFrom(ServerCommandSource source, String name, boolean replace) {
        Path in = file(name);
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(in, StandardCharsets.UTF_8));
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            source.sendError(Stagecoach.text("command.import_unreadable", in.toString(), String.valueOf(e.getMessage())));
            return 0;
        }
        StagecoachState network = StagecoachState.get(source.getServer());
        int max = StagecoachConfig.get().maxNameLength;
        int placed = 0;
        List<Text> failures = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("stops")) {
            String label = "?";
            try {
                JsonObject o = e.getAsJsonObject();
                label = o.get("name").getAsString();
                String stopName = CoachNames.clean(label, max);
                Identifier dim = Identifier.tryParse(o.get("dimension").getAsString());
                ServerWorld world = dim == null ? null : source.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, dim));
                JsonArray p = o.getAsJsonArray("pos");
                BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
                Direction facing = Direction.byName(o.get("facing").getAsString());
                StagecoachWood wood = StagecoachWood.byId(o.has("wood") ? o.get("wood").getAsString() : "oak");
                if (stopName == null || world == null || facing == null || facing.getAxis().isVertical() || wood == null) {
                    failures.add(Stagecoach.text("command.import_bad_entry", label));
                    continue;
                }
                CoachRecord existing = network.at(world.getRegistryKey(), pos);
                List<CoachRecord> sameName = network.named(stopName);
                if (existing != null || !sameName.isEmpty()) {
                    if (!replace) {
                        failures.add(Stagecoach.text("command.import_exists", label));
                        continue;
                    }
                    if (existing != null) takeDown(world, existing, network);
                    for (CoachRecord r : sameName) {
                        ServerWorld w = source.getServer().getWorld(r.dimension);
                        if (w != null) takeDown(w, r, network);
                    }
                }
                world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                BlockState controller = StagecoachRegistry.BLOCKS.get(wood).getDefaultState().with(StagecoachBlock.FACING, facing);
                CoachRecord record = StagecoachCommands.createStop(world, pos, controller, stopName);
                if (o.has("flags")) {
                    for (JsonElement f : o.getAsJsonArray("flags")) {
                        switch (f.getAsString()) {
                            case "public" -> record.isPublic = true;
                            case "hidden" -> record.hidden = true;
                            case "locked" -> record.locked = true;
                            case "free" -> record.free = true;
                            default -> {
                            }
                        }
                    }
                }
                if (o.has("cost")) record.costMultiplier = Math.max(0, o.get("cost").getAsDouble());
                if (o.has("scene")) record.scene = Identifier.tryParse(o.get("scene").getAsString());
                network.changed();
                placed++;
            } catch (RuntimeException ex) {
                failures.add(Stagecoach.text("command.import_bad_entry", label));
            }
        }
        int n = placed;
        source.sendFeedback(() -> Stagecoach.text("command.imported", n, failures.size()), true);
        for (Text f : failures) source.sendFeedback(() -> f.copy().formatted(Formatting.RED), false);
        return placed;
    }

    private static void takeDown(ServerWorld world, CoachRecord r, StagecoachState network) {
        if (world.getBlockEntity(r.pos) instanceof StagecoachBlockEntity be && be.getCoachId() == r.id) {
            StagecoachStructure.removeCoach(world, r.pos, world.getBlockState(r.pos));
        }
        network.remove(r.id);
    }
}
