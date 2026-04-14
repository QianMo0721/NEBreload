package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;

import java.nio.charset.StandardCharsets;

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

    public static int estimateChunkPacketRawSize(Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        return estimatePacketSize(new SPacketChunkData(chunk, 65535), EnumPacketDirection.CLIENTBOUND);
    }

    public static int estimatePacketSize(Packet<?> packet, EnumPacketDirection direction) {
        if (packet == null || direction == null) {
            return 0;
        }
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(buf);
            Integer packetId = EnumConnectionState.PLAY.getPacketId(direction, packet);
            if (packetId == null) {
                return buf.readableBytes();
            }
            return PacketBuffer.getVarIntSize(packetId.intValue()) + buf.readableBytes();
        } catch (Exception ignored) {
            return 0;
        } finally {
            buf.release();
        }
    }

    public static int estimateResourceLocationBytes(ResourceLocation type) {
        if (type == null) {
            return 0;
        }
        return estimateWriteStringBytes(type.toString());
    }

    public static int estimateWriteStringBytes(String value) {
        if (value == null) {
            return 0;
        }
        int utf8Length = value.getBytes(StandardCharsets.UTF_8).length;
        return PacketBuffer.getVarIntSize(utf8Length) + utf8Length;
    }
}
