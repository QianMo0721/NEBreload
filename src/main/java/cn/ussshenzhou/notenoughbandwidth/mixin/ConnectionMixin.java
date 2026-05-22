package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkManager.class, priority = 1)
public abstract class ConnectionMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void nebAggregatePacket(Packet<?> packet, CallbackInfo ci) {
        NetworkManager connection = (NetworkManager) (Object) this;
        if (AggregationManager.isInternalSend()) {
            return;
        }
        if (connection.isLocalChannel() || connection.channel() == null || !connection.isChannelOpen()) {
            return;
        }
        EnumConnectionState currentProtocol = connection.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get();
        if (currentProtocol != EnumConnectionState.PLAY) {
            return;
        }
        if (!hasNegotiatedNebTransport(connection)) {
            return;
        }
        if (shouldSkipAggregation(packet)) {
            AggregationManager.flushConnection(connection);
            return;
        }
        if (AggregationManager.takeOver(packet, connection)) {
            ci.cancel();
        }
    }

    @Inject(method = "closeChannel", at = @At("HEAD"))
    private void nebClearConnectionScopedStateBeforeClose(ITextComponent message, CallbackInfo ci) {
        NetworkManager connection = (NetworkManager) (Object) this;
        AggregationManager.clearConnection(connection);
        ChannelAttributes.clearTransportSetupRequested(connection);
        ChannelAttributes.setPayloadSetup(connection, null);
    }

    private static boolean hasNegotiatedNebTransport(NetworkManager connection) {
        return ChannelAttributes.hasPayload(connection, PacketAggregationPacket.CHANNEL_NAME);
    }

    private static boolean shouldSkipAggregation(Packet<?> packet) {
        String type = PacketUtil.getTrueType(packet);
        return type == null || NotEnoughBandwidthLegacyConfig.skipType(type);
    }

    private static boolean isFrameworkPayload(Packet<?> packet) {
        String type = PacketUtil.getTrueType(packet);
        return ModConstants.PAYLOAD_CHANNEL.equals(type);
    }
}
