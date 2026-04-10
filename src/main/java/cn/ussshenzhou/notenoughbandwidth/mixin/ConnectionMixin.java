package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    @Unique
    private static final ThreadLocal<Integer> NEB_CUSTOM_PAYLOAD_RESEND_DEPTH = ThreadLocal.withInitial(() -> 0);

    @Shadow
    @Nullable
    private volatile PacketListener packetListener;

    @Shadow
    public abstract void send(Packet<?> packet, @Nullable PacketSendListener listener);

    @Shadow
    public abstract SocketAddress getRemoteAddress();

    @Shadow
    private ConnectionProtocol getCurrentProtocol() {
        throw new AssertionError();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void nebwPacketAggregate(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        // only work on established PLAY connections, otherwise packets like
        // ClientboundLoginPacket may be wrongly aggregated before the protocol
        // actually switches from LOGIN to PLAY and the remote side is ready to
        // decode NEB payloads.
        if (this.getRemoteAddress() instanceof LocalAddress) {
            return;
        }
        if (this.packetListener == null) {
            return;
        }
        if (connection.channel() == null) {
            return;
        }
        ConnectionProtocol currentProtocol;
        try {
            currentProtocol = this.getCurrentProtocol();
        } catch (Exception ignored) {
            return;
        }
        if (currentProtocol != ConnectionProtocol.PLAY) {
            return;
        }
        // Mod custom channels such as Architectury / FTB Quests are extremely
        // sensitive to payload byte layout and ordering. They must stay on the
        // original channel path, but we may still transparently zstd-compress
        // the payload body itself.
        if (isCustomPayloadPacket(packet)) {
            if (NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.get() > 0) {
                return;
            }
            AggregationManager.flushConnection(connection);
            Packet<?> compressed = tryCompressCustomPayload(packet);
            if (compressed != null) {
                pushCustomPayloadResendDepth();
                try {
                    this.send(compressed, listener);
                } finally {
                    popCustomPayloadResendDepth();
                }
                ci.cancel();
            }
            return;
        }
        if (shouldBypassBundlePacket(packet)) {
            AggregationManager.flushConnection(connection);
            return;
        }
        // de-bundle: in Forge 1.20.1 BundlePacket exists as a vanilla concept
        // Attempt to detect bundle packets via class name (may not exist on all Forge 1.20.1 builds)
        if (isBundlePacket(packet)) {
            deBundlePacket(packet, listener);
            ci.cancel();
            return;
        }
        if (!isPlayPacket(packet)) {
            return;
        }
        if (shouldAlwaysBypassAggregation(packet)) {
            AggregationManager.flushConnection(connection);
            return;
        }
        var packetType = PacketUtil.getTrueType(packet);
        // compatibility and avoid infinite loop
        if (NotEnoughBandwidthLegacyConfig.skipType(packetType.toString())) {
            // flush to ensure packet order
            AggregationManager.flushConnection(connection);
            return;
        }
        AggregationManager.takeOver(packet, connection);
        ci.cancel();
    }

    /**
     * Check if this is a BundlePacket (vanilla 1.19.4+ bundle).
     * We check by class name to avoid compile-time dependency issues.
     */
    @Unique
    private static boolean isBundlePacket(Packet<?> packet) {
        return packet.getClass().getSimpleName().contains("Bundle");
    }

    @Unique
    private static boolean isPlayPacket(Packet<?> packet) {
        return ConnectionProtocol.getProtocolForPacket(packet) == ConnectionProtocol.PLAY;
    }

    @Unique
    private static boolean isCustomPayloadPacket(Packet<?> packet) {
        return packet instanceof ClientboundCustomPayloadPacket
                || packet instanceof ServerboundCustomPayloadPacket;
    }

    @Unique
    private static Packet<?> tryCompressCustomPayload(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            return CustomPayloadCodecHelper.tryCompress(clientbound);
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            return CustomPayloadCodecHelper.tryCompress(serverbound);
        }
        return null;
    }

    @Unique
    private static void pushCustomPayloadResendDepth() {
        NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.set(NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.get() + 1);
    }

    @Unique
    private static void popCustomPayloadResendDepth() {
        int depth = NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.get() - 1;
        if (depth <= 0) {
            NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.remove();
        } else {
            NEB_CUSTOM_PAYLOAD_RESEND_DEPTH.set(depth);
        }
    }

    @Unique
    private static boolean shouldAlwaysBypassAggregation(Packet<?> packet) {
        return isAlwaysBypassPacketType(packet);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static boolean shouldBypassBundlePacket(Packet<?> packet) {
        if (!(packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundlePacket)) {
            return false;
        }
        for (Packet<?> subPacket : (Iterable<Packet<?>>) bundlePacket.subPackets()) {
            if (isAlwaysBypassPacketType(subPacket)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean isAlwaysBypassPacketType(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
                || packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
    }

    /**
     * Expand a BundlePacket into individual sub-packets and re-send each.
     */
    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void deBundlePacket(Packet<?> bundle, @Nullable PacketSendListener listener) {
        try {
            // BundlePacket has subPackets() method returning Iterable<Packet<?>>
            var method = bundle.getClass().getMethod("subPackets");
            Iterable<Packet<?>> subPackets = (Iterable<Packet<?>>) method.invoke(bundle);
            for (Packet<?> p : subPackets) {
                this.send(p, listener);
            }
        } catch (Exception e) {
            // If we can't de-bundle, just skip aggregation and send normally
            AggregationManager.flushConnection((Connection) (Object) this);
        }
    }

}
