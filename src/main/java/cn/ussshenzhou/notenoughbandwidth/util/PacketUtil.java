package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class PacketUtil {
    private PacketUtil() {
    }

    public static String getTrueType(Packet<?> packet) {
        return PacketAggregationPacket.resolvePacketType(packet);
    }

    public static Object getTruePacket(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload || packet instanceof CPacketCustomPayload) {
            String type = PacketAggregationPacket.resolvePacketType(packet);
            if (type == null) {
                return packet;
            }
            PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
            if (payload == null) {
                return packet;
            }
            try {
                if (PacketAggregationPacket.CHANNEL_NAME.equals(type)) {
                    return payload;
                }
                if (PayloadRegistry.contains(type)) {
                    PacketBuffer decodeBuffer = new PacketBuffer(Unpooled.buffer(payload.readableBytes()));
                    try {
                        decodeBuffer.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
                        NebPayload nebPayload = PayloadRegistry.decode(type, decodeBuffer);
                        if (nebPayload != null) {
                            return nebPayload;
                        }
                    } finally {
                        decodeBuffer.release();
                    }
                }
            } finally {
                if (payload.refCnt() > 0) {
                    payload.release();
                }
            }
        }
        return packet;
    }

    public static boolean isCustomPayload(Packet<?> packet) {
        return packet instanceof SPacketCustomPayload || packet instanceof CPacketCustomPayload;
    }
}
