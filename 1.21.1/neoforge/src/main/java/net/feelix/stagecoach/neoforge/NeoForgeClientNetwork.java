package net.feelix.stagecoach.neoforge;

import net.feelix.stagecoach.client.ClientPlatform;
import net.feelix.stagecoach.net.RawPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class NeoForgeClientNetwork implements ClientPlatform {
    private static final Map<Identifier, ClientReceiver> RECEIVERS = new ConcurrentHashMap<>();

    static void receive(RawPayload payload) {
        ClientReceiver receiver = RECEIVERS.get(payload.channel());
        if (receiver != null) receiver.receive(MinecraftClient.getInstance(), payload.buf());
    }

    @Override
    public void registerClientReceiver(Identifier channel, ClientReceiver receiver) {
        RECEIVERS.put(channel, receiver);
    }

    @Override
    public void sendToServer(Identifier channel, PacketByteBuf buf) {
        PacketDistributor.sendToServer(RawPayload.of(channel, buf));
    }
}
