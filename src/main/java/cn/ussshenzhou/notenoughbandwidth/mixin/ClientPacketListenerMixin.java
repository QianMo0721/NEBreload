package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.mojang.logging.LogUtils;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadContext;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
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
        ResourceLocation identifier = packet.getIdentifier();
        if (identifier != null && PayloadRegistry.contains(identifier)) {
            FriendlyByteBuf payload = packet.getInternalData();
            if (payload != null) {
                boolean handled = false;
                FriendlyByteBuf replayPayload = null;
                try {
                    PayloadContext context = PayloadContext.of(connection, (PacketListener) (Object) this, PacketFlow.CLIENTBOUND);
                    if (NotEnoughBandwidthLegacyConfig.shouldKeepCustomPayloadOnNetworkThread(identifier.toString())) {
                        handled = PayloadRegistry.decodeAndHandle(identifier, payload, context);
                    } else {
                        replayPayload = new FriendlyByteBuf(payload.retainedDuplicate());
                        FriendlyByteBuf finalReplayPayload = replayPayload;
                        context.enqueueWork(() -> {
                            try {
                                PayloadRegistry.decodeAndHandle(identifier, finalReplayPayload, context);
                            } finally {
                                if (finalReplayPayload.refCnt() > 0) {
                                    finalReplayPayload.release();
                                }
                            }
                        });
                        handled = true;
                    }
                    if (handled) {
                        ci.cancel();
                        return;
                    }
                } catch (Throwable throwable) {
                    if (replayPayload != null && replayPayload.refCnt() > 0) {
                        replayPayload.release();
                    }
                    throw throwable;
                } finally {
                    if (payload.refCnt() > 0) {
                        payload.release();
                    }
                }
            }
        }
        if (identifier != null) {
            boolean handledByForge = NetworkHooks.onCustomPayload(packet, connection);
            if (handledByForge) {
                LOGGER.debug("[NEB] Dispatched client custom payload through Forge path: {}", identifier);
                ci.cancel();
            }
        }
    }
}
