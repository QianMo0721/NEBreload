package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

public class AggregatedDecodePacket {
    private final int vanillaPacketId;
    private final String type;
    private final PacketBuffer data;
    private final NebPayload payload;

    public AggregatedDecodePacket(int vanillaPacketId, String type, PacketBuffer data) {
        this.vanillaPacketId = vanillaPacketId;
        this.type = type;
        this.data = data;
        this.payload = decodeRegisteredPayload(type, data);
    }

    private static NebPayload decodeRegisteredPayload(String type, PacketBuffer data) {
        if (type == null || !PayloadRegistry.contains(type)) {
            return null;
        }
        PacketBuffer payloadBuf = new PacketBuffer(data.retainedDuplicate());
        try {
            return PayloadRegistry.decode(type, payloadBuf);
        } finally {
            payloadBuf.release();
        }
    }

    public PacketBuffer getData() {
        return data;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void replay(NetworkManager connection) {
        Packet packet = createPacket(connection);
        if (packet != null) {
            INetHandler handler = connection.getNetHandler();
            if (handler != null) {
                packet.processPacket(handler);
            }
        }
    }

    public void replay(PayloadContext context) {
        if (context == null || context.connection() == null) {
            return;
        }
        if (payload != null && hasNegotiatedPayloadChannel(context.connection(), type)) {
            context.handle(payload);
            return;
        }
        Packet<?> packet = createPacket(context.connection());
        if (packet == null) {
            return;
        }
        context.handlePacket(packet);
    }

    private Packet<?> createPacket(NetworkManager connection) {
        Packet<?> packet = createVanillaPacket(connection);
        if (packet != null) {
            return packet;
        }
        return createCustomPayloadPacket(connection.getDirection());
    }

    private Packet<?> createVanillaPacket(NetworkManager connection) {
        if (vanillaPacketId < 0) {
            return null;
        }
        try {
            EnumConnectionState state = connection.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get();
            if (state == null) {
                return null;
            }
            Packet<?> created = state.getPacket(connection.getDirection(), vanillaPacketId);
            if (created == null) {
                return null;
            }
            PacketBuffer read = new PacketBuffer(data.retainedDuplicate());
            try {
                created.readPacketData(read);
                if (read.readableBytes() > 0) {
                    return null;
                }
                return created;
            } finally {
                read.release();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private Packet<?> createCustomPayloadPacket(EnumPacketDirection direction) {
        if (type == null) {
            return null;
        }
        if (payload != null) {
            PacketBuffer packetBuf = new PacketBuffer(Unpooled.buffer());
            try {
                PayloadRegistry.encode(packetBuf, payload);
                if (direction == EnumPacketDirection.CLIENTBOUND) {
                    return PacketAggregationPacket.newClientboundPacket(type, packetBuf);
                }
                return PacketAggregationPacket.newServerboundPacket(type, packetBuf);
            } catch (Exception e) {
                packetBuf.release();
                return null;
            }
        }
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
        copy.writeBytes(data, data.readerIndex(), data.readableBytes());
        if (direction == EnumPacketDirection.CLIENTBOUND) {
            return PacketAggregationPacket.newClientboundPacket(type, copy);
        }
        return PacketAggregationPacket.newServerboundPacket(type, copy);
    }

    private boolean hasNegotiatedPayloadChannel(NetworkManager connection, String id) {
        if (id == null) {
            return false;
        }
        NetworkManager current = connection;
        return current != null && (ChannelAttributes.getPayloadSetup(current) == null || ChannelAttributes.hasPayload(current, id));
    }

    public void release() {
        if (data != null && data.refCnt() > 0) {
            data.release();
        }
    }
}
