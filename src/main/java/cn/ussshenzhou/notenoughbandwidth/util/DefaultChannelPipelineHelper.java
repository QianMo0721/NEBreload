package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;

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
    private static final Field CONNECTION_CHANNEL;

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
            // Connection.channel field - try official name first, fallback to SRG
            Field channelField = null;
            try {
                channelField = Connection.class.getDeclaredField("channel");
            } catch (NoSuchFieldException e) {
                channelField = Connection.class.getDeclaredField("f_129508_");
            }
            CONNECTION_CHANNEL = channelField;
            CONNECTION_CHANNEL.setAccessible(true);
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
        try {
            var channel = CONNECTION_CHANNEL.get(connection);
            if (channel instanceof io.netty.channel.Channel ch && ch.pipeline() instanceof DefaultChannelPipeline pipeline) {
                return pipeline;
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Sends an aggregated packet directly via the Forge SimpleChannel.
     */
    public static void sendAggregated(Connection connection, PacketAggregationPacket packet) {
        try {
            ModNetworkRegistry.CHANNEL.sendTo(
                    packet,
                    connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
        } catch (Exception e1) {
            try {
                ModNetworkRegistry.CHANNEL.sendTo(
                        packet,
                        connection,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER
                );
            } catch (Exception e2) {
                com.mojang.logging.LogUtils.getLogger().error("[NEB] Failed to send aggregated packet via channel", e2);
            }
        }
    }
}
