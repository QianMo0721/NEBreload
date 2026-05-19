package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NettyPacketDecoder;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NettyPacketDecoder.class)
public class PacketDecoderMixin {

    @Inject(method = "decode", at = @At("TAIL"))
    private void nebRecordInboundTraffic(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (!out.isEmpty()) {
            Object last = out.get(out.size() - 1);
            if (last instanceof Packet) {
                Packet<?> packet = (Packet<?>) last;
                int bakedSize = input.readerIndex();
                SimpleStatManager.inBaked(bakedSize);
                Object truePacket = PacketUtil.getTruePacket(packet);
                if (PacketAggregationPacket.isTransport(packet)) {
                    PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
                    if (payload != null) {
                        try {
                            PacketAggregationPacket.decode(null, payload);
                        } finally {
                            payload.release();
                        }
                    }
                } else {
                    int rawSize = EncodedTrafficStatHelper.estimateRawPacketSize(packet, input);
                    SimpleStatManager.inRaw(rawSize);
                }
            }
        }
    }
}
