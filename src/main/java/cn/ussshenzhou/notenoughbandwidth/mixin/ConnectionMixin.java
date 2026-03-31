package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    @Nullable
    private volatile PacketListener packetListener;

    @Shadow
    public abstract void send(Packet<?> packet, @Nullable PacketSendListener listener);

    @Shadow
    public abstract SocketAddress getRemoteAddress();

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void nebwPacketAggregate(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        // only work on play
        if (this.getRemoteAddress() instanceof LocalAddress) {
            return;
        }
        if (this.packetListener == null) {
            return;
        }
        // In Forge 1.20.1, determine PLAY phase by checking listener type
        // ServerGamePacketListener = server-side play listener
        // ClientGamePacketListener = client-side play listener
        boolean isPlayPhase = (this.packetListener instanceof ServerGamePacketListener)
                || (this.packetListener instanceof ClientGamePacketListener);
        if (!isPlayPhase) {
            return;
        }
        // compatibility and avoid infinite loop
        if (NotEnoughBandwidthLegacyConfig.skipType(PacketUtil.getTrueType(packet).toString())) {
            // flush to ensure packet order
            AggregationManager.flushConnection((Connection) (Object) this);
            return;
        }
        // de-bundle: in Forge 1.20.1 BundlePacket exists as a vanilla concept
        // Attempt to detect bundle packets via class name (may not exist on all Forge 1.20.1 builds)
        if (isBundlePacket(packet)) {
            deBundlePacket(packet, listener);
            ci.cancel();
            return;
        }
        AggregationManager.takeOver(packet, (Connection) (Object) this);
        ci.cancel();
    }

    /**
     * Check if this is a BundlePacket (vanilla 1.19.4+ bundle).
     * We check by class name to avoid compile-time dependency issues.
     */
    private static boolean isBundlePacket(Packet<?> packet) {
        return packet.getClass().getSimpleName().contains("Bundle");
    }

    /**
     * Expand a BundlePacket into individual sub-packets and re-send each.
     */
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
