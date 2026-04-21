package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * @author USS_Shenzhou
 */
@Mixin(PacketDecoder.class)
public class PacketDecoderMixin {

    @Inject(method = "decode",
            at = @At(value = "TAIL"))
    private void nebRecordInboundTraffic(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (!out.isEmpty()) {
            Object last = out.get(out.size() - 1);
            if (last instanceof Packet<?> packet) {
                int bakedSize = input.readerIndex();
                SimpleStatManager.inBaked(bakedSize);
                Object truePacket = PacketUtil.getTruePacket(packet);
                if (truePacket instanceof PacketAggregationPacket aggregationPacket) {
                    aggregationPacket.setBakedSize(bakedSize);
                } else {
                    int rawSize = EncodedTrafficStatHelper.estimateRawPacketSize(packet, input);
                    SimpleStatManager.inRaw(rawSize);
                }
            }
        }
    }
}
