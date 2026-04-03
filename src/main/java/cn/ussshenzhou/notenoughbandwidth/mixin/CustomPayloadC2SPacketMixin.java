package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CustomPayloadC2SPacket.class)
public abstract class CustomPayloadC2SPacketMixin {
    @Shadow
    public abstract Identifier getChannel();

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;writeIdentifier(Lnet/minecraft/util/Identifier;)Lnet/minecraft/network/PacketByteBuf;"))
    private PacketByteBuf nebl$encodeIndexedHeader(PacketByteBuf buf, Identifier id) {
        Identifier type = getChannel();
        if (NotEnoughBandwidthConfig.skipType(type.toString())) {
            return buf.writeIdentifier(type);
        }
        CustomPacketPrefixHelper.get().index(type).save(buf);
        return buf;
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;readIdentifier()Lnet/minecraft/util/Identifier;"))
    private Identifier nebl$decodeIndexedHeader(PacketByteBuf buf) {
        buf.markReaderIndex();
        try {
            Identifier vanillaType = buf.readIdentifier();
            if (NotEnoughBandwidthConfig.skipType(vanillaType.toString())) {
                return vanillaType;
            }
        } catch (Exception ignored) {
        }
        buf.resetReaderIndex();
        return CustomPacketPrefixHelper.getType(buf);
    }
}
