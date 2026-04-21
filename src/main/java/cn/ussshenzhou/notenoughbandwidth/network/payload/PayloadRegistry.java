package cn.ussshenzhou.notenoughbandwidth.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PayloadRegistry {
    private static final Map<ResourceLocation, PayloadRegistration<?>> REGISTRATIONS = new ConcurrentHashMap<>();

    private PayloadRegistry() {
    }

    public static <T extends NebPayload> void register(PayloadRegistration<T> registration) {
        var previous = REGISTRATIONS.putIfAbsent(registration.id(), registration);
        if (previous != null) {
            throw new IllegalStateException("Duplicate payload registration: " + registration.id());
        }
    }

    @Nullable
    public static PayloadRegistration<?> getRegistration(ResourceLocation id) {
        return REGISTRATIONS.get(id);
    }

    public static Collection<PayloadRegistration<?>> registrations() {
        return REGISTRATIONS.values();
    }

    public static boolean contains(ResourceLocation id) {
        return REGISTRATIONS.containsKey(id);
    }

    public static NetworkPayloadSetup buildSetup(@Nullable Map<ResourceLocation, String> negotiatedChannels) {
        NetworkPayloadSetup setup = NetworkPayloadSetup.empty();
        for (PayloadRegistration<?> registration : REGISTRATIONS.values()) {
            if (negotiatedChannels == null || negotiatedChannels.containsKey(registration.id())) {
                setup.register(registration);
            }
        }
        return setup;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends NebPayload> T decode(ResourceLocation id, FriendlyByteBuf buf) {
        var registration = (PayloadRegistration<T>) REGISTRATIONS.get(id);
        if (registration == null) {
            return null;
        }
        return registration.codec().decode(buf);
    }

    @SuppressWarnings("unchecked")
    public static <T extends NebPayload> void encode(FriendlyByteBuf buf, T payload) {
        var registration = (PayloadRegistration<T>) REGISTRATIONS.get(payload.type());
        if (registration == null) {
            throw new IllegalStateException("No payload registration for " + payload.type());
        }
        registration.codec().encode(buf, payload);
    }

    public static void send(Connection connection, PacketFlow flow, NebPayload payload) {
        if (connection == null || payload == null) {
            return;
        }
        FriendlyByteBuf payloadBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode(payloadBuf, payload);
            FriendlyByteBuf packetBuf = new FriendlyByteBuf(Unpooled.buffer());
            if (flow == PacketFlow.CLIENTBOUND) {
                try {
                    packetBuf.writeResourceLocation(payload.type());
                    packetBuf.writeBytes(payloadBuf, payloadBuf.readerIndex(), payloadBuf.readableBytes());
                    connection.send(new ClientboundCustomPayloadPacket(packetBuf));
                } finally {
                    packetBuf.release();
                }
            } else {
                try {
                    packetBuf.writeResourceLocation(payload.type());
                    packetBuf.writeBytes(payloadBuf, payloadBuf.readerIndex(), payloadBuf.readableBytes());
                    connection.send(new ServerboundCustomPayloadPacket(packetBuf));
                } finally {
                    packetBuf.release();
                }
            }
        } finally {
            payloadBuf.release();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends NebPayload> boolean dispatchPayload(T payload, PayloadContext context) {
        if (payload == null) {
            return false;
        }
        var registration = (PayloadRegistration<T>) REGISTRATIONS.get(payload.type());
        if (registration == null || !registration.matches(context.flow())) {
            return false;
        }
        registration.handler().handle(payload, context);
        return true;
    }

    public static boolean decodeAndHandle(ResourceLocation id, FriendlyByteBuf buf, PayloadContext context) {
        NebPayload payload = decode(id, buf);
        if (payload == null) {
            return false;
        }
        context.handle(payload);
        return true;
    }
}
