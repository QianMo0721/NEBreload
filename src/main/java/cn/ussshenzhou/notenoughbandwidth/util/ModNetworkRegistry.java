package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.HandlerThread;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;

/**
 * Aggregation transport registry only.
 */
public class ModNetworkRegistry {

    public static void networkPacketRegistry(PayloadRegistrar registrar) {
        var networkThreadRegistrar = registrar.executesOn(HandlerThread.NETWORK);
        networkThreadRegistrar.playBidirectional(
                PacketAggregationPacket.SAMPLE,
                PacketAggregationPacket.CODEC,
                PacketAggregationPacket::handle
        );
    }

}
