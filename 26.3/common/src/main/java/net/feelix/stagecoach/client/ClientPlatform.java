package net.feelix.stagecoach.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public interface ClientPlatform {
    /** Read {@code buf} on the network thread, then hop to the client thread for the work. */
    @FunctionalInterface
    interface ClientReceiver {
        void receive(Minecraft client, FriendlyByteBuf buf);
    }

    void registerClientReceiver(Identifier channel, ClientReceiver receiver);

    void sendToServer(Identifier channel, FriendlyByteBuf buf);

    final class Holder {
        private Holder() {}

        private static volatile ClientPlatform instance;
    }

    static ClientPlatform get() {
        ClientPlatform p = Holder.instance;
        if (p == null) throw new IllegalStateException("Stagecoach: no client platform installed");
        return p;
    }

    static void install(ClientPlatform platform) {
        Holder.instance = platform;
    }
}
