package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;

public final class PacketAggregationPacket {
    public static final String CHANNEL_NAME = ModConstants.MOD_ID + ":main";

    private PacketAggregationPacket() {
    }

    public static String resolvePacketType(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            return ((SPacketCustomPayload) packet).getChannelName();
        }
        if (packet instanceof CPacketCustomPayload) {
            return ((CPacketCustomPayload) packet).getChannelName();
        }
        return packet.getClass().getName();
    }

    public static int estimatePayloadSize(ArrayList<AggregatedEncodePacket> packets) {
        int raw = 0;
        for (AggregatedEncodePacket packet : packets) {
            raw += Math.max(0, packet.getEncodedSizeEstimate());
        }
        int wrapped = 1 + raw;
        if (raw >= 32 && ZstdHelper.isAvailable()) {
            wrapped += PacketBuffer.getVarIntSize(raw);
        }
        return wrapped;
    }

    public static PacketBuffer createTransportPayload(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        try {
            encodePayload(connection, packets, payload);
            return payload;
        } catch (Exception e) {
            payload.release();
            throw new RuntimeException("[NEB] Failed to encode aggregation payload", e);
        }
    }

    public static Packet<?> createTransportPacket(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets) {
        PacketBuffer payload = createTransportPayload(connection, packets);
        Packet<?> first = packets.get(0).getPacket();
        if (first instanceof SPacketCustomPayload) {
            return new SPacketCustomPayload(CHANNEL_NAME, payload);
        }
        return new CPacketCustomPayload(CHANNEL_NAME, payload);
    }

    private static void encodePayload(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets, PacketBuffer out) throws IOException {
        PacketBuffer rawBuffer = new PacketBuffer(Unpooled.buffer());
        try {
            for (AggregatedEncodePacket packet : packets) {
                encodeSubPacket(rawBuffer, packet);
            }
            int rawSize = rawBuffer.readableBytes();
            SimpleStatManager.outRaw(rawSize);
            boolean compress = rawSize >= 32 && ZstdHelper.isAvailable();
            out.writeBoolean(compress);
            if (compress) {
                out.writeVarInt(rawSize);
                ByteBuf compressed = ZstdHelper.compress(connection, rawBuffer);
                try {
                    out.writeBytes(compressed, compressed.readerIndex(), compressed.readableBytes());
                } finally {
                    compressed.release();
                }
            } else {
                out.writeBytes(rawBuffer, rawBuffer.readerIndex(), rawBuffer.readableBytes());
            }
        } finally {
            rawBuffer.release();
        }
    }

    private static void encodeSubPacket(PacketBuffer raw, AggregatedEncodePacket packet) throws IOException {
        PacketBuffer body = new PacketBuffer(Unpooled.buffer());
        try {
            packet.encode(body);
            raw.writeBoolean(packet.isVanillaPacket());
            if (packet.isVanillaPacket()) {
                raw.writeVarInt(packet.getVanillaPacketId());
            } else {
                raw.writeString(packet.getType());
            }
            raw.writeVarInt(body.readableBytes());
            raw.writeBytes(body, body.readerIndex(), body.readableBytes());
        } finally {
            body.release();
        }
    }

    public static ArrayList<AggregatedDecodePacket> decode(NetworkManager connection, PacketBuffer payload) {
        boolean compressed = payload.readBoolean();
        PacketBuffer raw;
        if (compressed) {
            int originalSize = payload.readVarInt();
            ByteBuf compressedBuf = payload.readBytes(payload.readableBytes());
            try {
                raw = new PacketBuffer(ZstdHelper.decompress(connection, compressedBuf, originalSize));
            } finally {
                compressedBuf.release();
            }
        } else {
            raw = new PacketBuffer(payload.readBytes(payload.readableBytes()));
        }

        ArrayList<AggregatedDecodePacket> packets = new ArrayList<AggregatedDecodePacket>();
        try {
            int rawSize = raw.readableBytes();
            while (raw.readableBytes() > 0) {
                boolean vanilla = raw.readBoolean();
                int vanillaPacketId = -1;
                String type = null;
                if (vanilla) {
                    vanillaPacketId = raw.readVarInt();
                } else {
                    type = raw.readString(256);
                }
                int size = raw.readVarInt();
                if (size < 0 || size > raw.readableBytes()) {
                    break;
                }
                PacketBuffer slice = new PacketBuffer(raw.readBytes(size));
                packets.add(new AggregatedDecodePacket(vanillaPacketId, type, slice));
            }
            SimpleStatManager.inRaw(rawSize);
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Failed to decode aggregation payload", e);
        } finally {
            raw.release();
        }
        return packets;
    }

    public static boolean isTransport(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            return CHANNEL_NAME.equals(((SPacketCustomPayload) packet).getChannelName());
        }
        if (packet instanceof CPacketCustomPayload) {
            return CHANNEL_NAME.equals(((CPacketCustomPayload) packet).getChannelName());
        }
        return false;
    }

    @Nullable
    public static PacketBuffer copyPayload(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            PacketBuffer data = ((SPacketCustomPayload) packet).getBufferData();
            return data == null ? null : new PacketBuffer(data.retainedDuplicate());
        }
        if (packet instanceof CPacketCustomPayload) {
            PacketBuffer data = ((CPacketCustomPayload) packet).getBufferData();
            return data == null ? null : new PacketBuffer(data.retainedDuplicate());
        }
        return null;
    }
}
