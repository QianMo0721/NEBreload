package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.RawTrafficHelper;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * @author USS_Shenzhou
 * Aggregated packet container for Forge 1.20.1.
 *
 * Wire format (same as NeoForge version):
 * <pre>
 * +-------+------+-------+-------+------+-------+-------+------+...
 * | B     | (S)  |  V0   |  h0   |  s0  |  d0   |  V1   |  h1  |...
 * +-------+------+-------+------+-------+------+-------+...
 *                |----packet 0----+      |----packet 1----+
 *                |---------compressed-----------+
 *
 * B = boolean, whether the payload is compressed
 * S = varint, raw (uncompressed) size – only present when B=true
 * V = boolean, whether this sub-packet is vanilla play packet
 * h = if V=true then varint packet id; otherwise indexed custom payload prefix
 * s = varint, byte length of this sub-packet's data
 * d = raw packet data
 * </pre>
 */
public class PacketAggregationPacket {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "packet_aggregation_packet");

    private int bakedSize;

    // ---------------------------------------- encode ----------------------------------------
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private final ArrayList<AggregatedEncodePacket> packetsToEncode;
    private final Connection connection;

    public PacketAggregationPacket(ArrayList<AggregatedEncodePacket> packetsToEncode, Connection connection) {
        this.packetsToEncode = packetsToEncode;
        this.connection = connection;
    }

    /**
     * Encode all buffered sub-packets into {@code buffer}.
     * Mirrors the NeoForge encode logic but uses Forge 1.20.1 APIs.
     */
    public void encode(FriendlyByteBuf buffer) {
        var rawBuf = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            packetsToEncode.forEach(p -> encodeSubPacket(rawBuf, p));

            int rawSize = rawBuf.readableBytes();
            SimpleStatManager.outRaw(rawSize);

            boolean compress = rawSize >= 32 && ZstdHelper.isAvailable();
            // B
            buffer.writeBoolean(compress);
            if (compress) {
                // S – raw size for decompression
                buffer.writeVarInt(rawSize);
                var compressed = ZstdHelper.compress(connection, rawBuf);
                logCompressRatio(rawSize, compressed.readableBytes());
                buffer.writeBytes(compressed);
                this.bakedSize = compressed.readableBytes();
                compressed.release();
            } else {
                buffer.writeBytes(rawBuf);
                this.bakedSize = rawSize;
            }

            if (ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class).debugLog) {
                LogUtils.getLogger().debug("[NEB] Encoded {} sub-packets, baked size: {}",
                        packetsToEncode.size(), bakedSize);
            }
        } finally {
            rawBuf.release();
        }
    }

    private static void logCompressRatio(int rawSize, int compressedSize) {
        if (ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class).debugLog) {
            LogUtils.getLogger().debug("[NEB] Compressed: {} -> {} bytes ({} %)",
                    rawSize, compressedSize,
                    String.format("%.2f", 100f * compressedSize / rawSize));
        }
    }

    /**
     * Encode a single sub-packet.
     * Format: [prefix(p)] [size(s)] [data(d)]
     * – size covers only the data bytes.
     */
    private static void encodeSubPacket(FriendlyByteBuf raw, AggregatedEncodePacket p) {
        // Serialize packet data into a temporary buffer first so we know the size
        var dataBuf = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            p.encode(dataBuf);
            raw.writeBoolean(p.isVanillaPacket());
            if (p.isVanillaPacket()) {
                raw.writeVarInt(p.getVanillaPacketId());
            } else {
                var prefixHelper = CustomPacketPrefixHelper.get().index(p.getType());
                prefixHelper.save(raw);
                if (prefixHelper.isIndexed() && p.getType() != null) {
                    SimpleStatManager.outRaw(RawTrafficHelper.getWriteUtfCost(p.getType()));
                }
            }
            // s – data length
            raw.writeVarInt(dataBuf.readableBytes());
            // d – data bytes
            raw.writeBytes(dataBuf);
        } finally {
            dataBuf.release();
        }
    }

    // ---------------------------------------- decode ----------------------------------------
    private FriendlyByteBuf data;

    public PacketAggregationPacket(FriendlyByteBuf buffer) {
        this.packetsToEncode = null;
        this.connection = null;
        // Retain a copy of the entire buffer; readerIndex of the source buffer is advanced
        this.data = new FriendlyByteBuf(buffer.retainedDuplicate());
        buffer.readerIndex(buffer.writerIndex());
    }

    // ---------------------------------------- handle ----------------------------------------
    public void handler(Supplier<NetworkEvent.Context> ctxSupplier) {
        handler(ctxSupplier.get());
    }

    public void handler(NetworkEvent.Context context) {
        try {
            var packetsToHandle = decodeEntries();

            if (ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class).debugLog) {
                LogUtils.getLogger().debug("[NEB] Handling {} sub-packets", packetsToHandle.size());
            }

            for (AggregatedDecodePacket pkt : packetsToHandle) {
                try {
                    pkt.handle(context);
                } finally {
                    pkt.getData().release();
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Failed to handle aggregation packet", e);
        } finally {
            if (data != null) {
                data.release();
                data = null;
            }
        }
    }

    public ArrayList<Packet<?>> decodeToPackets(PacketFlow flow) {
        try {
            var entries = decodeEntries();
            var result = new ArrayList<Packet<?>>();
            for (AggregatedDecodePacket entry : entries) {
                try {
                    Packet<?> packet = entry.decode(flow);
                    if (packet != null) {
                        result.add(packet);
                    }
                } finally {
                    entry.getData().release();
                }
            }
            return result;
        } finally {
            if (data != null) {
                data.release();
                data = null;
            }
        }
    }

    public void replay(Connection connection, PacketFlow flow) {
        try {
            var entries = decodeEntries();
            for (AggregatedDecodePacket entry : entries) {
                try {
                    entry.replay(connection, flow);
                } finally {
                    entry.getData().release();
                }
            }
        } finally {
            if (data != null) {
                data.release();
                data = null;
            }
        }
    }

    private ArrayList<AggregatedDecodePacket> decodeEntries() {
        this.bakedSize = data.readableBytes();

        boolean compressed = data.readBoolean();
        FriendlyByteBuf raw;
        if (compressed) {
            if (!ZstdHelper.isAvailable()) {
                throw new IllegalStateException("Received compressed NEB packet but zstd-jni is unavailable on this runtime");
            }
            int rawSize = data.readVarInt();
            raw = new FriendlyByteBuf(ZstdHelper.decompress(null, data.retainedDuplicate(), rawSize));
        } else {
            raw = new FriendlyByteBuf(data.retainedDuplicate());
        }

        SimpleStatManager.inRaw(raw.readableBytes());

        var packetsToHandle = new ArrayList<AggregatedDecodePacket>();
        try {
            while (raw.readableBytes() > 0) {
                deAggregatePacket(raw, packetsToHandle);
            }
        } finally {
            raw.release();
        }
        return packetsToHandle;
    }

    private void deAggregatePacket(FriendlyByteBuf buf, ArrayList<AggregatedDecodePacket> out) {
        boolean vanilla = buf.readBoolean();
        int vanillaPacketId = -1;
        ResourceLocation type = null;
        if (vanilla) {
            vanillaPacketId = buf.readVarInt();
        } else {
            var decodedType = CustomPacketPrefixHelper.read(buf);
            type = decodedType.type();
            if (decodedType.indexed() && type != null) {
                SimpleStatManager.inRaw(RawTrafficHelper.getWriteUtfCost(type));
            }
        }
        // s – data size
        int size = buf.readVarInt();
        // d – data slice (retained so each AggregatedDecodePacket owns its ref)
        var slice = new FriendlyByteBuf(buf.readRetainedSlice(size));
        if (vanilla) {
            out.add(new AggregatedDecodePacket(vanillaPacketId, slice));
        } else {
            out.add(new AggregatedDecodePacket(type, slice));
        }
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
            return encodedPacket.writerIndex();
        }
        return stats.wrapperOverhead() + stats.rawPayloadSize();
    }

    public static int estimateWrapperOverheadFromEncodedWrapper(ByteBuf encodedPacket) {
        ParsedWrapperStats stats = parseEncodedWrapper(encodedPacket);
        if (stats == null) {
            return 0;
        }
        return stats.wrapperOverhead();
    }

    private static ParsedWrapperStats parseEncodedWrapper(ByteBuf encodedPacket) {
        FriendlyByteBuf buf = new FriendlyByteBuf(encodedPacket.duplicate());
        buf.readerIndex(0);
        try {
            int totalSize = buf.writerIndex();
            buf.readVarInt();
            ResourceLocation channel = buf.readResourceLocation();
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

    private record ParsedWrapperStats(int rawPayloadSize, int wrapperOverhead) {
    }
}
