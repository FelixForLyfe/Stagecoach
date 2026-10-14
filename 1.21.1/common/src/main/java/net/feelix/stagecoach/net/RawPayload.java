package net.feelix.stagecoach.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record RawPayload(CustomPayload.Id<RawPayload> id, byte[] data) implements CustomPayload {
    /** The destination list is the only large packet, far below this. */
    private static final int MAX_BYTES = 1 << 20;
    private static final Map<Identifier, Id<RawPayload>> IDS = new ConcurrentHashMap<>();

    @Override
    public Id<RawPayload> getId() {
        return id;
    }

    public static Id<RawPayload> id(Identifier channel) {
        return IDS.computeIfAbsent(channel, Id::new);
    }

    /** The packet around it already carries the length. */
    public static PacketCodec<PacketByteBuf, RawPayload> codec(Identifier channel) {
        Id<RawPayload> id = id(channel);
        return PacketCodec.of((payload, buf) -> buf.writeBytes(payload.data), buf -> {
            int n = buf.readableBytes();
            if (n > MAX_BYTES) throw new IllegalArgumentException("stagecoach payload too large: " + n);
            byte[] data = new byte[n];
            buf.readBytes(data);
            return new RawPayload(id, data);
        });
    }

    public static RawPayload of(Identifier channel, PacketByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return new RawPayload(id(channel), data);
    }

    public Identifier channel() {
        return id.id();
    }

    public PacketByteBuf buf() {
        return new PacketByteBuf(Unpooled.wrappedBuffer(data));
    }
}
