package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.network.payload.HandlerThread;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;

public final class ModNetworkRegistry {
    private static boolean initialized;

    private ModNetworkRegistry() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        networkPacketRegistry(new PayloadRegistrar("1").optional().executesOn(HandlerThread.NETWORK));
        initialized = true;
    }

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        cn.ussshenzhou.notenoughbandwidth.network.payload.ModNetworkRegistry.networkPacketRegistry(registrar);
        cn.ussshenzhou.network.ModNetworkRegistry.networkPacketRegistry(registrar);
    }
}
