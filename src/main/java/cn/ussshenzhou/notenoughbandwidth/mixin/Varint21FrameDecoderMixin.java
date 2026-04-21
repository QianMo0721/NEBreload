package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * @author USS_Shenzhou
 */
@Mixin(Varint21FrameDecoder.class)
public class Varint21FrameDecoderMixin {

    @Inject(method = "decode", at = @At("HEAD"))
    private void nebCheckFrameLength(ChannelHandlerContext context, ByteBuf input, List<Object> out, CallbackInfo ci) {
        int readableBytes = input.readableBytes();
        if (readableBytes <= 0) {
            return;
        }

        int maxPacketSize = NotEnoughBandwidthLegacyConfig.get().getMaxPacketSize();
        int readerIndex = input.readerIndex();
        int frameLength = 0;

        for (int i = 0; i < 3; i++) {
            if (i >= readableBytes) {
                return;
            }
            int current = input.getByte(readerIndex + i);
            frameLength |= (current & 0x7F) << (i * 7);
            if ((current & 0x80) == 0) {
                if (frameLength > maxPacketSize) {
                    throw new CorruptedFrameException("NEB: Packet too large: size " + frameLength + " is over " + maxPacketSize);
                }
                return;
            }
        }
    }
}
