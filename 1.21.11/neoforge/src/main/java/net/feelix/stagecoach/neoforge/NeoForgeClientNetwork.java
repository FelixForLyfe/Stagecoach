package net.feelix.stagecoach.neoforge;

import net.feelix.stagecoach.client.ClientPlatform;
import net.feelix.stagecoach.net.RawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class NeoForgeClientNetwork implements ClientPlatform {
    private static final Map<Identifier, ClientReceiver> RECEIVERS = new ConcurrentHashMap<>();

    static void receive(RawPayload payload) {
        ClientReceiver receiver = RECEIVERS.get(payload.channel());
        if (receiver != null) receiver.receive(Minecraft.getInstance(), payload.buf());
    }

    @Override
    public void registerClientReceiver(Identifier channel, ClientReceiver receiver) {
        RECEIVERS.put(channel, receiver);
    }

    @Override
    public void sendToServer(Identifier channel, FriendlyByteBuf buf) {
        ClientPacketDistributor.sendToServer(RawPayload.of(channel, buf));
    }
}
