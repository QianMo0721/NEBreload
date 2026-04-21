package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadCodec;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * @author USS_Shenzhou
 */
public class StatRespond implements NebPayload {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_resp");
    public static final StatRespond SAMPLE = new StatRespond(0, 0, 0, 0, 0, 0, 0, 0);
    public static final PayloadCodec<StatRespond> CODEC = new PayloadCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, StatRespond value) {
            value.encode(buf);
        }

        @Override
        public StatRespond decode(FriendlyByteBuf buf) {
            return new StatRespond(buf);
        }
    };

    private final long inboundBytesBaked;
    private final long inboundBytesRaw;
    private final long outboundBytesBaked;
    private final long outboundBytesRaw;
    private final double inboundSpeedBaked;
    private final double inboundSpeedRaw;
    private final double outboundSpeedBaked;
    private final double outboundSpeedRaw;

    public StatRespond(long inboundBytesBaked, long inboundBytesRaw, long outboundBytesBaked, long outboundBytesRaw,
                       double inboundSpeedBaked, double inboundSpeedRaw, double outboundSpeedBaked, double outboundSpeedRaw) {
        this.inboundBytesBaked = inboundBytesBaked;
        this.inboundBytesRaw = inboundBytesRaw;
        this.outboundBytesBaked = outboundBytesBaked;
        this.outboundBytesRaw = outboundBytesRaw;
        this.inboundSpeedBaked = inboundSpeedBaked;
        this.inboundSpeedRaw = inboundSpeedRaw;
        this.outboundSpeedBaked = outboundSpeedBaked;
        this.outboundSpeedRaw = outboundSpeedRaw;
    }

    public StatRespond(FriendlyByteBuf buf) {
        this.inboundBytesBaked = buf.readVarLong();
        this.inboundBytesRaw = buf.readVarLong();
        this.outboundBytesBaked = buf.readVarLong();
        this.outboundBytesRaw = buf.readVarLong();
        this.inboundSpeedBaked = buf.readDouble();
        this.inboundSpeedRaw = buf.readDouble();
        this.outboundSpeedBaked = buf.readDouble();
        this.outboundSpeedRaw = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(inboundBytesBaked);
        buf.writeVarLong(inboundBytesRaw);
        buf.writeVarLong(outboundBytesBaked);
        buf.writeVarLong(outboundBytesRaw);
        buf.writeDouble(inboundSpeedBaked);
        buf.writeDouble(inboundSpeedRaw);
        buf.writeDouble(outboundSpeedBaked);
        buf.writeDouble(outboundSpeedRaw);
    }

    public void handle(PayloadContext context) {
        context.enqueueWork(() -> {
            SimpleStatManager.inboundBytesBakedServer = inboundBytesBaked;
            SimpleStatManager.inboundBytesRawServer = inboundBytesRaw;
            SimpleStatManager.outboundBytesBakedServer = outboundBytesBaked;
            SimpleStatManager.outboundBytesRawServer = outboundBytesRaw;
            SimpleStatManager.inboundSpeedBakedServer = inboundSpeedBaked;
            SimpleStatManager.inboundSpeedRawServer = inboundSpeedRaw;
            SimpleStatManager.outboundSpeedBakedServer = outboundSpeedBaked;
            SimpleStatManager.outboundSpeedRawServer = outboundSpeedRaw;
        });
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }
}
