package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
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
    @Nullable
    private final NebPayload payload;
    @Nullable
    private final FriendlyByteBuf customPayloadData;

    public AggregatedEncodePacket(Packet<?> packet, @Nullable ResourceLocation type) {
        this.type = type;
        this.payload = decodeRegisteredPayload(packet, type);
        if (this.payload != null) {
            this.packet = null;
            this.customPayloadData = null;
        } else if (packet instanceof ClientboundCustomPayloadPacket || packet instanceof ServerboundCustomPayloadPacket) {
            this.packet = null;
            this.customPayloadData = captureCustomPayloadData(packet);
        } else {
            this.packet = packet;
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
            FriendlyByteBuf payloadBuf = serverbound.getData();
            try {
                return PayloadRegistry.decode(type, payloadBuf);
            } finally {
                // serverbound 原始 payload 最终仍会由 vanilla handle 路径回收，
                // 这里 decode 使用的是 getData() 返回值，若该返回值是独立副本则释放，
                // 若不是独立副本则 refCnt 保护可避免额外崩溃。
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
            payload.release();
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
     * aggregated prefix already carries the channel identifier. For normal
     * vanilla packets, mirror PacketEncoder by delegating to Packet#write.
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

    private void writePayloadBytes(ByteBuf out, FriendlyByteBuf payloadBuf) {
        out.writeBytes(payloadBuf, payloadBuf.readerIndex(), payloadBuf.readableBytes());
    }

    public void sendPassthrough(Connection connection, PacketFlow flow) {
        try {
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
                    packetBuf.release();
                }
                return;
            }
            if (packet != null) {
                connection.send(packet);
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
