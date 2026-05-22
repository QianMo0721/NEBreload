package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;

public final class PacketAggregationPacket {
    public static final String CHANNEL_NAME = ModConstants.TRANSPORT_CHANNEL;
    private static volatile Field clientboundChannelField;
    private static volatile Field clientboundDataField;
    private static volatile Field serverboundChannelField;
    private static volatile Field serverboundDataField;
    private static volatile Constructor<SPacketCustomPayload> clientboundCustomPayloadCtor;
    private static volatile Constructor<CPacketCustomPayload> serverboundCustomPayloadCtor;

    private PacketAggregationPacket() {
    }

    public static String resolvePacketType(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            return getChannelName((SPacketCustomPayload) packet);
        }
        if (packet instanceof CPacketCustomPayload) {
            return getChannelName((CPacketCustomPayload) packet);
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
        boolean success = false;
        try {
            encodePayload(connection, packets, payload);
            success = true;
            return payload;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Failed to encode aggregation payload", e);
        } finally {
            if (!success && payload.refCnt() > 0) {
                payload.release();
            }
        }
    }

    public static Packet<?> createTransportPacket(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets) {
        PacketBuffer payload = createTransportPayload(connection, packets);
        Packet<?> first = packets.get(0).getPacket();
        if (first instanceof SPacketCustomPayload) {
            return newClientboundPacket(CHANNEL_NAME, payload);
        }
        return newServerboundPacket(CHANNEL_NAME, payload);
    }

