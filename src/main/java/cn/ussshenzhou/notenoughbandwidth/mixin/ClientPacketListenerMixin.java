package cn.ussshenzhou.notenoughbandwidth.mixin;

import com.mojang.logging.LogUtils;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleAggregatedCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        Connection connection = ((ClientPacketListener) (Object) this).getConnection();
        if (connection == null) {
            return;
        }
        if (packet.getIdentifier() != null && PayloadRegistry.contains(packet.getIdentifier())) {
            FriendlyByteBuf payload = packet.getInternalData();
            if (payload != null) {
                boolean handled = false;
                try {
                    handled = PayloadRegistry.decodeAndHandle(
                            packet.getIdentifier(),
                            payload,
                            PayloadContext.of(connection, (PacketListener) (Object) this, PacketFlow.CLIENTBOUND)
                    );
                    if (handled) {
                        ci.cancel();
                        return;
                    }
                } finally {
                    if (payload.refCnt() > 0) {
                        payload.release();
                    }
                }
            }
        }
        if (packet.getIdentifier() != null) {
            boolean handledByForge = NetworkHooks.onCustomPayload(packet, connection);
            if (handledByForge) {
                LOGGER.debug("[NEB] Dispatched client custom payload through Forge path: {}", packet.getIdentifier());
                ci.cancel();
            }
        }
    }
}
