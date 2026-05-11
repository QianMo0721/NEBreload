package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings("DataFlowIssue")
public class AggregatedEncodePacket {
    @Nullable
    private final Packet<?> packet;
    @Nullable
    private final ResourceLocation type;
    private final int vanillaPacketId;
    @Nullable
    private final NebPayload payload;
    @Nullable
    private final FriendlyByteBuf customPayloadData;

    public AggregatedEncodePacket(Packet<?> packet, @Nullable ResourceLocation type, PacketFlow flow) {
        NebPayload decodedPayload = decodeRegisteredPayload(packet, type);
        if (decodedPayload != null) {
            this.packet = null;
            this.type = type;
            this.vanillaPacketId = -1;
            this.payload = decodedPayload;
            this.customPayloadData = null;
        } else if (packet instanceof ClientboundCustomPayloadPacket || packet instanceof ServerboundCustomPayloadPacket) {
            this.packet = null;
            this.type = type;
            this.vanillaPacketId = -1;
            this.payload = null;
            this.customPayloadData = captureCustomPayloadData(packet);
        } else {
            this.packet = packet;
            this.type = type;
            this.vanillaPacketId = ConnectionProtocol.PLAY.getPacketId(flow, packet);
            this.payload = null;
            this.customPayloadData = null;
        }
    }

    @Nullable
    private NebPayload decodeRegisteredPayload(Packet<?> packet, @Nullable ResourceLocation type) {
        if (type == null || !PayloadRegistry.contains(type)) {
            return null;
        }
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            FriendlyByteBuf payloadBuf = clientbound.getInternalData();
            try {
                return PayloadRegistry.decode(type, payloadBuf);
            } finally {
                if (payloadBuf.refCnt() > 0) {
                    payloadBuf.release();
                }
            }
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            FriendlyByteBuf payloadBuf = new FriendlyByteBuf(serverbound.getData().retainedDuplicate());
            try {
                return PayloadRegistry.decode(type, payloadBuf);
            } finally {
                if (payloadBuf.refCnt() > 0) {
                    payloadBuf.release();
                }
            }
        }
        return null;
    }

    @Nullable
    private FriendlyByteBuf captureCustomPayloadData(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            FriendlyByteBuf payload = clientbound.getInternalData();
            if (payload == null || payload.refCnt() <= 0) {
                return null;
            }
            FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.buffer(payload.readableBytes()));
            copy.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
            payload.release();
            return copy;
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            FriendlyByteBuf payload = serverbound.getData();
            if (payload == null || payload.refCnt() <= 0) {
                return null;
            }
            FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.buffer(payload.readableBytes()));
            copy.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
            return copy;
        }
        return null;
    }

    @Nullable
    public ResourceLocation getType() {
        return type;
    }

    /**
     * Encode only the payload body for game custom-payload packets, because the
     * aggregated container already carries the channel identifier. Vanilla
     * packets write their original PLAY body directly and are identified by the
     * separate vanilla packet id in the aggregated entry header.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void encode(ByteBuf buf) {
        try {
            if (payload != null) {
                PayloadRegistry.encode(new FriendlyByteBuf(buf), payload);
                return;
            }
            if (customPayloadData != null) {
                FriendlyByteBuf payloadBuf = new FriendlyByteBuf(customPayloadData.duplicate());
                try {
                    writePayloadBytes(buf, payloadBuf);
                } finally {
                    payloadBuf.readerIndex(0);
                }
                return;
            }
            if (packet == null) {
                return;
            }
            var friendly = new FriendlyByteBuf(buf);
            ((Packet) packet).write(friendly);
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Skipped: Failed to encode packet " + type, e);
        }
    }

    @Nullable
    public Packet<?> getPacket() {
        return packet;
    }

    public boolean isVanillaPacket() {
        return packet != null;
    }

    public int getVanillaPacketId() {
        return vanillaPacketId;
    }

    public int getEncodedSizeEstimate() {
        return getEncodedSizeEstimate(null);
    }

    public int getEncodedSizeEstimate(@Nullable Connection connection) {
        int dataSize = getPayloadBodySizeEstimate();
        if (dataSize <= 0) {
            return dataSize;
        }
        int headerSize = 1 + FriendlyByteBuf.getVarIntSize(dataSize);
        if (isVanillaPacket()) {
            return headerSize + FriendlyByteBuf.getVarIntSize(vanillaPacketId) + dataSize;
        }
        if (type == null) {
            return dataSize;
        }
        return headerSize + getTypePrefixSize(connection, type) + dataSize;
    }

    private int getPayloadBodySizeEstimate() {
        if (customPayloadData != null) {
            return customPayloadData.readableBytes();
        }
        FriendlyByteBuf sizeProbe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            if (payload != null) {
                PayloadRegistry.encode(sizeProbe, payload);
                return sizeProbe.readableBytes();
            }
            if (packet != null) {
                packet.write(sizeProbe);
                return sizeProbe.readableBytes();
            }
            return 0;
        } finally {
            sizeProbe.release();
        }
    }

    private static int getTypePrefixSize(@Nullable Connection connection, ResourceLocation type) {
        FriendlyByteBuf sizeProbe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // 优先按连接级索引估算，避免全局表未就绪时退化为 RL 前缀高估
            if (connection != null && NamespaceIndexManager.ready(connection) && NamespaceIndexManager.contains(connection, type)) {
                var idx = NamespaceIndexManager.getCheckedIndex(connection, type);
                sizeProbe.writeVarInt(idx.getA());
                sizeProbe.writeVarInt(idx.getB());
                return sizeProbe.readableBytes();
            }
            if (NamespaceIndexManager.ready() && NamespaceIndexManager.contains(type)) {
                var idx = NamespaceIndexManager.getCheckedIndex(type);
                if (idx != null) {
                    sizeProbe.writeVarInt(idx.getA());
                    sizeProbe.writeVarInt(idx.getB());
                    return sizeProbe.readableBytes();
                }
            }
            sizeProbe.writeByte(0);
            sizeProbe.writeResourceLocation(type);
            return sizeProbe.readableBytes();
        } finally {
            sizeProbe.release();
        }
    }

    private void writePayloadBytes(ByteBuf out, FriendlyByteBuf payloadBuf) {
        out.writeBytes(payloadBuf, payloadBuf.readerIndex(), payloadBuf.readableBytes());
    }

    public void sendPassthrough(Connection connection, PacketFlow flow) {
        try {
            if (packet != null) {
                connection.send(packet);
                return;
            }
            if (payload != null) {
                PayloadRegistry.send(connection, flow, payload);
                return;
            }
            if (customPayloadData != null && type != null) {
                FriendlyByteBuf packetBuf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packetBuf.writeResourceLocation(type);
                    packetBuf.writeBytes(customPayloadData, customPayloadData.readerIndex(), customPayloadData.readableBytes());
                    if (flow == PacketFlow.CLIENTBOUND) {
                        connection.send(new ClientboundCustomPayloadPacket(packetBuf));
                    } else {
                        connection.send(new ServerboundCustomPayloadPacket(packetBuf));
                    }
                } finally {
                    if (packetBuf.refCnt() > 0) {
                        packetBuf.release();
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Skipped: Failed to passthrough packet " + type, e);
        }
    }

    public void release() {
        if (customPayloadData != null && customPayloadData.refCnt() > 0) {
            customPayloadData.release();
        }
    }
}
