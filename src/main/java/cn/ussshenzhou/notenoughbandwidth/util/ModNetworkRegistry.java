package cn.ussshenzhou.notenoughbandwidth.util;

public final class ModNetworkRegistry {
    private ModNetworkRegistry() {
    }

    public static void init() {
        cn.ussshenzhou.network.ModNetworkRegistry.init();
    }
}
