package net.feelix.stagecoach.forge;

import io.netty.buffer.Unpooled;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class ForgeNetwork {
    private ForgeNetwork() {}

    private static final String PROTOCOL = "3";
    /** The destination list is the only large packet, far below this. */
    private static final int MAX_BYTES = 1 << 20;

    static final Map<Identifier, Platform.ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static SimpleChannel channel;

    /** Which shared channel, and the bytes the shared code wrote for it. */
    record Envelope(Identifier id, byte[] data) {
        static Envelope of(Identifier id, PacketByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return new Envelope(id, bytes);
        }

        PacketByteBuf buf() {
            return new PacketByteBuf(Unpooled.wrappedBuffer(data));
        }

        void write(PacketByteBuf out) {
            out.writeIdentifier(id);
            out.writeByteArray(data);
        }

        static Envelope read(PacketByteBuf in) {
            return new Envelope(in.readIdentifier(), in.readByteArray(MAX_BYTES));
        }
    }

    static void init() {
        channel = NetworkRegistry.newSimpleChannel(Stagecoach.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.messageBuilder(Envelope.class, 0)
                .encoder(Envelope::write)
                .decoder(Envelope::read)
                .consumerNetworkThread(ForgeNetwork::handle)
                .add();
    }

    private static void handle(Envelope message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        if (ctx.getDirection().getReceptionSide().isServer()) {
            ServerPlayerEntity player = ctx.getSender();
            Platform.ServerReceiver receiver = SERVER_RECEIVERS.get(message.id());
            if (player != null && receiver != null) receiver.receive(player.getServer(), player, message.buf());
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClientNetwork.receive(message));
        }
        ctx.setPacketHandled(true);
    }

    static void sendToPlayer(ServerPlayerEntity player, Identifier id, PacketByteBuf buf) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), Envelope.of(id, buf));
    }

    static void sendToServer(Identifier id, PacketByteBuf buf) {
        channel.sendToServer(Envelope.of(id, buf));
    }
}
