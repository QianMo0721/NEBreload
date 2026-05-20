package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleAggregatedCustomPayload(SPacketCustomPayload packet, CallbackInfo ci) {
        NetHandlerPlayClient listener = (NetHandlerPlayClient) (Object) this;
        NetworkManager connection = listener.getNetworkManager();
        if (connection == null) {
            return;
        }
        String channel = PacketAggregationPacket.getChannelName(packet);
        if (channel == null || !PayloadRegistry.contains(channel)) {
            return;
        }
        PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
        if (payload == null) {
            return;
        }
        boolean handled = false;
        try {
            handled = PayloadRegistry.decodeAndHandle(
                    channel,
                    payload,
                    PayloadContext.of(connection, listener, true)
            );
        } finally {
            payload.release();
        }
        if (handled) {
            ci.cancel();
        }
    }
}
