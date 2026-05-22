package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.ConnectionIndexTable;
import io.netty.util.AttributeKey;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ChannelAttributes {
    public static final AttributeKey<NetworkPayloadSetup> PAYLOAD_SETUP = AttributeKey.valueOf("nebl:payload_setup");
    public static final AttributeKey<ConnectionIndexTable> CONNECTION_INDEX_TABLE = AttributeKey.valueOf("nebl:connection_index_table");
    public static final AttributeKey<Set<String>> ADHOC_CHANNELS = AttributeKey.valueOf("nebl:adhoc_channels");
    public static final AttributeKey<Map<EnumConnectionState, Set<String>>> COMMON_CHANNELS = AttributeKey.valueOf("nebl:common_channels");
    public static final AttributeKey<Boolean> TRANSPORT_SETUP_REQUESTED = AttributeKey.valueOf("nebl:transport_setup_requested");

    private ChannelAttributes() {
    }

    public static void setPayloadSetup(NetworkManager connection, @Nullable NetworkPayloadSetup setup) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(PAYLOAD_SETUP).set(setup);
            if (setup != null && setup.hasChannel(PacketAggregationPacket.CHANNEL_NAME)) {
                connection.channel().attr(TRANSPORT_SETUP_REQUESTED).set(Boolean.FALSE);
            }
        }
    }

    public static void setConnectionIndexTable(NetworkManager connection, @Nullable ConnectionIndexTable table) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(CONNECTION_INDEX_TABLE).set(table);
        }
    }

    @Nullable
    public static ConnectionIndexTable getConnectionIndexTable(NetworkManager connection) {
        if (connection == null || connection.channel() == null) {
            return null;
        }
        return connection.channel().attr(CONNECTION_INDEX_TABLE).get();
    }

    @Nullable
    public static NetworkPayloadSetup getPayloadSetup(NetworkManager connection) {
        if (connection == null || connection.channel() == null) {
            return null;
        }
        return connection.channel().attr(PAYLOAD_SETUP).get();
    }

    public static boolean hasPayload(NetworkManager connection, String id) {
        NetworkPayloadSetup setup = getPayloadSetup(connection);
        return setup != null && setup.hasChannel(id);
    }

    public static boolean hasNebTransport(NetworkManager connection) {
        return hasPayload(connection, PacketAggregationPacket.CHANNEL_NAME);
    }

    public static boolean isTransportSetupRequested(NetworkManager connection) {
        if (connection == null || connection.channel() == null) {
            return false;
        }
        return Boolean.TRUE.equals(connection.channel().attr(TRANSPORT_SETUP_REQUESTED).get());
    }

    public static void markTransportSetupRequested(NetworkManager connection) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(TRANSPORT_SETUP_REQUESTED).set(Boolean.TRUE);
        }
    }

    public static void clearTransportSetupRequested(NetworkManager connection) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(TRANSPORT_SETUP_REQUESTED).set(Boolean.FALSE);
        }
    }

    public static Set<String> getOrCreateAdHocChannels(NetworkManager connection) {
        if (connection == null || connection.channel() == null) {
            return new HashSet<String>();
        }
        Set<String> channels = connection.channel().attr(ADHOC_CHANNELS).get();
        if (channels == null) {
            channels = new HashSet<String>();
            connection.channel().attr(ADHOC_CHANNELS).set(channels);
        }
        return channels;
    }

    public static Set<String> getOrCreateCommonChannels(NetworkManager connection, EnumConnectionState protocol) {
        if (connection == null || connection.channel() == null) {
            return new HashSet<String>();
        }
        Map<EnumConnectionState, Set<String>> channels = connection.channel().attr(COMMON_CHANNELS).get();
        if (channels == null) {
            channels = new EnumMap<EnumConnectionState, Set<String>>(EnumConnectionState.class);
            connection.channel().attr(COMMON_CHANNELS).set(channels);
        }
        Set<String> result = channels.get(protocol);
        if (result == null) {
            result = new HashSet<String>();
            channels.put(protocol, result);
        }
        return result;
    }
}
