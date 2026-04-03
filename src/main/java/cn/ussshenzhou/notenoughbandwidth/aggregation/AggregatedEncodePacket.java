package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

public class AggregatedEncodePacket {
    public final Identifier type;
    private final boolean isMinecraft;
    private final Packet<?> packet;
    private final PacketByteBuf payloadData;

    public AggregatedEncodePacket(Packet<?> p, Identifier type) {
        if (p instanceof CustomPayloadC2SPacket customPayloadC2SPacket) {
            this.isMinecraft = false;
            this.packet = null;
            this.payloadData = new PacketByteBuf(customPayloadC2SPacket.getData().copy());
        } else if (p instanceof CustomPayloadS2CPacket customPayloadS2CPacket) {
            this.isMinecraft = false;
            this.packet = null;
            this.payloadData = new PacketByteBuf(customPayloadS2CPacket.getData().copy());
        } else {
            this.isMinecraft = true;
            this.packet = p;
            this.payloadData = null;
        }
        this.type = type;
    }

    public void encode(ByteBuf buf) {
        if (isMinecraft) {
            PacketUtil.writeVanillaPacket(packet, buf);
            return;
        }
        if (payloadData == null) {
            throw new IllegalStateException("Missing payload data for custom packet " + type);
        }
        buf.writeBytes(payloadData.copy());
    }
}
