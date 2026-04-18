package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.indextype.CustomPacketPrefixHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author USS_Shenzhou
 * Forge 1.20.1 对齐移植前项目：仅索引 custom payload 的类型头，不改写 payload body。
 */
@Mixin({ClientboundCustomPayloadPacket.class, ServerboundCustomPayloadPacket.class})
public class CustomPacketPayloadMixin {

    @Redirect(
            method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeResourceLocation(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/network/FriendlyByteBuf;"
            )
    )
    private FriendlyByteBuf neb$indexedHeaderEncode(FriendlyByteBuf buf, ResourceLocation type) {
        if (type == null || NotEnoughBandwidthLegacyConfig.skipType(type.toString())) {
            return buf.writeResourceLocation(type);
        }
        CustomPacketPrefixHelper.get()
                .index(type)
                .save(buf);
        return buf;
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;readResourceLocation()Lnet/minecraft/resources/ResourceLocation;"
            )
    )
    private ResourceLocation neb$indexedHeaderDecode(FriendlyByteBuf buf) {
        try {
            FriendlyByteBuf tryRead = new FriendlyByteBuf(buf.retainedDuplicate());
            try {
                ResourceLocation tryType = tryRead.readResourceLocation();
                if (tryType != null && NotEnoughBandwidthLegacyConfig.skipType(tryType.toString())) {
                    return buf.readResourceLocation();
                }
            } finally {
                tryRead.release();
            }
        } catch (Exception ignored) {
        }
        return CustomPacketPrefixHelper.getType(buf);
    }
}
