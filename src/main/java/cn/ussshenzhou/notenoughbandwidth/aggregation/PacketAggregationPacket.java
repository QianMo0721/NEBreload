package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.network.NebPayloads;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

/**
 * @author USS_Shenzhou
 */
public class PacketAggregationPacket {
    private int bakedSize;
    private final ArrayList<AggregatedEncodePacket> packetsToEncode;
    private ClientConnection connection;
    private PacketByteBuf data;

    public PacketAggregationPacket(ArrayList<AggregatedEncodePacket> packetsToEncode, ClientConnection connection) {
        this.packetsToEncode = packetsToEncode;
        this.connection = connection;
    }

    public PacketAggregationPacket(PacketByteBuf buffer) {
        this.packetsToEncode = null;
        this.data = new PacketByteBuf(buffer.copy());
    }

    public NebPayloads.PacketAggregationPayload toPayload() {
        PacketByteBuf buffer = new PacketByteBuf(ByteBufAllocator.DEFAULT.buffer());
        encode(buffer);
        return new NebPayloads.PacketAggregationPayload(buffer);
    }

    public void encode(PacketByteBuf buffer) {
        PacketByteBuf rawBuf = new PacketByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            packetsToEncode.forEach(packet -> encodePackets(rawBuf, packet));

            int rawSize = rawBuf.readableBytes();
            boolean compress = rawSize >= 32;
            buffer.writeBoolean(compress);
            if (compress) {
                buffer.writeVarInt(rawSize);
                PacketByteBuf compressedBuf = new PacketByteBuf(ZstdHelper.compress(connection, rawBuf));
                try {
                    this.bakedSize = compressedBuf.readableBytes();
                    buffer.writeBytes(compressedBuf, compressedBuf.readerIndex(), compressedBuf.readableBytes());
                } finally {
                    compressedBuf.release();
                }
            } else {
                this.bakedSize = rawSize;
                buffer.writeBytes(rawBuf, rawBuf.readerIndex(), rawBuf.readableBytes());
            }
            SimpleStatManager.outRaw(rawSize);
        } finally {
            rawBuf.release();
        }
    }

    private void encodePackets(PacketByteBuf raw, AggregatedEncodePacket packet) {
        Identifier type = packet.type;
        if (NotEnoughBandwidthConfig.skipType(type.toString())) {
            raw.writeIdentifier(type);
        } else {
            CustomPacketPrefixHelper.get().index(type).save(raw);
        }
        PacketByteBuf d = new PacketByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            packet.encode(d);
            raw.writeVarInt(d.readableBytes());
            raw.writeBytes(d, d.readerIndex(), d.readableBytes());
        } finally {
            d.release();
        }
    }

    public ArrayList<AggregatedDecodePacket> decode(ClientConnection connection) {
        this.connection = connection;
        PacketByteBuf read = this.data;
        if (read == null) {
            return new ArrayList<>();
        }

        PacketByteBuf raw = null;
        try {
            boolean compressed = read.readBoolean();
            if (compressed) {
                int size = read.readVarInt();
                raw = new PacketByteBuf(ZstdHelper.decompress(connection, read.copy(), size));
            } else {
                raw = new PacketByteBuf(read.copy());
            }

            int rawSize = raw.readableBytes();
            ArrayList<AggregatedDecodePacket> packetsToHandle = new ArrayList<>();
            while (raw.readableBytes() > 0) {
                Identifier type = tryDecodeHeader(raw);
                int size = raw.readVarInt();
                PacketByteBuf slice = new PacketByteBuf(raw.readRetainedSlice(size));
                packetsToHandle.add(new AggregatedDecodePacket(type, slice));
            }
            SimpleStatManager.inRaw(rawSize);
            return packetsToHandle;
        } finally {
            if (raw != null) {
                raw.release();
            }
        }
    }

    private static Identifier tryDecodeHeader(PacketByteBuf raw) {
        raw.markReaderIndex();
        try {
            Identifier vanillaType = raw.readIdentifier();
            if (NotEnoughBandwidthConfig.skipType(vanillaType.toString())) {
                return vanillaType;
            }
            raw.resetReaderIndex();
        } catch (Exception ignored) {
            raw.resetReaderIndex();
        }

        Identifier indexedType = CustomPacketPrefixHelper.getType(raw);
        if (indexedType != null) {
            return indexedType;
        }

        raw.resetReaderIndex();
        return raw.readIdentifier();
    }

    public static int estimateRawSize(PacketByteBuf data, int baked) {
        PacketByteBuf copy = new PacketByteBuf(data.copy());
        try {
            boolean compressed = copy.readBoolean();
            if (compressed) {
                return copy.readVarInt();
            }
            return Math.max(0, baked - 1);
        } catch (IndexOutOfBoundsException ignored) {
            return baked;
        } finally {
            copy.release();
        }
    }

    public static boolean isAggregationPacket(Packet<?> packet) {
        return PacketUtilCompat.isAggregationPacket(packet);
    }

    public static boolean shouldSkip(Packet<?> packet) {
        return NotEnoughBandwidthConfig.skipType(cn.ussshenzhou.notenoughbandwidth.util.PacketUtil.getTrueType(packet).toString());
    }

    public int getBakedSize() {
        return bakedSize;
    }

    public void setBakedSize(int bakedSize) {
        this.bakedSize = bakedSize;
    }

    private static final class PacketUtilCompat {
        private static boolean isAggregationPacket(Packet<?> packet) {
            Identifier type = cn.ussshenzhou.notenoughbandwidth.util.PacketUtil.getTrueType(packet);
            return NebPayloads.PACKET_AGGREGATION_ID.equals(type);
        }
    }
}
