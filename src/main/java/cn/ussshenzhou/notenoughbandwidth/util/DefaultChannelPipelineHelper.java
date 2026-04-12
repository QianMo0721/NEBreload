package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class DefaultChannelPipelineHelper {
    private DefaultChannelPipelineHelper() {
    }

    public static Packet<?> toVanillaAggregatedPacket(NetworkManager connection, PacketAggregationPacket packet) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        packet.encode(buffer);
        if (getOutboundDirection(connection) == EnumPacketDirection.CLIENTBOUND) {
            return LegacyCustomPayloadAccessor.createSPacket(PacketAggregationPacket.TYPE.toString(), buffer);
        }
        return LegacyCustomPayloadAccessor.createCPacket(PacketAggregationPacket.TYPE.toString(), buffer);
    }

    private static EnumPacketDirection getOutboundDirection(NetworkManager connection) {
        return connection.getDirection() == EnumPacketDirection.CLIENTBOUND
                ? EnumPacketDirection.SERVERBOUND
                : EnumPacketDirection.CLIENTBOUND;
    }
}
