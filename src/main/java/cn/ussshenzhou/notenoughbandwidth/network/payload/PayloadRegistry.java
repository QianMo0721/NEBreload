package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PayloadRegistry {
    private static final Map<String, PayloadRegistration<?>> REGISTRATIONS = new ConcurrentHashMap<String, PayloadRegistration<?>>();

    private PayloadRegistry() {
    }

    public static <T extends NebPayload> void register(PayloadRegistration<T> registration) {
        PayloadRegistration<?> previous = REGISTRATIONS.putIfAbsent(registration.id(), registration);
        if (previous != null) {
            throw new IllegalStateException("Duplicate payload registration: " + registration.id());
        }
    }

    @Nullable
    public static PayloadRegistration<?> getRegistration(String id) {
        return REGISTRATIONS.get(id);
    }

    public static Collection<PayloadRegistration<?>> registrations() {
        return Collections.unmodifiableCollection(REGISTRATIONS.values());
    }

    public static boolean contains(String id) {
        return REGISTRATIONS.containsKey(id);
    }

    public static NetworkPayloadSetup buildTransportOnlySetup() {
        NetworkPayloadSetup setup = NetworkPayloadSetup.empty();
        PayloadRegistration<?> registration = REGISTRATIONS.get(PacketAggregationPacket.CHANNEL_NAME);
        if (registration != null) {
            setup.register(registration);
        }
        return setup;
    }

    public static NetworkPayloadSetup buildSetup(@Nullable Map<String, String> negotiatedChannels) {
        NetworkPayloadSetup setup = NetworkPayloadSetup.empty();
        if (negotiatedChannels == null || negotiatedChannels.isEmpty()) {
            for (PayloadRegistration<?> registration : REGISTRATIONS.values()) {
                setup.register(registration);
            }
            return setup;
        }
        for (Map.Entry<String, String> entry : negotiatedChannels.entrySet()) {
            setup.register(entry.getKey(), entry.getValue());
        }
        return setup;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends NebPayload> T decode(String id, PacketBuffer buf) {
        PayloadRegistration<T> registration = (PayloadRegistration<T>) REGISTRATIONS.get(id);
        if (registration == null) {
            return null;
        }
        return registration.codec().decode(buf);
    }

    @SuppressWarnings("unchecked")
    public static <T extends NebPayload> void encode(PacketBuffer buf, T payload) {
        PayloadRegistration<T> registration = (PayloadRegistration<T>) REGISTRATIONS.get(payload.type());
        if (registration == null) {
            throw new IllegalStateException("No payload registration for " + payload.type());
        }
        registration.codec().encode(buf, payload);
    }

    public static void send(NetworkManager connection, boolean clientbound, NebPayload payload) {
        if (connection == null || payload == null) {
            return;
        }
        PacketBuffer payloadBuf = new PacketBuffer(Unpooled.buffer());
        PacketBuffer packetBuf = null;
        try {
            encode(payloadBuf, payload);
            packetBuf = new PacketBuffer(Unpooled.buffer());
            packetBuf.writeString(payload.type());
            packetBuf.writeBytes(payloadBuf, payloadBuf.readerIndex(), payloadBuf.readableBytes());
            if (clientbound) {
                connection.sendPacket(PacketAggregationPacket.newClientboundPacket(ModConstants.PAYLOAD_CHANNEL, packetBuf));
            } else {
                connection.sendPacket(PacketAggregationPacket.newServerboundPacket(ModConstants.PAYLOAD_CHANNEL, packetBuf));
            }
        } finally {
            payloadBuf.release();
            if (packetBuf != null && packetBuf.refCnt() > 0) {
                packetBuf.release();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends NebPayload> boolean dispatchPayload(T payload, PayloadContext context) {
        if (payload == null) {
            return false;
        }
        PayloadRegistration<T> registration = (PayloadRegistration<T>) REGISTRATIONS.get(payload.type());
        if (registration == null || !registration.matches(context.clientbound())) {
            return false;
        }
        registration.handler().handle(payload, context);
        return true;
    }

    public static boolean decodeAndHandle(String id, PacketBuffer buf, PayloadContext context) {
        NebPayload payload = decode(id, buf);
        if (payload == null) {
            return false;
        }
        context.handle(payload);
        return true;
    }

    public static boolean handleIncomingCustomPayload(NetworkManager connection, INetHandler listener, String channel, PacketBuffer data, boolean clientbound) {
        if (!(ModConstants.PAYLOAD_CHANNEL).equals(channel) || data == null) {
            return false;
        }
        PacketBuffer frame = new PacketBuffer(data.retainedDuplicate());
        try {
            String id = frame.readString(256);
            PayloadContext context = PayloadContext.of(connection, listener, clientbound);
            return decodeAndHandle(id, frame, context);
        } finally {
            frame.release();
        }
    }

    public static void sendToServer(NebPayload payload, NebPayload... payloads) {
        NetworkManager connection = ClientPayloadBridge.getClientNetworkManager();
        send(connection, false, payload);
        if (payloads != null) {
            for (NebPayload other : payloads) {
                send(connection, false, other);
            }
        }
    }

    public static void sendToPlayer(EntityPlayerMP player, NebPayload payload, NebPayload... payloads) {
        if (player == null || player.connection == null) {
            return;
        }
        NetworkManager connection = player.connection.netManager;
        send(connection, true, payload);
        if (payloads != null) {
            for (NebPayload other : payloads) {
                send(connection, true, other);
            }
        }
    }
}
