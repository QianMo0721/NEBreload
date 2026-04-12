package cn.ussshenzhou.notenoughbandwidth.aggregation;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;

import javax.annotation.Nullable;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AggregatedDecodePacket {
    @Nullable
    private final ResourceLocation type;
    private final int vanillaPacketId;
    private final ByteBuf data;

    public AggregatedDecodePacket(ResourceLocation type, ByteBuf data) {
        this.type = type;
        this.vanillaPacketId = -1;
        this.data = data;
    }

    public AggregatedDecodePacket(int vanillaPacketId, ByteBuf data) {
        this.type = null;
        this.vanillaPacketId = vanillaPacketId;
        this.data = data;
    }

    public void handle(INetHandler listener) {
        EnumPacketDirection direction = listener instanceof INetHandlerPlayServer
                ? EnumPacketDirection.SERVERBOUND
                : EnumPacketDirection.CLIENTBOUND;
        Packet<?> packet = decode(direction);
        if (packet != null) {
            ((Packet) packet).processPacket(listener);
        }
    }

    public Packet<?> decode(EnumPacketDirection direction) {
        Packet<?> packet = createVanillaPacket(direction);
        if (packet != null) {
            return packet;
        }
        return createCustomPayloadPacket(direction);
    }

    private Packet<?> createVanillaPacket(EnumPacketDirection direction) {
        if (vanillaPacketId < 0) {
            return null;
        }
        try {
            Packet<?> packet = EnumConnectionState.PLAY.getPacket(direction, vanillaPacketId);
            if (packet == null) {
                return null;
            }
            packet.readPacketData(new PacketBuffer(data.copy()));
            return packet;
        } catch (Exception e) {
            return null;
        }
    }

    private Packet<?> createCustomPayloadPacket(EnumPacketDirection direction) {
        if (type == null) {
            return null;
        }
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
        payload.writeBytes(data.copy());
        if (direction == EnumPacketDirection.CLIENTBOUND) {
            return LegacyCustomPayloadAccessor.createSPacket(type.toString(), payload);
        }
        return LegacyCustomPayloadAccessor.createCPacket(type.toString(), payload);
    }

    public ByteBuf getData() {
        return data;
    }
}
