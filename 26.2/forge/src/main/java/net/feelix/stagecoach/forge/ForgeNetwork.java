package net.feelix.stagecoach.forge;

import io.netty.buffer.Unpooled;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.platform.Platform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
        static Envelope of(Identifier id, FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return new Envelope(id, bytes);
        }

        FriendlyByteBuf buf() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        }

        void write(FriendlyByteBuf out) {
            out.writeIdentifier(id);
            out.writeByteArray(data);
        }

        static Envelope read(FriendlyByteBuf in) {
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
            ServerPlayer player = ctx.getSender();
            Platform.ServerReceiver receiver = SERVER_RECEIVERS.get(message.id());
            if (player != null && receiver != null) receiver.receive(player.level().getServer(), player, message.buf());
        } else if (FMLEnvironment.dist.isClient()) {
            ForgeClientNetwork.receive(message);
        }
        ctx.setPacketHandled(true);
    }

    static void sendToPlayer(ServerPlayer player, Identifier id, FriendlyByteBuf buf) {
        channel.send(Envelope.of(id, buf), PacketDistributor.PLAYER.with(player));
    }

    static void sendToServer(Identifier id, FriendlyByteBuf buf) {
        channel.send(Envelope.of(id, buf), PacketDistributor.SERVER.noArg());
    }
}
