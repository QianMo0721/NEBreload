package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
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
import java.net.SocketAddress;

/**
 * @author USS_Shenzhou
 */
@Mixin(value = Connection.class, priority = 1)
public abstract class ConnectionMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
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
        if (currentProtocol != ConnectionProtocol.PLAY || ConnectionProtocol.getProtocolForPacket(packet) != ConnectionProtocol.PLAY) {
            return;
        }
        if (packet instanceof BundlePacket<?> bundlePacket) {
            if (shouldBypassBundlePacket(bundlePacket)) {
                AggregationManager.flushConnection(connection);
            }
            bundlePacket.subPackets().forEach(p -> connection.send(p, listener));
            ci.cancel();
            return;
        }
        if (shouldSkipAggregation(packet)) {
            AggregationManager.flushConnection(connection);
            return;
        }
        AggregationManager.takeOver(packet, connection);
        ci.cancel();
    }

    @Unique
    private static boolean shouldBypassBundlePacket(Packet<?> packet) {
        if (!(packet instanceof BundlePacket<?> bundlePacket)) {
            return false;
        }
        for (Packet<?> subPacket : bundlePacket.subPackets()) {
            if (shouldSkipAggregation(subPacket)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean shouldSkipAggregation(Packet<?> packet) {
        var type = PacketUtil.getTrueType(packet);
        return type == null || shouldAlwaysBypassAggregation(packet) || NotEnoughBandwidthLegacyConfig.skipType(type.toString());
    }

    @Unique
    private static boolean shouldAlwaysBypassAggregation(Packet<?> packet) {
        return packet instanceof ClientboundAddEntityPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundRemoveEntitiesPacket
                || packet instanceof ClientboundBlockUpdatePacket;
    }
}
