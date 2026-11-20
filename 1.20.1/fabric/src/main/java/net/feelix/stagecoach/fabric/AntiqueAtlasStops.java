package net.feelix.stagecoach.fabric;

import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The known stops as Antique Atlas markers: Surveyor landmarks put locally, so they never reach the server. */
final class AntiqueAtlasStops {
    private AntiqueAtlasStops() {}

    /** Ids under this path draw assets/stagecoach/textures/atlas/marker/stop.png. */
    private static final String PATH = "stop/";
    private static final int COLOUR = 0xD9A441;

    /** Landmarks are saved with the client's map, so stops gone since the last visit are swept here too. */
    static void show(List<Packets.KnownStop> stops) {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<RegistryKey<World>, List<Packets.KnownStop>> byDimension = new HashMap<>();
        if (client.world != null) byDimension.put(client.world.getRegistryKey(), new ArrayList<>());
        for (Packets.KnownStop stop : stops) {
            byDimension.computeIfAbsent(RegistryKey.of(RegistryKeys.WORLD, stop.dimension()), k -> new ArrayList<>()).add(stop);
        }
        for (Map.Entry<RegistryKey<World>, List<Packets.KnownStop>> e : byDimension.entrySet()) {
            WorldSummary summary = SurveyorClient.tryGetSummary(e.getKey());
            if (summary == null || summary.landmarks() == null) continue;
            WorldLandmarks landmarks = summary.landmarks();
            Set<Identifier> wanted = new HashSet<>();
            for (Packets.KnownStop stop : e.getValue()) {
                Identifier id = Stagecoach.id(PATH + stop.id());
                wanted.add(id);
                landmarks.putLocal(Landmark.global(id, b -> b.add(LandmarkComponentTypes.POS, stop.pos())
                        .add(LandmarkComponentTypes.NAME, Text.literal(stop.name()))
                        .add(LandmarkComponentTypes.COLOR, COLOUR)));
            }
            List<Identifier> stale = new ArrayList<>();
            for (Identifier id : landmarks.keySet(null).get(WorldLandmarks.GLOBAL)) {
                if (id.getNamespace().equals(Stagecoach.MOD_ID) && id.getPath().startsWith(PATH) && !wanted.contains(id)) stale.add(id);
            }
            for (Identifier id : stale) landmarks.removeLocal(WorldLandmarks.GLOBAL, id);
        }
    }
}
