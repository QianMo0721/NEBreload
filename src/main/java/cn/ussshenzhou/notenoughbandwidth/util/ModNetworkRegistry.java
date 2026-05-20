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
        PayloadRegistrar registrar = new PayloadRegistrar("1").executesOn(HandlerThread.MAIN);
        cn.ussshenzhou.notenoughbandwidth.network.payload.ModNetworkRegistry.networkPacketRegistry(registrar);
        cn.ussshenzhou.network.ModNetworkRegistry.init();
        initialized = true;
    }
}
