package cn.ussshenzhou.notenoughbandwidth.network.payload;

import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * neoforge搬过来的类,好用!
 */
public final class ChannelAttributes {
    public static final AttributeKey<NetworkPayloadSetup> PAYLOAD_SETUP = AttributeKey.valueOf("nebl:payload_setup");
    public static final AttributeKey<Set<ResourceLocation>> ADHOC_CHANNELS = AttributeKey.valueOf("nebl:adhoc_channels");
    public static final AttributeKey<Map<ConnectionProtocol, Set<ResourceLocation>>> COMMON_CHANNELS = AttributeKey.valueOf("nebl:common_channels");

    private ChannelAttributes() {
    }

    public static void setPayloadSetup(Connection connection, NetworkPayloadSetup setup) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(PAYLOAD_SETUP).set(setup);
        }
    }

    @Nullable
    public static NetworkPayloadSetup getPayloadSetup(Connection connection) {
        if (connection == null || connection.channel() == null) {
            return null;
        }
        return connection.channel().attr(PAYLOAD_SETUP).get();
    }

    public static Set<ResourceLocation> getOrCreateAdHocChannels(Connection connection) {
        if (connection == null || connection.channel() == null) {
            return new HashSet<>();
        }
        Set<ResourceLocation> channels = connection.channel().attr(ADHOC_CHANNELS).get();
        if (channels == null) {
            channels = new HashSet<>();
            connection.channel().attr(ADHOC_CHANNELS).set(channels);
        }
        return channels;
    }

    public static Set<ResourceLocation> getOrCreateCommonChannels(Connection connection, ConnectionProtocol protocol) {
        if (connection == null || connection.channel() == null) {
            return new HashSet<>();
        }
        Map<ConnectionProtocol, Set<ResourceLocation>> channels = connection.channel().attr(COMMON_CHANNELS).get();
        if (channels == null) {
            channels = new EnumMap<>(ConnectionProtocol.class);
            connection.channel().attr(COMMON_CHANNELS).set(channels);
        }
        return channels.computeIfAbsent(protocol, p -> new HashSet<>());
    }
}
