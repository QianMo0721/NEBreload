package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacy;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;

/**
 * 1.12.2 聚合包封装。
 */
public class PacketAggregationPacket {
    // 1.12.2 的 CPacket/SPacketCustomPayload 在反序列化时将 channel 长度限制为 20 个字符，
    // 因此不能直接沿用新版本里更长的 packet_aggregation_packet 标识。
    // 此处用了很短的标识agg
    public static final ResourceLocation TYPE = new ResourceLocation(ModConstants.MOD_ID, "agg");

    private final ArrayList<AggregatedEncodePacket> packetsToEncode;
    private final NetworkManager connection;
    private PacketBuffer data;
    private int bakedSize;

    public PacketAggregationPacket(ArrayList<AggregatedEncodePacket> packetsToEncode, NetworkManager connection) {
        this.packetsToEncode = packetsToEncode;
        this.connection = connection;
    }

    public PacketAggregationPacket(PacketBuffer buffer) {
        this.packetsToEncode = null;
        this.connection = null;
        this.data = new PacketBuffer(buffer.copy());
    }

    public void encode(PacketBuffer target) {
        PacketBuffer raw = new PacketBuffer(Unpooled.buffer());
        try {
            for (AggregatedEncodePacket packet : packetsToEncode) {
                encodeSubPacket(raw, packet);
            }
            int rawSize = raw.readableBytes();
            boolean compress = rawSize >= 32 && ZstdHelper.isAvailable();
            target.writeBoolean(compress);
            if (compress) {
                target.writeVarInt(rawSize);
                ByteBuf compressed = ZstdHelper.compress(connection, raw);
                target.writeBytes(compressed, compressed.readerIndex(), compressed.readableBytes());
                bakedSize = compressed.readableBytes();
                compressed.release();
            } else {
                target.writeBytes(raw, raw.readerIndex(), raw.readableBytes());
                bakedSize = rawSize;
            }
            if (ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class).debugLog) {
                NotEnoughBandwidthLegacy.LOGGER.debug("[NEB] Encoded {} packets into {} bytes", packetsToEncode.size(), bakedSize);
            }
        } finally {
            raw.release();
        }
    }

    private static void encodeSubPacket(PacketBuffer raw, AggregatedEncodePacket packet) {
        PacketBuffer dataBuf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.encode(dataBuf);
            raw.writeBoolean(packet.isVanillaPacket());
            if (packet.isVanillaPacket()) {
                raw.writeVarInt(packet.getVanillaPacketId());
            } else {
                CustomPacketPrefixHelper.get().index(packet.getType()).save(raw);
            }
            raw.writeVarInt(dataBuf.readableBytes());
            raw.writeBytes(dataBuf, dataBuf.readerIndex(), dataBuf.readableBytes());
        } finally {
            dataBuf.release();
        }
    }

    public ArrayList<Packet<?>> decodeToPackets(EnumPacketDirection direction) {
        ArrayList<Packet<?>> result = new ArrayList<Packet<?>>();
        ArrayList<AggregatedDecodePacket> entries = decodeEntries();
        for (AggregatedDecodePacket entry : entries) {
            try {
                Packet<?> packet = entry.decode(direction);
                if (packet != null) {
                    result.add(packet);
                }
            } finally {
                entry.getData().release();
            }
        }
        return result;
    }

    public void handle(INetHandler listener) {
        EnumPacketDirection direction = listener instanceof net.minecraft.network.play.INetHandlerPlayServer
                ? EnumPacketDirection.SERVERBOUND
                : EnumPacketDirection.CLIENTBOUND;
        ArrayList<AggregatedDecodePacket> entries = decodeEntries();
        for (AggregatedDecodePacket entry : entries) {
            try {
                entry.handle(listener);
            } finally {
                entry.getData().release();
            }
        }
    }

    private ArrayList<AggregatedDecodePacket> decodeEntries() {
        ArrayList<AggregatedDecodePacket> packets = new ArrayList<AggregatedDecodePacket>();
        if (data == null) {
            return packets;
        }
        PacketBuffer source = data;
        data = null;
        try {
            boolean compressed = source.readBoolean();
            PacketBuffer raw;
            if (compressed) {
                int rawSize = source.readVarInt();
                raw = new PacketBuffer(ZstdHelper.decompress(null, source, rawSize));
            } else {
                raw = new PacketBuffer(source.copy(source.readerIndex(), source.readableBytes()));
            }
            try {
                SimpleStatManager.inRaw(raw.readableBytes());
                while (raw.isReadable()) {
                    boolean vanilla = raw.readBoolean();
                    if (vanilla) {
                        int packetId = raw.readVarInt();
                        int size = raw.readVarInt();
                        packets.add(new AggregatedDecodePacket(packetId, raw.readBytes(size)));
                    } else {
                        ResourceLocation type = CustomPacketPrefixHelper.getType(raw);
                        int size = raw.readVarInt();
                        packets.add(new AggregatedDecodePacket(type, raw.readBytes(size)));
                    }
                }
            } finally {
                raw.release();
            }
        } finally {
            source.release();
        }
        return packets;
    }

    public int getBakedSize() {
        return bakedSize;
    }

    public void setBakedSize(int bakedSize) {
        this.bakedSize = bakedSize;
    }

    public static int estimateRawSizeFromEncodedWrapper(ByteBuf encodedPacket) {
        ParsedWrapperStats stats = parseEncodedWrapper(encodedPacket);
        if (stats == null) {
            return encodedPacket.readableBytes();
        }
        return stats.wrapperOverhead + stats.rawPayloadSize;
    }

    public static int estimateWrapperOverheadFromEncodedWrapper(ByteBuf encodedPacket) {
        ParsedWrapperStats stats = parseEncodedWrapper(encodedPacket);
        return stats == null ? 0 : stats.wrapperOverhead;
    }

    private static ParsedWrapperStats parseEncodedWrapper(ByteBuf encodedPacket) {
        PacketBuffer buf = new PacketBuffer(encodedPacket.duplicate());
        buf.readerIndex(0);
        try {
            int totalSize = buf.writerIndex();
            buf.readVarInt();
            ResourceLocation channel = new ResourceLocation(buf.readString(32767));
            if (!TYPE.equals(channel)) {
                return null;
            }
            boolean compressed = buf.readBoolean();
            if (compressed) {
                int rawPayloadSize = buf.readVarInt();
                int compressedPayloadSize = buf.readableBytes();
                return new ParsedWrapperStats(rawPayloadSize, totalSize - compressedPayloadSize);
            }
            int rawPayloadSize = buf.readableBytes();
            return new ParsedWrapperStats(rawPayloadSize, totalSize - rawPayloadSize);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ParsedWrapperStats {
        private final int rawPayloadSize;
        private final int wrapperOverhead;

        private ParsedWrapperStats(int rawPayloadSize, int wrapperOverhead) {
            this.rawPayloadSize = rawPayloadSize;
            this.wrapperOverhead = wrapperOverhead;
        }
    }
}
