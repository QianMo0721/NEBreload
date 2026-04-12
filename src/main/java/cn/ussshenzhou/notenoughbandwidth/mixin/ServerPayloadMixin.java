package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = NetHandlerPlayServer.class)
public abstract class ServerPayloadMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "processCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebProcessCustomPayload(CPacketCustomPayload packet, CallbackInfo ci) {
        if (!NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.initFromRegisteredChannels();
        }
        final NetHandlerPlayServer listener = (NetHandlerPlayServer) (Object) this;
        if (listener.player != null && listener.player.getServerWorld() != null && !listener.player.getServerWorld().isCallingFromMinecraftThread()) {
            final CPacketCustomPayload copiedPacket = nebCopy(packet);
            listener.player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    listener.processCustomPayload(copiedPacket);
                }
            });
            ci.cancel();
            return;
        }

        if (PacketAggregationPacket.TYPE.toString().equals(LegacyCustomPayloadAccessor.getChannelName(packet))) {
            PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(LegacyCustomPayloadAccessor.getBufferData(packet));
            ArrayList<Packet<?>> packets = aggregationPacket.decodeToPackets(EnumPacketDirection.SERVERBOUND);
            for (Packet<?> subPacket : packets) {
                ((Packet) subPacket).processPacket(listener);
            }
            ci.cancel();
            return;
        }

        CPacketCustomPayload decompressed = CustomPayloadCodecHelper.tryDecompress(packet);
        if (decompressed != null) {
            ((NetHandlerPlayServer) (Object) this).processCustomPayload(decompressed);
            ci.cancel();
        }
    }

    private static CPacketCustomPayload nebCopy(CPacketCustomPayload packet) {
        PacketBuffer original = LegacyCustomPayloadAccessor.getBufferData(packet);
        PacketBuffer copied = new PacketBuffer(Unpooled.buffer(original.readableBytes()));
        copied.writeBytes(original, original.readerIndex(), original.readableBytes());
        return LegacyCustomPayloadAccessor.createCPacket(LegacyCustomPayloadAccessor.getChannelName(packet), copied);
    }
}
