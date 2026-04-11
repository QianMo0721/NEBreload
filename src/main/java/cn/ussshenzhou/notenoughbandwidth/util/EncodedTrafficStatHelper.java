package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

/**
 * Traffic statistics should compare actual transmitted bytes against the total
 * original payload bytes of the same packet, not just wrapper overhead.
 */
public final class EncodedTrafficStatHelper {
    public static final long NEB_CUSTOM_PAYLOAD_MAGIC = 0x4E45425A53544431L; // "NEBZSTD1"

    private EncodedTrafficStatHelper() {
    }

    public static int estimateRawPacketSize(Packet<?> packet, ByteBuf encodedPacket) {
        if (packet instanceof ClientboundCustomPayloadPacket || packet instanceof ServerboundCustomPayloadPacket) {
            return estimateRawCustomPayloadPacketSize(encodedPacket);
        }
        if (PacketAggregationPacket.TYPE.equals(PacketUtil.getTrueType(packet))) {
            return PacketAggregationPacket.estimateRawSizeFromEncodedWrapper(encodedPacket);
        }
        return encodedPacket.readableBytes();
    }

    private static int estimateRawCustomPayloadPacketSize(ByteBuf encodedPacket) {
        FriendlyByteBuf buf = new FriendlyByteBuf(encodedPacket.duplicate());
        buf.readerIndex(0);
        try {
            int totalSize = buf.writerIndex();
            buf.readVarInt();
            buf.readResourceLocation();
            int headerSize = buf.readerIndex();
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
