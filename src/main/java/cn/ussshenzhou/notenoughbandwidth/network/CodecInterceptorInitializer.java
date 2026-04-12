package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacy;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.network.ConnectionInterceptor;
import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;

public final class CodecInterceptorInitializer {
    private CodecInterceptorInitializer() {
    }

    public static void install(NetworkManager connection) {
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }
        if (channel.pipeline().get("nebl_connection_interceptor") == null) {
            channel.pipeline().addBefore("packet_handler", "nebl_connection_interceptor", new ConnectionInterceptor(connection));
        }
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
        if (NotEnoughBandwidthLegacyConfig.get().debugLog) {
            NotEnoughBandwidthLegacy.LOGGER.info("[NEB] Installed network interceptors on {}", connection.getDirection());
        }
    }
}
