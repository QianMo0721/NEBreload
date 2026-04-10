package cn.ussshenzhou.notenoughbandwidth.aggregation;

import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ConnectionProtocol;
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
    private final Packet<?> packet;
    @Nullable
    private final ResourceLocation type;
    private final int vanillaPacketId;

    public AggregatedEncodePacket(Packet<?> p, @Nullable ResourceLocation type, PacketFlow flow) {
        this.packet = p;
        if (p instanceof ClientboundCustomPayloadPacket || p instanceof ServerboundCustomPayloadPacket) {
            this.type = type;
            this.vanillaPacketId = -1;
        } else {
            this.type = null;
            this.vanillaPacketId = ConnectionProtocol.PLAY.getPacketId(flow, p);
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

    /**
     * Encode only the payload body for game custom-payload packets, because the
     * aggregated prefix already carries the channel identifier. For normal
     * vanilla packets, mirror PacketEncoder by delegating to Packet#write.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void encode(ByteBuf buf) {
        try {
            if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
                FriendlyByteBuf payload = clientbound.getData();
                try {
                    writeCustomPayloadBody(buf, payload);
                } finally {
                    payload.release();
                }
                return;
            }
            if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
                writeCustomPayloadBody(buf, serverbound.getData());
                return;
            }
            var friendly = new FriendlyByteBuf(buf);
            ((Packet) packet).write(friendly);
        } catch (Exception e) {
            LogUtils.getLogger().error("[NEB] Skipped: Failed to encode packet " + type, e);
        }
    }

    public Packet<?> getPacket() {
        return packet;
    }

    private void writeCustomPayloadBody(ByteBuf out, FriendlyByteBuf payload) {
        int bodyStart = findPayloadBodyStart(payload);
        int bodyLength = payload.writerIndex() - bodyStart;
        if (bodyLength < 0) {
            bodyStart = payload.readerIndex();
            bodyLength = payload.readableBytes();
        }
        out.writeBytes(payload, bodyStart, bodyLength);
    }

    private int findPayloadBodyStart(FriendlyByteBuf payload) {
        if (type == null) {
            return payload.readerIndex();
        }
        Integer indexedHeaderEnd = tryConsumeIndexedHeader(payload);
        if (indexedHeaderEnd != null) {
            return indexedHeaderEnd;
        }
        Integer vanillaHeaderEnd = tryConsumeVanillaHeader(payload);
        if (vanillaHeaderEnd != null) {
            return vanillaHeaderEnd;
        }
        return payload.readerIndex();
    }

    private Integer tryConsumeIndexedHeader(FriendlyByteBuf payload) {
        FriendlyByteBuf probe = new FriendlyByteBuf(payload.retainedDuplicate());
        try {
            ResourceLocation decoded = CustomPacketPrefixHelper.getType(probe);
            if (type.equals(decoded)) {
                return probe.readerIndex();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            probe.release();
        }
    }

    private Integer tryConsumeVanillaHeader(FriendlyByteBuf payload) {
        FriendlyByteBuf probe = new FriendlyByteBuf(payload.retainedDuplicate());
        try {
            ResourceLocation decoded = probe.readResourceLocation();
            if (type.equals(decoded)) {
                return probe.readerIndex();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            probe.release();
        }
    }
}
