package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public class AggregatedDecodePacket {
    private final int vanillaPacketId;
    private final String type;
    private final PacketBuffer data;

    public AggregatedDecodePacket(int vanillaPacketId, String type, PacketBuffer data) {
        this.vanillaPacketId = vanillaPacketId;
        this.type = type;
        this.data = data;
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
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
        copy.writeBytes(data, data.readerIndex(), data.readableBytes());
        if (direction == EnumPacketDirection.CLIENTBOUND) {
            return new SPacketCustomPayload(type, copy);
        }
        return new CPacketCustomPayload(type, copy);
    }

    public void release() {
        if (data != null && data.refCnt() > 0) {
            data.release();
        }
    }
}
