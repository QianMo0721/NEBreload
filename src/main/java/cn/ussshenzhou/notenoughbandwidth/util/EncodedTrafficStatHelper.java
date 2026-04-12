package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class EncodedTrafficStatHelper {
    public static final long NEB_CUSTOM_PAYLOAD_MAGIC = 0x4E45425A53544431L;

    private EncodedTrafficStatHelper() {
    }

    public static int estimateRawPacketSize(Packet<?> packet, ByteBuf encodedPacket) {
        if (packet instanceof CPacketCustomPayload || packet instanceof SPacketCustomPayload) {
            return estimateRawCustomPayloadPacketSize(encodedPacket);
        }
        return encodedPacket.readableBytes();
    }

    private static int estimateRawCustomPayloadPacketSize(ByteBuf encodedPacket) {
        PacketBuffer buf = new PacketBuffer(encodedPacket.duplicate());
        buf.readerIndex(0);
        try {
            int totalSize = buf.writerIndex();
            buf.readVarInt();
            String channel = buf.readString(32767);
            int headerSize = buf.readerIndex();
            if (PacketAggregationPacket.TYPE.toString().equals(channel)) {
                return PacketAggregationPacket.estimateRawSizeFromEncodedWrapper(encodedPacket);
            }
            if (buf.readableBytes() < Long.BYTES + 1) {
                return totalSize;
            }
            long magic = buf.readLong();
            if (magic != NEB_CUSTOM_PAYLOAD_MAGIC) {
                return totalSize;
            }
            int rawPayloadSize = buf.readVarInt();
            return headerSize + rawPayloadSize;
        } catch (Exception ignored) {
            return encodedPacket.readableBytes();
        }
    }
}
