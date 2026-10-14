package net.feelix.stagecoach.forge;

import net.feelix.stagecoach.client.ClientPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ForgeClientNetwork implements ClientPlatform {
    private static final Map<Identifier, ClientReceiver> RECEIVERS = new ConcurrentHashMap<>();

    static void receive(ForgeNetwork.Envelope message) {
        ClientReceiver receiver = RECEIVERS.get(message.id());
        if (receiver != null) receiver.receive(MinecraftClient.getInstance(), message.buf());
    }

    @Override
    public void registerClientReceiver(Identifier channel, ClientReceiver receiver) {
        RECEIVERS.put(channel, receiver);
    }

    @Override
    public void sendToServer(Identifier channel, PacketByteBuf buf) {
        ForgeNetwork.sendToServer(channel, buf);
    }
}
