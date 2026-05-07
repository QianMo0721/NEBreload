package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 该类已废弃
 */
@Mixin({ClientboundCustomPayloadPacket.class, ServerboundCustomPayloadPacket.class})
public class CustomPacketPayloadMixin {
}
