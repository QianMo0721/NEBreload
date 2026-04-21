package cn.ussshenzhou.notenoughbandwidth.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

/**
 * Traffic statistics should compare actual transmitted bytes against the total
 * original payload bytes of the same packet, not just wrapper overhead.
 * The legacy transparent custom-payload zstd wrapper has been removed from the
 * active runtime path, so custom payloads should now use the same byte-counting
 * rule as other packets.
 */
public final class EncodedTrafficStatHelper {

    private EncodedTrafficStatHelper() {
    }

    public static int estimateRawPacketSize(Packet<?> packet, ByteBuf encodedPacket) {
        if (packet instanceof ClientboundCustomPayloadPacket || packet instanceof ServerboundCustomPayloadPacket) {
            return totalPacketBytes(encodedPacket);
        }
        return totalPacketBytes(encodedPacket);
    }

    private static int totalPacketBytes(ByteBuf encodedPacket) {
        return Math.max(encodedPacket.writerIndex(), encodedPacket.readerIndex() + encodedPacket.readableBytes());
    }
}
