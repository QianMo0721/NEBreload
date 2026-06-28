
package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 */
@Mixin(Varint21LengthFieldPrepender.class)
public class Varint21LengthFieldPrependerMixin {

    @ModifyConstant(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", constant = @Constant(intValue = 3), require = 0)
    private int neblAllowBiggerPacket(int constant) {
        return 4;
    }

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/VarInt;getByteSize(I)I", shift = At.Shift.AFTER))
    private void neblCheckPacketSize(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, CallbackInfo ci, @Local(ordinal = 0) int bodyLength) {
        int maxSize = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
        if (bodyLength > maxSize) {
            throw new EncoderException("NEBL: Packet too large: size " + bodyLength + " is over " + maxSize);
        }
    }
}