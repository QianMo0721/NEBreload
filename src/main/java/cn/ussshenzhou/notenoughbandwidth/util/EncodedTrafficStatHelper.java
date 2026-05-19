package cn.ussshenzhou.notenoughbandwidth.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.Packet;

public final class EncodedTrafficStatHelper {
    private EncodedTrafficStatHelper() {
    }

    public static int estimateRawPacketSize(Packet<?> packet, ByteBuf encodedPacket) {
        return Math.max(encodedPacket.writerIndex(), encodedPacket.readerIndex() + encodedPacket.readableBytes());
    }
}
