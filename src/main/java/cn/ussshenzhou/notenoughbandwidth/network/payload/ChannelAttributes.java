package cn.ussshenzhou.notenoughbandwidth.network.payload;

import io.netty.util.AttributeKey;
import net.minecraft.network.NetworkManager;

import javax.annotation.Nullable;

public final class ChannelAttributes {
    public static final AttributeKey<NetworkPayloadSetup> PAYLOAD_SETUP = AttributeKey.valueOf("nebl:payload_setup");
    public static final AttributeKey<Boolean> TRANSPORT_SETUP_REQUESTED = AttributeKey.valueOf("nebl:transport_setup_requested");

    private ChannelAttributes() {
    }

    public static void setPayloadSetup(NetworkManager connection, @Nullable NetworkPayloadSetup setup) {
        if (connection != null && connection.channel() != null) {
            connection.channel().attr(PAYLOAD_SETUP).set(setup);
            if (setup != null) {
                connection.channel().attr(TRANSPORT_SETUP_REQUESTED).set(Boolean.FALSE);
            }
        }
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
}
