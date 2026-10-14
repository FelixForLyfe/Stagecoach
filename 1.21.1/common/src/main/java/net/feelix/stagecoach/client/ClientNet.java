package net.feelix.stagecoach.client;

import io.netty.buffer.Unpooled;
import net.feelix.stagecoach.client.scene.Scene;
import net.feelix.stagecoach.client.screen.DestinationScreen;
import net.feelix.stagecoach.client.screen.NamingScreen;
import net.feelix.stagecoach.client.screen.TravelScreen;
import net.feelix.stagecoach.compat.Waypoints;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ClientNet {
    private ClientNet() {}

    private static @Nullable Packets.JourneyStart journey;
    private static @Nullable SoundInstance loop;

    public static @Nullable Packets.JourneyStart journey() {
        return journey;
    }

    public static void init() {
        ClientPlatform net = ClientPlatform.get();
        net.registerClientReceiver(Packets.OPEN_NAMING, (client, buf) -> {
            Packets.OpenNaming data = Packets.OpenNaming.read(buf);
            client.execute(() -> {
                if (journey == null) client.setScreen(new NamingScreen(data, null));
            });
        });
        net.registerClientReceiver(Packets.OPEN_LIST, (client, buf) -> {
            Packets.DestinationList data = Packets.DestinationList.read(buf);
            client.execute(() -> {
                if (journey == null) client.setScreen(new DestinationScreen(data));
            });
        });
        net.registerClientReceiver(Packets.LIST_MORE, (client, buf) -> {
            Packets.ListMore data = Packets.ListMore.read(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof DestinationScreen screen) screen.append(data);
            });
        });
        net.registerClientReceiver(Packets.JOURNEY_START, (client, buf) -> {
            Packets.JourneyStart data = Packets.JourneyStart.read(buf);
            client.execute(() -> {
                journey = data;
                TravelScreen screen = new TravelScreen(data);
                client.setScreen(screen);
                startLoop(client, screen.scene());
            });
        });
        net.registerClientReceiver(Packets.JOURNEY_END, (client, buf) -> {
            buf.readByte(); // reason; the server's own message explains it
            client.execute(() -> {
                journey = null;
                stopLoop(client);
                if (client.currentScreen instanceof TravelScreen || client.currentScreen instanceof TravelScreen.Abandon) {
                    client.setScreen(null);
                }
            });
        });
        net.registerClientReceiver(Packets.SERVER_SETTINGS, (client, buf) -> {
            Packets.ServerSettings data = Packets.ServerSettings.read(buf);
            client.execute(() -> ClientConfig.setForcedStyle(data.travelStyle()));
        });
        net.registerClientReceiver(Packets.STOP_EFFECT, (client, buf) -> {
            Packets.StopEffect data = Packets.StopEffect.read(buf);
            client.execute(() -> CoachEffects.play(client, data));
        });
        net.registerClientReceiver(Packets.KNOWN_STOPS, (client, buf) -> {
            Packets.KnownStops data = Packets.KnownStops.read(buf);
            client.execute(() -> Waypoints.update(data.stops()));
        });
    }

    /** Asks for the known-stop list only when a map mod can show it. */
    public static void onJoin() {
        if (Waypoints.available()) ClientPlatform.get().sendToServer(Packets.WANT_STOPS, new PacketByteBuf(Unpooled.buffer()));
    }

    /** Reopens the travel screen if anything closed it while the server still has us on the road. */
    public static void onClientTick(MinecraftClient client) {
        Waypoints.tick();
        if (journey != null && client.world != null && client.currentScreen == null) client.setScreen(new TravelScreen(journey));
    }

    public static void onDisconnect() {
        journey = null;
        stopLoop(MinecraftClient.getInstance());
        ClientConfig.setForcedStyle("none");
        Waypoints.clear();
    }

    /** Only a sound event that has sounds is played, so an empty placeholder never logs a warning. */
    private static void startLoop(MinecraftClient client, Scene scene) {
        stopLoop(client);
        Identifier id = scene.ambientSound() == null || !ClientConfig.get().roadScene() ? null : Identifier.tryParse(scene.ambientSound());
        if (id == null) return;
        WeightedSoundSet set = client.getSoundManager().get(id);
        if (set == null || set.getWeight() == 0) return;
        loop = new PositionedSoundInstance(id, SoundCategory.AMBIENT, 0.7f, 1.0f, SoundInstance.createRandom(), true, 0,
                SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        client.getSoundManager().play(loop);
    }

    private static void stopLoop(MinecraftClient client) {
        if (loop == null) return;
        client.getSoundManager().stop(loop);
        loop = null;
    }

    public static void setName(long coachId, String name, boolean fromList) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new Packets.SetName(coachId, name, fromList).write(buf);
        ClientPlatform.get().sendToServer(Packets.SET_NAME, buf);
    }

    public static void travel(long originId, long destId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new Packets.Travel(originId, destId).write(buf);
        ClientPlatform.get().sendToServer(Packets.TRAVEL, buf);
    }

    public static void cancel() {
        ClientPlatform.get().sendToServer(Packets.CANCEL, new PacketByteBuf(Unpooled.buffer()));
    }
}
