package net.feelix.stagecoach.forge;

import io.netty.buffer.Unpooled;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Every shared packet travels as one envelope: the channel id and the exact bytes Packets wrote. */
final class ForgeNetwork {
    private ForgeNetwork() {}

    private static final int PROTOCOL = 3;
    /** The destination list is the only large packet, far below this. */
    private static final int MAX_BYTES = 1 << 20;

    static final Map<Identifier, Platform.ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static SimpleChannel channel;

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
        channel = ChannelBuilder.named(Stagecoach.id("main")).networkProtocolVersion(PROTOCOL).simpleChannel();
        channel.messageBuilder(Envelope.class)
                .encoder(Envelope::write)
                .decoder(Envelope::read)
                .consumerNetworkThread(ForgeNetwork::handle)
                .add();
    }

    /** The client branch only runs on a client, so its client-only class never loads on a server. */
    private static void handle(Envelope message, CustomPayloadEvent.Context ctx) {
        if (ctx.isServerSide()) {
            ServerPlayerEntity player = ctx.getSender();
            Platform.ServerReceiver receiver = SERVER_RECEIVERS.get(message.id());
            if (player != null && receiver != null) receiver.receive(player.getServer(), player, message.buf());
        } else if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientNetwork.receive(message);
        }
        ctx.setPacketHandled(true);
    }

    static void sendToPlayer(ServerPlayerEntity player, Identifier id, PacketByteBuf buf) {
        channel.send(Envelope.of(id, buf), PacketDistributor.PLAYER.with(player));
    }

    static void sendToServer(Identifier id, PacketByteBuf buf) {
        channel.send(Envelope.of(id, buf), PacketDistributor.SERVER.noArg());
    }
}
