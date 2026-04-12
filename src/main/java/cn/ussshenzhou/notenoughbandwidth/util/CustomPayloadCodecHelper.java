package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

/**
 * Transparent zstd wrapper for vanilla game custom payload packets.
 *
 * <p>This avoids touching packet constructor/write bytecode directly, which is
 * far more compatible with optimization mods that also patch the same classes.</p>
 */
public final class CustomPayloadCodecHelper {
    private static final long NEB_CUSTOM_PAYLOAD_MAGIC = 0x4E45425A53544431L; // "NEBZSTD1"
    private static final int MIN_COMPRESS_SIZE = 32;

    private CustomPayloadCodecHelper() {
    }

    public static ClientboundCustomPayloadPacket tryCompress(ClientboundCustomPayloadPacket packet) {
        FriendlyByteBuf payload = packet.getData();
        if (!shouldCompress(packet.getIdentifier(), payload)) {
            return null;
        }
        return new ClientboundCustomPayloadPacket(packet.getIdentifier(), compressPayload(payload));
    }

    public static ServerboundCustomPayloadPacket tryCompress(ServerboundCustomPayloadPacket packet) {
        FriendlyByteBuf payload = packet.getData();
        if (!shouldCompress(packet.getIdentifier(), payload)) {
            return null;
        }
        return new ServerboundCustomPayloadPacket(packet.getIdentifier(), compressPayload(payload));
    }

    public static ClientboundCustomPayloadPacket tryDecompress(ClientboundCustomPayloadPacket packet) {
        FriendlyByteBuf payload = packet.getData();
        FriendlyByteBuf decompressed = tryDecompressPayload(payload);
        if (decompressed == null) {
            return null;
        }
        return new ClientboundCustomPayloadPacket(packet.getIdentifier(), decompressed);
    }

    public static ServerboundCustomPayloadPacket tryDecompress(ServerboundCustomPayloadPacket packet) {
        FriendlyByteBuf payload = packet.getData();
        FriendlyByteBuf decompressed = tryDecompressPayload(payload);
        if (decompressed == null) {
            return null;
        }
        return new ServerboundCustomPayloadPacket(packet.getIdentifier(), decompressed);
    }

    private static boolean shouldCompress(ResourceLocation identifier, FriendlyByteBuf payload) {
        if (identifier == null || !ZstdHelper.isAvailable() || PacketAggregationPacket.TYPE.equals(identifier)) {
            return false;
        }
        if (NotEnoughBandwidthLegacyConfig.skipType(identifier.toString())) {
            return false;
        }
        if (payload.readableBytes() < MIN_COMPRESS_SIZE) {
            return false;
        }
        return !isNebCompressed(payload);
    }

    private static FriendlyByteBuf compressPayload(FriendlyByteBuf payload) {
        byte[] raw = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), raw);
        byte[] compressed = ZstdHelper.compress(raw);
        if (compressed.length + Long.BYTES + 5 >= raw.length) {
            return wrapRaw(raw);
        }
        FriendlyByteBuf result = new FriendlyByteBuf(Unpooled.buffer(Long.BYTES + 5 + compressed.length));
        result.writeLong(NEB_CUSTOM_PAYLOAD_MAGIC);
        result.writeVarInt(raw.length);
        result.writeBytes(compressed);
        return result;
    }

    private static FriendlyByteBuf tryDecompressPayload(FriendlyByteBuf payload) {
        if (!isNebCompressed(payload)) {
            return null;
        }
        FriendlyByteBuf probe = new FriendlyByteBuf(payload.retainedDuplicate());
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
        ByteBuf probe = payload.duplicate();
        return probe.readLong() == NEB_CUSTOM_PAYLOAD_MAGIC;
    }

    private static FriendlyByteBuf wrapRaw(byte[] raw) {
        FriendlyByteBuf result = new FriendlyByteBuf(Unpooled.buffer(raw.length));
        result.writeBytes(raw);
        return result;
    }
}
