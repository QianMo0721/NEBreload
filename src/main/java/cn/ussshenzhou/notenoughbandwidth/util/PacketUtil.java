package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class PacketUtil {
    private PacketUtil() {
    }

    public static String getTrueType(Packet<?> packet) {
        return PacketAggregationPacket.resolvePacketType(packet);
    }

    public static Object getTruePacket(Packet<?> packet) {
        if (PacketAggregationPacket.isTransport(packet)) {
            PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
            if (payload != null) {
                return payload;
            }
        }
        return packet;
    }

    public static boolean isCustomPayload(Packet<?> packet) {
        return packet instanceof SPacketCustomPayload || packet instanceof CPacketCustomPayload;
    }
}
