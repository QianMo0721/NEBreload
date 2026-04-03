package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.DecoderHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DecoderHandler.class)
public class PacketDecoderMixin {
    @Inject(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/jfr/FlightProfiler;onPacketReceived(IILjava/net/SocketAddress;I)V", shift = At.Shift.BEFORE))
    private void neblRecordIn(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        int size = input.readableBytes();
        if (out.isEmpty()) {
            return;
        }
        Object decoded = out.get(out.size() - 1);
        if (!(decoded instanceof Packet<?> packet)) {
            return;
        }
        SimpleStatManager.inBaked(size);
        if (PacketUtil.getTruePacket(packet) instanceof PacketAggregationPacket aggregationPacket) {
            aggregationPacket.setBakedSize(size);
        } else {
            SimpleStatManager.inRaw(size);
        }
    }
}
