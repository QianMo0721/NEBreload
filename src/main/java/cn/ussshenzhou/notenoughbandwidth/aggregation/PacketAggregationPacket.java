package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.RawTrafficHelper;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * @author USS_Shenzhou
 * Aggregated packet container for Forge 1.20.1, progressively aligned with the
 * NeoForge payload codec model.
 *
 * Wire format (same high-level idea as NeoForge payload aggregation):
 * <pre>
 * +-------+------+-------+------+-------+------+-------+...
 * | B     | (S)  |  p0   |  s0  |  d0   |  p1  |  s1   |...
 * +-------+------+-------+------+-------+------+-------+...
 *                |----packet 0----+      |----packet 1----+
 *                |---------compressed-----------+
 *
 * B = boolean, whether the payload is compressed
 * S = varint, raw (uncompressed) size – only present when B=true
 * p = indexed payload prefix / packet type prefix
 * s = varint, byte length of this sub-packet's data
 * d = raw packet data
 * </pre>
 */
public class PacketAggregationPacket implements NebPayload {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "packet_aggregation_packet");
    public static final PacketAggregationPacket SAMPLE = new PacketAggregationPacket();
    public static final PayloadCodec<PacketAggregationPacket> CODEC = new PayloadCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, PacketAggregationPacket payload) {
            payload.encode(buf);
        }

        @Override
        public PacketAggregationPacket decode(FriendlyByteBuf buf) {
            return new PacketAggregationPacket(buf);
        }
    };

    private int bakedSize;

    // ---------------------------------------- encode ----------------------------------------
    private final ArrayList<AggregatedEncodePacket> packetsToEncode;
    private final Connection connection;

    private PacketAggregationPacket() {
        this.packetsToEncode = null;
        this.connection = null;
    }

    public PacketAggregationPacket(ArrayList<AggregatedEncodePacket> packetsToEncode, Connection connection) {
        this.packetsToEncode = packetsToEncode;
        this.connection = connection;
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
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
                int rawSizeVarIntSize = FriendlyByteBuf.getVarIntSize(rawSize);
                buffer.writeVarInt(rawSize);
                var compressed = ZstdHelper.compress(connection, rawBuf);
                int compressedSize = compressed.readableBytes();
                logCompressRatio(rawSize, compressedSize);
                buffer.writeBytes(compressed);
                this.bakedSize = compressedSize;
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
     * Format: [vanilla(v)] [header(h)] [size(s)] [data(d)]
     * – if v=true, h is the vanilla PLAY packet id
     * – if v=false, h is the payload type prefix
     * – size covers only the data bytes.
     */
    private void encodeSubPacket(FriendlyByteBuf raw, AggregatedEncodePacket p) {
        // Serialize packet data into a temporary buffer first so we know the size
        var dataBuf = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            p.encode(dataBuf);
            raw.writeBoolean(p.isVanillaPacket());
            if (p.isVanillaPacket()) {
                raw.writeVarInt(p.getVanillaPacketId());
            } else {
                var decodedType = p.getType();
                CustomPacketPrefixHelper.write(connection, decodedType, raw);
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
    public void handle(PayloadContext context) {
        if (context.connection() != null) {
            replay(context.connection(), context.flow());
        }
    }

    public void replay(Connection connection, PacketFlow flow) {
        try {
            var entries = decodeEntries(connection);
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

    private ArrayList<AggregatedDecodePacket> decodeEntries(@Nullable Connection decodingConnection) {
        int payloadReadableBytes = data.readableBytes();
        if (bakedSize > payloadReadableBytes) {
            SimpleStatManager.inRaw(bakedSize - payloadReadableBytes);
        }

        boolean compressed = data.readBoolean();
        FriendlyByteBuf raw;
        if (compressed) {
            if (!ZstdHelper.isAvailable()) {
                throw new IllegalStateException("Received compressed NEB packet but zstd-jni is unavailable on this runtime");
            }
            int rawSize = data.readVarInt();
            FriendlyByteBuf compressedView = new FriendlyByteBuf(data.retainedDuplicate());
            try {
                raw = new FriendlyByteBuf(ZstdHelper.decompress(decodingConnection, compressedView, rawSize));
            } finally {
                compressedView.release();
            }
        } else {
            raw = new FriendlyByteBuf(data.retainedDuplicate());
        }

        SimpleStatManager.inRaw(raw.readableBytes());

        var packetsToHandle = new ArrayList<AggregatedDecodePacket>();
        try {
            while (raw.readableBytes() > 0) {
                deAggregatePacket(decodingConnection, raw, packetsToHandle);
            }
        } finally {
            raw.release();
        }
        return packetsToHandle;
    }

    private void deAggregatePacket(@Nullable Connection decodingConnection, FriendlyByteBuf buf, ArrayList<AggregatedDecodePacket> out) {
        boolean vanilla = buf.readBoolean();
        int vanillaPacketId = -1;
        ResourceLocation type = null;
        if (vanilla) {
            vanillaPacketId = buf.readVarInt();
        } else {
            CustomPacketPrefixHelper.DecodedTypeInfo info = CustomPacketPrefixHelper.readInfo(decodingConnection, buf);
            if (!info.valid()) {
                int invalidSize = buf.readVarInt();
                if (invalidSize >= 0 && invalidSize <= buf.readableBytes()) {
                    buf.skipBytes(invalidSize);
                }
                LogUtils.getLogger().warn("[NEB] Corrupted sub-packet: invalid indexed payload prefix, size={}, readable={}", invalidSize, buf.readableBytes());
                return;
            }
            type = info.type();
        }
        // s – data size
        int size = buf.readVarInt();
        if (size < 0 || size > buf.readableBytes()) {
            LogUtils.getLogger().warn("[NEB] Corrupted sub-packet: size={}, readable={}, vanilla={}, type={}", size, buf.readableBytes(), vanilla, type);
            return;
        }
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
}
