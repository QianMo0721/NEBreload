package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketEncoder.class)
public class PacketEncoderMixin {
    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/jfr/FlightProfiler;onPacketSent(IILjava/net/SocketAddress;I)V", shift = At.Shift.BEFORE))
    private void neblRecordOut(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf output, CallbackInfo ci) {
        int size = output.writerIndex();
        if (size <= 0) {
            return;
        }
        SimpleStatManager.outBaked(size);
        if (PacketUtil.getTruePacket(packet) instanceof PacketAggregationPacket aggregationPacket) {
            SimpleStatManager.outRaw(Math.max(0, size - aggregationPacket.getBakedSize()));
        } else {
            SimpleStatManager.outRaw(size);
        }
    }
}
