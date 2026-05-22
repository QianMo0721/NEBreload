package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NettyPacketDecoder;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(NettyPacketDecoder.class)
public class PacketDecoderMixin {
    @Unique
    private static final String FRAMEWORK_CHANNEL = ModConstants.PAYLOAD_CHANNEL;

    @Inject(method = "decode", at = @At("TAIL"))
    private void nebRecordInboundTraffic(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (!out.isEmpty()) {
            Object last = out.get(out.size() - 1);
            if (last instanceof Packet) {
                Packet<?> packet = (Packet<?>) last;
                int bakedSize = input.readerIndex();
                SimpleStatManager.inBaked(bakedSize);
                Object truePacket = PacketUtil.getTruePacket(packet);
                if (handleFrameworkPayload(ctx, packet)) {
                    out.remove(out.size() - 1);
                    return;
                }
                if (PacketAggregationPacket.isTransport(packet)) {
                    PacketBuffer payload = PacketAggregationPacket.copyPayload(packet);
                    if (payload != null) {
                        try {
                            PacketAggregationPacket.decode(resolveConnection(ctx), payload);
                        } finally {
                            payload.release();
                        }
                    }
                } else {
                    int rawSize = truePacket instanceof PacketBuffer ? 0 : EncodedTrafficStatHelper.estimateRawPacketSize(packet, input);
                    SimpleStatManager.inRaw(rawSize);
                }
            }
        }
    }

    @Unique
    private boolean handleFrameworkPayload(ChannelHandlerContext ctx, Packet<?> packet) {
        NetworkManager connection = resolveConnection(ctx);
        INetHandler listener = connection == null ? null : connection.getNetHandler();
        if (packet instanceof SPacketCustomPayload) {
            PacketBuffer data = PacketAggregationPacket.getPayloadData(packet);
            String channel = PacketAggregationPacket.resolvePacketType(packet);
            if (data == null || !FRAMEWORK_CHANNEL.equals(channel)) {
                return false;
            }
            return PayloadRegistry.handleIncomingCustomPayload(connection, listener, channel, data, true);
        }
        if (packet instanceof CPacketCustomPayload) {
            PacketBuffer data = PacketAggregationPacket.getPayloadData(packet);
            String channel = PacketAggregationPacket.resolvePacketType(packet);
            if (data == null || !FRAMEWORK_CHANNEL.equals(channel)) {
                return false;
            }
            return PayloadRegistry.handleIncomingCustomPayload(connection, listener, channel, data, false);
        }
        return false;
    }

    @Unique
    private NetworkManager resolveConnection(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return null;
        }
        Object handler = ctx.pipeline().get("packet_handler");
        if (handler instanceof NetworkManager) {
            return (NetworkManager) handler;
        }
        try {
            for (String fieldName : new String[]{"this$0", "field_150746_k", "val$networkmanager", "networkManager"}) {
                Field field = handler.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(handler);
                if (value instanceof NetworkManager) {
                    return (NetworkManager) value;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
