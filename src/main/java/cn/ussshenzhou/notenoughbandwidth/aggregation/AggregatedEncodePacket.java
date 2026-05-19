package cn.ussshenzhou.notenoughbandwidth.aggregation;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import java.io.IOException;

public class AggregatedEncodePacket {
    private final Packet<?> packet;
    private final String type;
    private final PacketBuffer payloadData;
    private final boolean customPayload;
    private final int vanillaPacketId;

    public AggregatedEncodePacket(Packet<?> packet, String type, EnumPacketDirection direction) {
        this.packet = packet;
        this.type = type;
        this.customPayload = packet instanceof SPacketCustomPayload || packet instanceof CPacketCustomPayload;
        this.payloadData = captureCustomPayloadData(packet);
        this.vanillaPacketId = resolveVanillaPacketId(packet, direction);
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public String getType() {
        return type;
    }

    public boolean isCustomPayload() {
        return customPayload;
    }

    public boolean isVanillaPacket() {
        return !customPayload && vanillaPacketId >= 0;
    }

    public int getVanillaPacketId() {
        return vanillaPacketId;
    }

    public void encode(ByteBuf target) throws IOException {
        PacketBuffer output = new PacketBuffer(target);
        if (payloadData != null) {
            output.writeBytes(payloadData, payloadData.readerIndex(), payloadData.readableBytes());
            return;
        }
        packet.writePacketData(output);
    }

    public int getEncodedSizeEstimate() {
        int bodySize = getPayloadBodySizeEstimate();
        int headerSize = 1 + PacketBuffer.getVarIntSize(bodySize);
        if (isVanillaPacket()) {
            return headerSize + PacketBuffer.getVarIntSize(vanillaPacketId) + bodySize;
        }
        int typeSize = PacketBuffer.getVarIntSize(type.length()) + type.length() * 4;
        return headerSize + typeSize + bodySize;
    }

    private int getPayloadBodySizeEstimate() {
        if (payloadData != null) {
            return payloadData.readableBytes();
        }
        PacketBuffer probe = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(probe);
            return probe.readableBytes();
        } catch (IOException e) {
            throw new RuntimeException("[NEB] Failed to estimate packet size", e);
        } finally {
            probe.release();
        }
    }

    public void sendPassthrough(NetworkManager connection) {
        connection.sendPacket(packet);
    }

    public void release() {
        if (payloadData != null && payloadData.refCnt() > 0) {
            payloadData.release();
        }
    }

    private static PacketBuffer captureCustomPayloadData(Packet<?> packet) {
        if (packet instanceof SPacketCustomPayload) {
            PacketBuffer data = ((SPacketCustomPayload) packet).getBufferData();
            if (data == null) {
                return null;
            }
            PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
            copy.writeBytes(data, data.readerIndex(), data.readableBytes());
            return copy;
        }
        if (packet instanceof CPacketCustomPayload) {
            PacketBuffer data = ((CPacketCustomPayload) packet).getBufferData();
            if (data == null) {
                return null;
            }
            PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
            copy.writeBytes(data, data.readerIndex(), data.readableBytes());
            return copy;
        }
        return null;
    }

    private static int resolveVanillaPacketId(Packet<?> packet, EnumPacketDirection direction) {
        if (packet instanceof SPacketCustomPayload || packet instanceof CPacketCustomPayload) {
            return -1;
        }
        try {
            EnumConnectionState play = EnumConnectionState.PLAY;
            Integer packetId = play.getPacketId(direction, packet);
            return packetId == null ? -1 : packetId.intValue();
        } catch (Exception ignored) {
            return -1;
        }
    }
}
