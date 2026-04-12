package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.util.CustomPayloadCodecHelper;
import cn.ussshenzhou.notenoughbandwidth.util.LegacyCustomPayloadAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.local.LocalAddress;
import io.netty.util.AttributeKey;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import java.net.SocketAddress;
import java.util.ArrayList;

public class ConnectionInterceptor extends ChannelDuplexHandler {
    private static final AttributeKey<Integer> DEPTH = AttributeKey.valueOf("nebl_payload_depth");
    private final NetworkManager connection;

    public ConnectionInterceptor(NetworkManager connection) {
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
    }

    private static Packet<?> tryDecompressCustomPayload(Packet<?> packet) {
        if (packet instanceof CPacketCustomPayload) {
            return CustomPayloadCodecHelper.tryDecompress((CPacketCustomPayload) packet);
        }
        if (packet instanceof SPacketCustomPayload) {
            return CustomPayloadCodecHelper.tryDecompress((SPacketCustomPayload) packet);
        }
        return null;
    }

    private ArrayList<Packet<?>> tryUnpackAggregated(Packet<?> packet) {
        if (!NamespaceIndexManager.isInitialized()) {
            NamespaceIndexManager.initFromRegisteredChannels();
        }
        if (packet instanceof CPacketCustomPayload) {
            CPacketCustomPayload customPayload = (CPacketCustomPayload) packet;
            if (PacketAggregationPacket.TYPE.toString().equals(LegacyCustomPayloadAccessor.getChannelName(customPayload))) {
                return new PacketAggregationPacket(LegacyCustomPayloadAccessor.getBufferData(customPayload)).decodeToPackets(connection.getDirection());
            }
        }
        if (packet instanceof SPacketCustomPayload) {
            SPacketCustomPayload customPayload = (SPacketCustomPayload) packet;
            if (PacketAggregationPacket.TYPE.toString().equals(LegacyCustomPayloadAccessor.getChannelName(customPayload))) {
                return new PacketAggregationPacket(LegacyCustomPayloadAccessor.getBufferData(customPayload)).decodeToPackets(connection.getDirection());
            }
        }
        return null;
    }

    private static int getDepth(Channel channel) {
        Integer depth = channel.attr(DEPTH).get();
        return depth == null ? 0 : depth.intValue();
    }

    private static void setDepth(Channel channel, int depth) {
        if (depth <= 0) {
            channel.attr(DEPTH).set(null);
        } else {
            channel.attr(DEPTH).set(Integer.valueOf(depth));
        }
    }
}
