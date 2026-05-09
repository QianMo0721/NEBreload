package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * @author USS_Shenzhou
 */
@Mixin(value = Connection.class, priority = 1)
public abstract class ConnectionMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nebAggregatePacket(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (AggregationManager.isInternalSend()) {
            return;
        }
        PacketListener packetListener = connection.getPacketListener();
        if (connection.getRemoteAddress() instanceof LocalAddress || packetListener == null || connection.channel() == null) {
            return;
        }
        ConnectionProtocol currentProtocol = connection.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
        if (currentProtocol == null) {
            return;
        }

        // Forge20.1里,PLAY期前后,Forge自身握手残留,代理/补丁链路插入包,以及Connection.send()被调用时机不干净,很容易出现判定错误,不能按原先的移植,只能这样写了
        if (currentProtocol != ConnectionProtocol.PLAY || ConnectionProtocol.getProtocolForPacket(packet) != ConnectionProtocol.PLAY) {
            return;
        }
        if (!hasNegotiatedNebTransport(connection)) {
            return;
        }
        if (shouldSkipAggregation(packet)) {
            AggregationManager.flushConnection(connection);
            return;
        }
        if (packet instanceof BundlePacket<?> bundlePacket) {
            bundlePacket.subPackets().forEach(p -> connection.send(p, listener));
            ci.cancel();
            return;
        }
        AggregationManager.takeOver(packet, connection);
        ci.cancel();
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;handleDisconnection()V"
            )
    )
    private void nebClearConnectionScopedStateBeforeHandleDisconnection(CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        AggregationManager.clearConnection(connection);
        NamespaceIndexManager.clearConnection(connection);
        ChannelAttributes.setPayloadSetup(connection, null);
        ChannelAttributes.clearTransportSetupRequested(connection);
    }

    @Unique
    private static boolean hasNegotiatedNebTransport(Connection connection) {
        var setup = ChannelAttributes.getPayloadSetup(connection);
        return setup != null && setup.hasChannel(PacketAggregationPacket.TYPE);
    }

    @Unique
    private static boolean shouldSkipAggregation(Packet<?> packet) {
        var type = PacketUtil.getTrueType(packet);
        return type == null || NotEnoughBandwidthLegacyConfig.skipType(type.toString());
    }
}
