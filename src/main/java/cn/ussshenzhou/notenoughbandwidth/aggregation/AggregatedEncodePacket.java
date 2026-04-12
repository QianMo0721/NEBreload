package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AggregatedEncodePacket {
    private final Packet<?> packet;
    @Nullable
    private final ResourceLocation type;
    private final int vanillaPacketId;

    public AggregatedEncodePacket(Packet<?> packet, @Nullable ResourceLocation type, EnumPacketDirection direction) {
        this.packet = packet;
        if (packet instanceof CPacketCustomPayload || packet instanceof SPacketCustomPayload) {
            this.type = type;
            this.vanillaPacketId = -1;
        } else {
            this.type = null;
            try {
                Integer packetId = EnumConnectionState.PLAY.getPacketId(direction, packet);
                this.vanillaPacketId = packetId == null ? -1 : packetId.intValue();
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve vanilla packet id", e);
            }
        }
    }

    public boolean isVanillaPacket() {
        return vanillaPacketId >= 0;
    }

    public int getVanillaPacketId() {
        return vanillaPacketId;
    }

    @Nullable
    public ResourceLocation getType() {
        return type;
    }

    public void encode(ByteBuf buf) {
        try {
            if (packet instanceof CPacketCustomPayload) {
                writeCustomPayloadBody(buf, LegacyCustomPayloadAccessor.getBufferData(packet));
                return;
            }
            if (packet instanceof SPacketCustomPayload) {
                writeCustomPayloadBody(buf, LegacyCustomPayloadAccessor.getBufferData(packet));
                return;
            }
            ((Packet) packet).writePacketData(new PacketBuffer(buf));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode aggregated packet", e);
        }
    }

    public Packet<?> getPacket() {
        return packet;
    }

    private void writeCustomPayloadBody(ByteBuf out, PacketBuffer payload) {
        if (payload == null) {
            return;
        }
        int bodyStart = findPayloadBodyStart(payload);
        int bodyLength = payload.writerIndex() - bodyStart;
        if (bodyLength < 0) {
            bodyStart = payload.readerIndex();
            bodyLength = payload.readableBytes();
        }
        out.writeBytes(payload, bodyStart, bodyLength);
    }

    private int findPayloadBodyStart(PacketBuffer payload) {
        if (payload == null) {
            return 0;
        }
        if (type == null) {
            return payload.readerIndex();
        }
        Integer indexedHeaderEnd = tryConsumeIndexedHeader(payload);
        if (indexedHeaderEnd != null) {
            return indexedHeaderEnd.intValue();
        }
        Integer vanillaHeaderEnd = tryConsumeVanillaHeader(payload);
        if (vanillaHeaderEnd != null) {
            return vanillaHeaderEnd.intValue();
        }
        return payload.readerIndex();
    }

    @Nullable
    private Integer tryConsumeIndexedHeader(PacketBuffer payload) {
        PacketBuffer probe = new PacketBuffer(payload.retainedDuplicate());
        try {
            ResourceLocation decoded = CustomPacketPrefixHelper.getType(probe);
            if (type.equals(decoded)) {
                return Integer.valueOf(probe.readerIndex());
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            probe.release();
        }
    }

    @Nullable
    private Integer tryConsumeVanillaHeader(PacketBuffer payload) {
        PacketBuffer probe = new PacketBuffer(payload.retainedDuplicate());
        try {
            ResourceLocation decoded = probe.readResourceLocation();
            if (type.equals(decoded)) {
                return Integer.valueOf(probe.readerIndex());
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            probe.release();
        }
    }
}
