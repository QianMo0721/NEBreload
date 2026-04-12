package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = NetHandlerPlayClient.class)
public abstract class ClientPayloadMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void nebHandleCustomPayload(SPacketCustomPayload packet, CallbackInfo ci) {
        if (!NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.initFromRegisteredChannels();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && !minecraft.isCallingFromMinecraftThread()) {
            final SPacketCustomPayload copiedPacket = nebCopy(packet);
            minecraft.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ((NetHandlerPlayClient) (Object) ClientPayloadMixin.this).handleCustomPayload(copiedPacket);
                }
            });
            ci.cancel();
            return;
        }

        if (PacketAggregationPacket.TYPE.toString().equals(LegacyCustomPayloadAccessor.getChannelName(packet))) {
            PacketAggregationPacket aggregationPacket = new PacketAggregationPacket(LegacyCustomPayloadAccessor.getBufferData(packet));
            ArrayList<Packet<?>> packets = aggregationPacket.decodeToPackets(EnumPacketDirection.CLIENTBOUND);
            NetHandlerPlayClient listener = (NetHandlerPlayClient) (Object) this;
            for (Packet<?> subPacket : packets) {
                ((Packet) subPacket).processPacket(listener);
            }
            ci.cancel();
            return;
        }

        SPacketCustomPayload decompressed = CustomPayloadCodecHelper.tryDecompress(packet);
        if (decompressed != null) {
            ((NetHandlerPlayClient) (Object) this).handleCustomPayload(decompressed);
            ci.cancel();
        }
    }

    private static SPacketCustomPayload nebCopy(SPacketCustomPayload packet) {
        PacketBuffer original = LegacyCustomPayloadAccessor.getBufferData(packet);
        PacketBuffer copied = new PacketBuffer(Unpooled.buffer(original.readableBytes()));
        copied.writeBytes(original, original.readerIndex(), original.readableBytes());
        return LegacyCustomPayloadAccessor.createSPacket(LegacyCustomPayloadAccessor.getChannelName(packet), copied);
    }
}
