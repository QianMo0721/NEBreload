package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class CustomPayloadCodecHelper {
    private static final long NEB_CUSTOM_PAYLOAD_MAGIC = 0x4E45425A53544431L;
    private static final int MIN_COMPRESS_SIZE = 32;

    private CustomPayloadCodecHelper() {
    }

    public static CPacketCustomPayload tryCompress(CPacketCustomPayload packet) {
        PacketBuffer payload = LegacyCustomPayloadAccessor.getBufferData(packet);
        String channelName = LegacyCustomPayloadAccessor.getChannelName(packet);
        if (!shouldCompress(channelName, payload)) {
            return null;
        }
        return LegacyCustomPayloadAccessor.createCPacket(channelName, compressPayload(payload));
    }

    public static SPacketCustomPayload tryCompress(SPacketCustomPayload packet) {
        PacketBuffer payload = LegacyCustomPayloadAccessor.getBufferData(packet);
        String channelName = LegacyCustomPayloadAccessor.getChannelName(packet);
        if (!shouldCompress(channelName, payload)) {
            return null;
        }
        return LegacyCustomPayloadAccessor.createSPacket(channelName, compressPayload(payload));
    }

    public static CPacketCustomPayload tryDecompress(CPacketCustomPayload packet) {
        PacketBuffer payload = LegacyCustomPayloadAccessor.getBufferData(packet);
        PacketBuffer decompressed = tryDecompressPayload(payload);
        if (decompressed == null) {
            return null;
        }
        return LegacyCustomPayloadAccessor.createCPacket(LegacyCustomPayloadAccessor.getChannelName(packet), decompressed);
    }

    public static SPacketCustomPayload tryDecompress(SPacketCustomPayload packet) {
        PacketBuffer payload = LegacyCustomPayloadAccessor.getBufferData(packet);
        PacketBuffer decompressed = tryDecompressPayload(payload);
        if (decompressed == null) {
            return null;
        }
        return LegacyCustomPayloadAccessor.createSPacket(LegacyCustomPayloadAccessor.getChannelName(packet), decompressed);
    }

    private static boolean shouldCompress(String identifier, PacketBuffer payload) {
        if (identifier == null || !ZstdHelper.isAvailable() || PacketAggregationPacket.TYPE.toString().equals(identifier)) {
            return false;
        }
        if (payload == null) {
            return false;
        }
        if (NotEnoughBandwidthLegacyConfig.skipType(identifier)) {
            return false;
        }
        if (payload.readableBytes() < MIN_COMPRESS_SIZE) {
            return false;
        }
        return !isNebCompressed(payload);
    }

    private static PacketBuffer compressPayload(PacketBuffer payload) {
        byte[] raw = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), raw);
        byte[] compressed = ZstdHelper.compress(raw);
        if (compressed.length + Long.BYTES + 5 >= raw.length) {
            return wrapRaw(raw);
        }
        PacketBuffer result = new PacketBuffer(Unpooled.buffer(Long.BYTES + 5 + compressed.length));
        result.writeLong(NEB_CUSTOM_PAYLOAD_MAGIC);
        result.writeVarInt(raw.length);
        result.writeBytes(compressed);
        return result;
    }

    private static PacketBuffer tryDecompressPayload(PacketBuffer payload) {
        if (payload == null) {
            return null;
        }
        if (!isNebCompressed(payload)) {
            return null;
        }
        PacketBuffer probe = new PacketBuffer(payload.retainedDuplicate());
        try {
            probe.readLong();
            int rawSize = probe.readVarInt();
            byte[] compressed = new byte[probe.readableBytes()];
            probe.readBytes(compressed);
            return wrapRaw(ZstdHelper.decompress(compressed, rawSize));
        } catch (Exception e) {
            return null;
        } finally {
            probe.release();
        }
    }

    private static boolean isNebCompressed(ByteBuf payload) {
        if (payload.readableBytes() < Long.BYTES + 1) {
            return false;
        }
        return payload.duplicate().readLong() == NEB_CUSTOM_PAYLOAD_MAGIC;
    }

    private static PacketBuffer wrapRaw(byte[] raw) {
        PacketBuffer result = new PacketBuffer(Unpooled.buffer(raw.length));
        result.writeBytes(raw);
        return result;
    }
}
