package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.SocketAddress;

@Mixin(value = ClientConnection.class, priority = 1)
public abstract class ConnectionMixin {
    @Shadow
    private PacketListener packetListener;

    @Shadow
    public abstract SocketAddress getAddress();

    @Shadow
    public abstract NetworkSide getSide();

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void neblPacketAggregate(Packet<?> packet, CallbackInfo ci) {
        if (this.getAddress() instanceof LocalAddress || this.packetListener == null) {
            return;
        }
        if (!(this.packetListener instanceof net.minecraft.network.listener.ClientPlayPacketListener
                || this.packetListener instanceof net.minecraft.network.listener.ServerPlayPacketListener)) {
            return;
        }
        if (packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            AggregationManager.flushConnection((ClientConnection) (Object) this);
            return;
        }
        if (PacketUtil.getTrueType(packet) == null) {
            return;
        }
        if (NotEnoughBandwidthConfig.skipType(PacketUtil.getTrueType(packet).toString())) {
            AggregationManager.flushConnection((ClientConnection) (Object) this);
            return;
        }
        if (packet instanceof BundlePacket<?> bundlePacket) {
            bundlePacket.getPackets().forEach(this::send);
            ci.cancel();
            return;
        }
        AggregationManager.takeOver(packet, (ClientConnection) (Object) this);
        ci.cancel();
    }
}
