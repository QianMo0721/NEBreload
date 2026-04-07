package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleAggregatedPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            ServerboundCustomPayloadPacket copiedPacket;
            FriendlyByteBuf payload = packet.getData();
            FriendlyByteBuf wrapper = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
            try {
                wrapper.writeResourceLocation(packet.getIdentifier());
                wrapper.writeBytes(payload);
                copiedPacket = new ServerboundCustomPayloadPacket(wrapper);
            } finally {
                payload.release();
                wrapper.release();
            }
            server.execute(() -> ((ServerGamePacketListenerImpl) (Object) this).handleCustomPayload(copiedPacket));
            ci.cancel();
            return;
        }

        if (!PacketAggregationPacket.TYPE.equals(packet.getIdentifier())) {
            return;
        }

        PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(packet.getData());
        ArrayList<Packet<?>> packets = aggregationPacket.decodeToPackets(PacketFlow.SERVERBOUND);
        ServerGamePacketListener listener = (ServerGamePacketListener) (Object) this;
        for (Packet<?> subPacket : packets) {
            ((Packet<ServerGamePacketListener>) subPacket).handle(listener);
        }
        ci.cancel();
    }
}
