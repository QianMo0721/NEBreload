package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NettyPacketDecoder;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = NettyPacketDecoder.class)
public class PacketDecoderMixin {
    @Inject(method = "decode", at = @At("TAIL"))
    private void nebRecordIn(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (out.isEmpty()) {
            return;
        }
        Object last = out.get(out.size() - 1);
        if (!(last instanceof Packet<?>)) {
            return;
        }
        Packet<?> packet = (Packet<?>) last;
        int bakedSize = input.readerIndex();
        int rawSize = EncodedTrafficStatHelper.estimateRawPacketSize(packet, input);
        SimpleStatManager.inBaked(bakedSize);
        SimpleStatManager.inRaw(rawSize);
    }
}
