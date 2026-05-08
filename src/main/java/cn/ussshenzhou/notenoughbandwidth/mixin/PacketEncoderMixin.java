package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings("rawtypes")
@Mixin(PacketEncoder.class)
public class PacketEncoderMixin {


    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At(value = "TAIL"))
    private void nebRecordOutboundTraffic(ChannelHandlerContext ctx, Packet packet, ByteBuf output, CallbackInfo ci) {
        int bakedSize = output.readableBytes();
        SimpleStatManager.outBaked(bakedSize);
        int rawSize;
        if (PacketUtil.getTruePacket(packet) instanceof PacketAggregationPacket) {
            // 聚合容器内部各子包的 raw 已在
            // PacketAggregationPacket.encode() 中统计过；
            // 这里若再补 baked-payload 差值，会把外层 wrapper 开销误记进 raw，
            // 导致双端上下行 raw 偏大。
            rawSize = 0;
        } else {
            rawSize = EncodedTrafficStatHelper.estimateRawPacketSize(packet, output);
        }
        SimpleStatManager.outRaw(rawSize);
    }
}
