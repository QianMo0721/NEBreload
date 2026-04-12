package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.channel.Channel;
import io.netty.channel.local.LocalAddress;
import io.netty.util.Attribute;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Mixin(value = NetworkManager.class)
public abstract class ConnectionMixin {
    @Unique
    private static final Set<String> NEB_ALWAYS_BYPASS_TYPES = new HashSet<String>(Arrays.asList(
            "minecraft:add_entity",
            "minecraft:spawn_object",
            "minecraft:spawn_mob",
            "minecraft:spawn_player",
            "minecraft:spawn_global_entity",
            "minecraft:spawn_painting",
            "minecraft:spawn_experience_orb",
            "minecraft:move_entity_pos",
            "minecraft:move_entity_pos_rot",
            "minecraft:move_entity_rot",
            "minecraft:entity_rel_move",
            "minecraft:entity_look_move",
            "minecraft:entity_look",
            "minecraft:teleport_entity",
            "minecraft:entity_teleport",
            "minecraft:set_entity_motion",
            "minecraft:entity_velocity",
            "minecraft:remove_entities",
            "minecraft:destroy_entities",
            "minecraft:block_update",
            "minecraft:block_change"
    ));

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void nebAggregateOrCompress(Packet<?> packet, CallbackInfo ci) {
        if (nebHandleOutboundPacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void nebAggregateOrCompressWithListeners(Packet<?> packet,
                                                     GenericFutureListener<? extends Future<? super Void>> listener,
                                                     GenericFutureListener<? extends Future<? super Void>>[] listeners,
                                                     CallbackInfo ci) {
        if (listener != null || (listeners != null && listeners.length > 0)) {
            return;
        }
        if (nebHandleOutboundPacket(packet)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean nebHandleOutboundPacket(Packet<?> packet) {
        NetworkManager manager = (NetworkManager) (Object) this;
        if (manager == null || packet == null) {
            return false;
        }
        SocketAddress remote = manager.getRemoteAddress();
        if (remote instanceof LocalAddress) {
            return false;
        }
        Channel channel = manager.channel();
        if (channel == null) {
            return false;
        }
        if (manager.getNetHandler() == null) {
            return false;
        }
        Attribute<EnumConnectionState> protocolAttr = channel.attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY);
        EnumConnectionState protocol = protocolAttr == null ? null : protocolAttr.get();
        if (protocol != EnumConnectionState.PLAY) {
            return false;
        }
        if (EnumConnectionState.getFromPacket(packet) != EnumConnectionState.PLAY) {
            return false;
        }

        if (!NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.initFromRegisteredChannels();
        }

        Packet<?> packetToSend = packet;
        if (packet instanceof CPacketCustomPayload || packet instanceof SPacketCustomPayload) {
            Packet<?> compressed = nebTryCompressCustomPayload(packet);
            if (compressed != null) {
                packetToSend = compressed;
            }
        }

        ResourceLocation type = PacketUtil.getTrueType(packetToSend);
        if (nebShouldAlwaysBypassAggregation(type)) {
            AggregationManager.flushConnection(manager);
            return false;
        }
        if (NotEnoughBandwidthLegacyConfig.skipType(type.toString())) {
            AggregationManager.flushConnection(manager);
            return false;
        }

        AggregationManager.takeOver(packetToSend, manager);
        return true;
    }

    @Unique
    private static Packet<?> nebTryCompressCustomPayload(Packet<?> packet) {
        if (packet instanceof CPacketCustomPayload) {
            return CustomPayloadCodecHelper.tryCompress((CPacketCustomPayload) packet);
        }
        if (packet instanceof SPacketCustomPayload) {
            return CustomPayloadCodecHelper.tryCompress((SPacketCustomPayload) packet);
        }
        return null;
    }

    @Unique
    private static boolean nebShouldAlwaysBypassAggregation(ResourceLocation type) {
        return type != null && NEB_ALWAYS_BYPASS_TYPES.contains(type.toString());
    }
}
