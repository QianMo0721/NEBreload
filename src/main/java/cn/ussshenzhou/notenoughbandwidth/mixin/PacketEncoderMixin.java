package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
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
    private void nebRecordOut(ChannelHandlerContext ctx, Packet packet, ByteBuf output, CallbackInfo ci) {
        int size = output.readableBytes();
        SimpleStatManager.outBaked(size);
        Object truePacket = PacketUtil.getTruePacket(packet);
        if (truePacket instanceof PacketAggregationPacket aggregationPacket) {
            SimpleStatManager.outRaw(size - aggregationPacket.getBakedSize());
        } else {
            SimpleStatManager.outRaw(size);
        }
    }
}
