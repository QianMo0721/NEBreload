package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public Connection connection;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleAggregatedPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (PacketAggregationPacket.TYPE.equals(packet.getIdentifier())) {
            PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(packet.getData());
            aggregationPacket.replay(connection, PacketFlow.SERVERBOUND);
            ci.cancel();
            return;
        }
        if (server != null && !server.isSameThread()) {
            ServerboundCustomPayloadPacket copiedPacket;
            FriendlyByteBuf payload = packet.getData();
            FriendlyByteBuf wrapper = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
            try {
                wrapper.writeResourceLocation(packet.getIdentifier());
                wrapper.writeBytes(payload);
                copiedPacket = new ServerboundCustomPayloadPacket(wrapper);
            } finally {
                wrapper.release();
            }
            server.execute(() -> ((ServerGamePacketListenerImpl) (Object) this).handleCustomPayload(copiedPacket));
            ci.cancel();
            return;
        }

        if (!PacketAggregationPacket.TYPE.equals(packet.getIdentifier())) {
            ServerboundCustomPayloadPacket decompressed = CustomPayloadCodecHelper.tryDecompress(packet);
            if (decompressed != null) {
                ((ServerGamePacketListenerImpl) (Object) this).handleCustomPayload(decompressed);
                ci.cancel();
            }
            return;
        }
    }
}