    private static void encodePayload(NetworkManager connection, ArrayList<AggregatedEncodePacket> packets, PacketBuffer out) throws IOException {
        PacketBuffer rawBuffer = new PacketBuffer(Unpooled.buffer());
        try {
            for (AggregatedEncodePacket packet : packets) {
                encodeSubPacket(connection, rawBuffer, packet);
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

    private static void encodeSubPacket(NetworkManager connection, PacketBuffer raw, AggregatedEncodePacket packet) throws IOException {
        PacketBuffer body = new PacketBuffer(Unpooled.buffer());
        try {
            packet.encode(body);
            raw.writeBoolean(packet.isVanillaPacket());
            if (packet.isVanillaPacket()) {
                raw.writeVarInt(packet.getVanillaPacketId());
            } else {
                CustomPacketPrefixHelper.write(connection, packet.getType(), raw);
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
        boolean success = false;
        try {
            int rawSize = raw.readableBytes();
            while (raw.readableBytes() > 0) {
                boolean vanilla = raw.readBoolean();
                int vanillaPacketId = -1;
                String type = null;
                if (vanilla) {
                    vanillaPacketId = raw.readVarInt();
                } else {
                    CustomPacketPrefixHelper.DecodedTypeInfo info = CustomPacketPrefixHelper.readInfo(connection, raw);
                    if (!info.valid()) {
                        int invalidSize = raw.readVarInt();
                        if (invalidSize >= 0 && invalidSize <= raw.readableBytes()) {
                            raw.skipBytes(invalidSize);
                        }
                        break;
                    }
                    type = info.type();
                }
                int size = raw.readVarInt();
                if (size < 0 || size > raw.readableBytes()) {
                    break;
                }
                PacketBuffer slice = new PacketBuffer(raw.readBytes(size));
                packets.add(new AggregatedDecodePacket(vanillaPacketId, type, slice));
            }
            SimpleStatManager.inRaw(rawSize);
            success = true;
            return packets;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Failed to decode aggregation payload", e);
        } finally {
            raw.release();
            if (!success) {
                for (AggregatedDecodePacket packet : packets) {
                    if (packet != null) {
                        packet.release();
                    }
                }
                packets.clear();
            }
        }
    }

    public static boolean isTransport(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            return CHANNEL_NAME.equals(getChannelName((SPacketCustomPayload) packet));
        }
        if (packet instanceof CPacketCustomPayload) {
            return CHANNEL_NAME.equals(getChannelName((CPacketCustomPayload) packet));
        }
        return false;
    }

    @Nullable
    public static PacketBuffer copyPayload(Packet<?> packet) {
        PacketBuffer data = getPayloadData(packet);
        if (data == null) {
            return null;
        }
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
        copy.writeBytes(data, data.readerIndex(), data.readableBytes());
        return copy;
    }

    @Nullable
    public static PacketBuffer getPayloadData(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            return getPayloadData((SPacketCustomPayload) packet);
        }
        if (packet instanceof CPacketCustomPayload) {
            return getPayloadData((CPacketCustomPayload) packet);
        }
        return null;
    }

    public static String getChannelName(SPacketCustomPayload packet) {
        return getFieldValue(clientboundChannelField(), packet, String.class);
    }

    public static String getChannelName(CPacketCustomPayload packet) {
        return getFieldValue(serverboundChannelField(), packet, String.class);
    }

    public static PacketBuffer getPayloadData(SPacketCustomPayload packet) {
        return getFieldValue(clientboundDataField(), packet, PacketBuffer.class);
    }

    public static PacketBuffer getPayloadData(CPacketCustomPayload packet) {
        return getFieldValue(serverboundDataField(), packet, PacketBuffer.class);
    }

    public static SPacketCustomPayload newClientboundPacket(String channel, PacketBuffer data) {
        return newPacket(clientboundCustomPayloadCtor(), channel, data);
    }

    public static CPacketCustomPayload newServerboundPacket(String channel, PacketBuffer data) {
        return newPacket(serverboundCustomPayloadCtor(), channel, data);
    }

    private static Field clientboundChannelField() {
        Field field = clientboundChannelField;
        if (field == null) {
            field = findField(SPacketCustomPayload.class, String.class, 0, "channel", "field_149172_a", "a");
            clientboundChannelField = field;
        }
        return field;
    }

    private static Field clientboundDataField() {
        Field field = clientboundDataField;
        if (field == null) {
            field = findField(SPacketCustomPayload.class, PacketBuffer.class, 0, "data", "field_149171_b", "b");
            clientboundDataField = field;
        }
        return field;
    }

    private static Field serverboundChannelField() {
        Field field = serverboundChannelField;
        if (field == null) {
            field = findField(CPacketCustomPayload.class, String.class, 0, "channel", "field_149561_a", "a");
            serverboundChannelField = field;
        }
        return field;
    }

    private static Field serverboundDataField() {
        Field field = serverboundDataField;
        if (field == null) {
            field = findField(CPacketCustomPayload.class, PacketBuffer.class, 0, "data", "field_149560_b", "b");
            serverboundDataField = field;
        }
        return field;
    }

    private static Constructor<SPacketCustomPayload> clientboundCustomPayloadCtor() {
        Constructor<SPacketCustomPayload> ctor = clientboundCustomPayloadCtor;
        if (ctor == null) {
            ctor = findConstructor(SPacketCustomPayload.class);
            clientboundCustomPayloadCtor = ctor;
        }
        return ctor;
    }

    private static Constructor<CPacketCustomPayload> serverboundCustomPayloadCtor() {
        Constructor<CPacketCustomPayload> ctor = serverboundCustomPayloadCtor;
        if (ctor == null) {
            ctor = findConstructor(CPacketCustomPayload.class);
            serverboundCustomPayloadCtor = ctor;
        }
        return ctor;
    }

    private static <T> T newPacket(Constructor<T> ctor, String channel, PacketBuffer data) {
        try {
            return ctor.newInstance(channel, data);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[NEB] Failed to construct custom payload packet", e);
        }
    }

    private static Field findField(Class<?> owner, Class<?> type, int typeIndex, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        int currentIndex = 0;
        Field[] fields = owner.getDeclaredFields();
        for (Field field : fields) {
            if (type.isAssignableFrom(field.getType())) {
                if (currentIndex == typeIndex) {
                    field.setAccessible(true);
                    return field;
                }
                currentIndex++;
            }
        }
        throw new IllegalStateException("[NEB] Failed to resolve " + type.getName() + " field on " + owner.getName());
    }

    private static <T> Constructor<T> findConstructor(Class<T> owner) {
        try {
            Constructor<T> ctor = owner.getDeclaredConstructor(String.class, PacketBuffer.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[NEB] Failed to resolve constructor on " + owner.getName(), e);
        }
    }

    private static <T> T getFieldValue(Field field, Object instance, Class<T> type) {
        try {
            Object value = field.get(instance);
            return type.cast(value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[NEB] Failed to access field " + field.getName(), e);
        }
    }
}
