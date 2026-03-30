package cn.ussshenzhou.notenoughbandwidth.aggregation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
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
     * Encode the packet into {@code buf}.
     * In Forge 1.20.1, all packets implement {@code Packet#write(FriendlyByteBuf)}.
     * We simply delegate to that.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void encode(ByteBuf buf) {
        try {
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
