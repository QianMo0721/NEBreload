package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultChannelPipelineHelper {

    private static final Field HEAD;
    private static final Field TAIL;
    private static final Field NEXT;

    static {
        try {
            HEAD = DefaultChannelPipeline.class.getDeclaredField("head");
            HEAD.setAccessible(true);
            TAIL = DefaultChannelPipeline.class.getDeclaredField("tail");
            TAIL.setAccessible(true);
            // AbstractChannelHandlerContext is the supertype of HeadContext
            Class<?> headCtxClass = DefaultChannelPipeline.class.getDeclaredField("head").getType();
            // Walk up the hierarchy to find 'next' field
            Field next = null;
            Class<?> c = headCtxClass;
            while (c != null) {
                try {
                    next = c.getDeclaredField("next");
                    break;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            if (next == null) throw new NoSuchFieldException("next field not found in pipeline context hierarchy");
            NEXT = next;
            NEXT.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public static PacketEncoder getPacketEncoder(DefaultChannelPipeline pipeline) {
        try {
            Object head = HEAD.get(pipeline);
            Object tail = TAIL.get(pipeline);
            var ctx = (ChannelHandlerContext) NEXT.get(head);
            if (ctx == null) {
                return null;
            }
            do {
                if (ctx.handler() instanceof PacketEncoder encoder) {
                    return encoder;
                }
                ctx = (ChannelHandlerContext) NEXT.get(ctx);
            } while (ctx != tail);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Nullable
    public static PacketDecoder getPacketDecoder(DefaultChannelPipeline pipeline) {
        try {
            Object head = HEAD.get(pipeline);
            Object tail = TAIL.get(pipeline);
            var ctx = (ChannelHandlerContext) NEXT.get(head);
            if (ctx == null) {
                return null;
            }
            do {
                if (ctx.handler() instanceof PacketDecoder decoder) {
                    return decoder;
                }
                ctx = (ChannelHandlerContext) NEXT.get(ctx);
            } while (ctx != tail);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Nullable
    public static DefaultChannelPipeline getPipeline(Connection connection) {
        var channel = connection.channel();
        if (channel != null && channel.pipeline() instanceof DefaultChannelPipeline pipeline) {
            return pipeline;
        }
        return null;
    }

    public static net.minecraft.network.protocol.Packet<?> toVanillaAggregatedPacket(Connection connection, PacketAggregationPacket packet) {
        FriendlyByteBuf wrapper = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
        try {
            wrapper.writeResourceLocation(PacketAggregationPacket.TYPE);
            packet.encode(wrapper);
            if (connection.getSending() == PacketFlow.CLIENTBOUND) {
                return new ClientboundCustomPayloadPacket(wrapper);
            }
            return new ServerboundCustomPayloadPacket(wrapper);
        } finally {
            wrapper.release();
        }
    }
}
