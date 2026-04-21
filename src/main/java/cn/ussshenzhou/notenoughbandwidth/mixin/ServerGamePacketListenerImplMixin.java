package cn.ussshenzhou.notenoughbandwidth.mixin;

import com.mojang.logging.LogUtils;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @Unique
    private Connection nebConnection() {
        try {
            Field field;
            try {
                field = ServerGamePacketListenerImpl.class.getDeclaredField("connection");
            } catch (NoSuchFieldException ignored) {
                field = ServerGamePacketListenerImpl.class.getDeclaredField("f_9742_");
            }
            field.setAccessible(true);
            return (Connection) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ServerGamePacketListenerImpl connection field", e);
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleAggregatedCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.getIdentifier() != null && PayloadRegistry.contains(packet.getIdentifier())) {
            FriendlyByteBuf payload = new FriendlyByteBuf(packet.getData().retainedDuplicate());
            boolean handled = false;
            try {
                handled = PayloadRegistry.decodeAndHandle(
                        packet.getIdentifier(),
                        payload,
                        PayloadContext.of(nebConnection(), (PacketListener) (Object) this, PacketFlow.SERVERBOUND)
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
        if (packet.getIdentifier() != null
                && NotEnoughBandwidthLegacyConfig.shouldKeepCustomPayloadOnNetworkThread(packet.getIdentifier().toString())) {
            if (NetworkHooks.onCustomPayload(packet, nebConnection())) {
                LOGGER.debug("[NEB] Dispatched server custom payload through Forge path: {}", packet.getIdentifier());
                ci.cancel();
            }
        }
    }
}
