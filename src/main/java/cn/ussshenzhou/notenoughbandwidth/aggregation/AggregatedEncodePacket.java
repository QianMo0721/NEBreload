package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings("DataFlowIssue")
public class AggregatedEncodePacket {
    public final ResourceLocation type;
    private final Packet<?> packet;

    public AggregatedEncodePacket(Packet<?> p, ResourceLocation type) {
        this.packet = p;
        this.type = type;
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
                    buf.writeBytes(payload.slice());
                } finally {
                    payload.release();
                }
                return;
            }
            if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
                buf.writeBytes(serverbound.getData().slice());
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
}
