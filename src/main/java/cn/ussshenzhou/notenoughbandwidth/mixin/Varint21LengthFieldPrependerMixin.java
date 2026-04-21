package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 */
@Mixin(Varint21LengthFieldPrepender.class)
public class Varint21LengthFieldPrependerMixin {

    @Inject(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
            at = @At("HEAD")
    )
    private void nebCheckPacketSize(ChannelHandlerContext context, ByteBuf input, ByteBuf output, CallbackInfo ci) {
        int packetSize = input.readableBytes();
        int maxPacketSize = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
        if (packetSize > maxPacketSize) {
            throw new IllegalArgumentException("NEB: Packet too large: size " + packetSize + " is over " + maxPacketSize);
        }
    }
}
