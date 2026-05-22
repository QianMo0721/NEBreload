package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NebPayload;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NettyPacketEncoder;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("rawtypes")
@Mixin(NettyPacketEncoder.class)
public class PacketEncoderMixin {

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At("TAIL"))
    private void nebRecordOutboundTraffic(ChannelHandlerContext ctx, Packet packet, ByteBuf output, CallbackInfo ci) {
        int bakedSize = output.readableBytes();
        SimpleStatManager.outBaked(bakedSize);
        int rawSize;
        Object truePacket = PacketUtil.getTruePacket(packet);
        if (truePacket instanceof PacketBuffer) {
            rawSize = 0;
        } else if (truePacket instanceof NebPayload) {
            rawSize = 0;
        } else if (PacketAggregationPacket.isTransport(packet)) {
            rawSize = 0;
        } else {
            rawSize = EncodedTrafficStatHelper.estimateRawPacketSize(packet, output);
        }
        SimpleStatManager.outRaw(rawSize);
    }
}
