package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.Connection;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/*
 * neoforge搬过来的类,好用!
 */
public final class ChannelAttributes {
    private static final Map<Connection, NetworkPayloadSetup> PAYLOAD_SETUPS = new WeakHashMap<>();

    private ChannelAttributes() {
    }

    public static void setPayloadSetup(Connection connection, NetworkPayloadSetup setup) {
        synchronized (PAYLOAD_SETUPS) {
            PAYLOAD_SETUPS.put(connection, setup);
        }
    }

    @Nullable
    public static NetworkPayloadSetup getPayloadSetup(Connection connection) {
        synchronized (PAYLOAD_SETUPS) {
            return PAYLOAD_SETUPS.get(connection);
        }
    }
}
