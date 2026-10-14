package net.feelix.stagecoach.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record RawPayload(CustomPacketPayload.Type<RawPayload> id, byte[] data) implements CustomPacketPayload {
    /** The destination list is the only large packet, far below this. */
    private static final int MAX_BYTES = 1 << 20;
    private static final Map<Identifier, Type<RawPayload>> IDS = new ConcurrentHashMap<>();

    @Override
    public Type<RawPayload> type() {
        return id;
    }

    public static Type<RawPayload> id(Identifier channel) {
        return IDS.computeIfAbsent(channel, Type::new);
    }

    /** The packet around it already carries the length. */
    public static StreamCodec<FriendlyByteBuf, RawPayload> codec(Identifier channel) {
        Type<RawPayload> id = id(channel);
        return StreamCodec.ofMember((payload, buf) -> buf.writeBytes(payload.data), buf -> {
            int n = buf.readableBytes();
            if (n > MAX_BYTES) throw new IllegalArgumentException("stagecoach payload too large: " + n);
            byte[] data = new byte[n];
            buf.readBytes(data);
            return new RawPayload(id, data);
        });
    }

    public static RawPayload of(Identifier channel, FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return new RawPayload(id(channel), data);
    }

    public Identifier channel() {
        return id.id();
    }

    public FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }
}
