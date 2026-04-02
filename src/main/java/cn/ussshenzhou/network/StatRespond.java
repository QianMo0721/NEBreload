package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * @author USS_Shenzhou
 */
public class StatRespond {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_resp");

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

    @OnlyIn(Dist.CLIENT)
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            SimpleStatManager.inboundBytesBakedServer = inboundBytesBaked;
            SimpleStatManager.inboundBytesRawServer = inboundBytesRaw;
            SimpleStatManager.outboundBytesBakedServer = outboundBytesBaked;
            SimpleStatManager.outboundBytesRawServer = outboundBytesRaw;
            SimpleStatManager.inboundSpeedBakedServer = inboundSpeedBaked;
            SimpleStatManager.inboundSpeedRawServer = inboundSpeedRaw;
            SimpleStatManager.outboundSpeedBakedServer = outboundSpeedBaked;
            SimpleStatManager.outboundSpeedRawServer = outboundSpeedRaw;
        });
        ctx.setPacketHandled(true);
    }
}
